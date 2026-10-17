import { cleanPhone, formatPhoneBr, OFFICIAL_PLANS, isUserEligibleForPromo, ValidPlanId } from "./licenseStore.js";

const ASAAS_BASE_URL = "https://api.asaas.com/v3";

function getApiKey(): string {
  return process.env.ASAAS_API_KEY || "";
}

export async function findOrCreateAsaasCustomer(phone: string): Promise<string> {
  const digits = cleanPhone(phone);
  const apiKey = getApiKey();

  // 1. Search existing customer by mobilePhone
  try {
    const searchUrl = `${ASAAS_BASE_URL}/customers?mobilePhone=${encodeURIComponent(digits)}`;
    const res = await fetch(searchUrl, {
      headers: { access_token: apiKey },
    });
    if (res.ok) {
      const data: any = await res.json();
      if (Array.isArray(data.data) && data.data.length > 0) {
        return data.data[0].id;
      }
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar cliente por telefone:", err);
  }

  // 2. Create customer
  const formatted = formatPhoneBr(digits);
  const postRes = await fetch(`${ASAAS_BASE_URL}/customers`, {
    method: "POST",
    headers: {
      access_token: apiKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      name: `Motorista GoOrNo ${digits.slice(-4)}`,
      mobilePhone: digits,
      externalReference: `goorno_tel_${digits}`,
      notificationDisabled: true,
    }),
  });

  if (!postRes.ok) {
    const errorBody = await postRes.text();
    console.error(`[asaas] Erro ao criar cliente: ${postRes.status} - ${errorBody}`);
    throw new Error(`Falha ao registrar cliente no Asaas: ${errorBody}`);
  }

  const created: any = await postRes.json();
  return created.id;
}

export interface CreatePixResult {
  paymentId: string;
  value: number;
  dueDate: string;
  encodedImage: string; // Base64 PNG QR Code
  payload: string; // Copia e Cola PIX String
  expirationDate?: string;
}

export async function createPixCharge(params: {
  whatsapp: string;
  plan: ValidPlanId;
}): Promise<CreatePixResult & { plan: ValidPlanId; promoApplied: boolean; renewalPrice: number; periodDays: number }> {
  const digits = cleanPhone(params.whatsapp);
  const apiKey = getApiKey();
  const customerId = await findOrCreateAsaasCustomer(digits);

  const planInfo = OFFICIAL_PLANS.find((p) => p.id === params.plan) || OFFICIAL_PLANS[0];
  const eligible = await isUserEligibleForPromo(digits);

  const value = eligible ? planInfo.promoPrice : planInfo.normalPrice;
  const promoApplied = eligible;
  const description = `GoOrNo Copiloto - ${planInfo.name} (${planInfo.periodDays} dias)${promoApplied ? ' - Promoção de Lançamento' : ''}`;

  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dueDate = tomorrow.toISOString().slice(0, 10);

  // 1. Create Payment
  const payRes = await fetch(`${ASAAS_BASE_URL}/payments`, {
    method: "POST",
    headers: {
      access_token: apiKey,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      customer: customerId,
      billingType: "PIX",
      value: value,
      dueDate: dueDate,
      description: description,
      externalReference: `goorno_tel_${digits}`,
    }),
  });

  if (!payRes.ok) {
    const errText = await payRes.text();
    throw new Error(`Erro ao gerar cobrança no Asaas: ${errText}`);
  }

  const payment: any = await payRes.json();
  const paymentId = payment.id;

  // 2. Fetch PIX QR Code & Copia e Cola
  const qrRes = await fetch(`${ASAAS_BASE_URL}/payments/${paymentId}/pixQrCode`, {
    headers: { access_token: apiKey },
  });

  if (!qrRes.ok) {
    const errText = await qrRes.text();
    throw new Error(`Erro ao buscar QR Code PIX no Asaas: ${errText}`);
  }

  const qrData: any = await qrRes.json();

  return {
    paymentId: paymentId,
    value: value,
    dueDate: dueDate,
    encodedImage: qrData.encodedImage,
    payload: qrData.payload,
    expirationDate: qrData.expirationDate,
    plan: planInfo.id,
    promoApplied: promoApplied,
    renewalPrice: planInfo.renewalPrice,
    periodDays: planInfo.periodDays,
  };
}

export async function verifyPaymentStatus(paymentId: string): Promise<{
  confirmed: boolean;
  status: string;
  value: number;
  externalReference?: string;
}> {
  const apiKey = getApiKey();
  const res = await fetch(`${ASAAS_BASE_URL}/payments/${paymentId}`, {
    headers: { access_token: apiKey },
  });

  if (!res.ok) {
    return { confirmed: false, status: "ERROR", value: 0 };
  }

  const data: any = await res.json();
  const confirmed = ["RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH"].includes(data.status);

  return {
    confirmed,
    status: data.status,
    value: Number(data.value || 0),
    externalReference: data.externalReference,
  };
}

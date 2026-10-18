import { cleanPhone, formatPhoneBr, OFFICIAL_PLANS, isUserEligibleForPromo, ValidPlanId } from "./licenseStore.js";

const ASAAS_BASE_URL = "https://api.asaas.com/v3";

function getApiKey(): string {
  return process.env.ASAAS_API_KEY || "";
}

// In-memory registry to match pixQrCodeId with driver phone for instant webhook reconciliation
const pendingPixMap = new Map<string, string>();

export function recordPendingPix(paymentId: string, phone: string) {
  if (paymentId && phone) {
    pendingPixMap.set(paymentId, cleanPhone(phone));
  }
}

export function getPendingPixPhone(paymentId: string): string | undefined {
  return pendingPixMap.get(paymentId);
}

export async function getPixAddressKey(): Promise<string> {
  const apiKey = getApiKey();
  try {
    const res = await fetch(`${ASAAS_BASE_URL}/pix/addressKeys`, {
      headers: {
        access_token: apiKey,
        "User-Agent": "GoOrNo-Backend/1.0",
      },
    });
    if (res.ok) {
      const data: any = await res.json();
      if (Array.isArray(data.data) && data.data.length > 0) {
        const active = data.data.find((k: any) => k.status === "ACTIVE");
        if (active && active.key) return active.key;
      }
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar chave PIX no Asaas, usando fallback:", err);
  }
  return "fbf59149-4c5c-4711-afcb-a8a98fceedab";
}

export async function findOrCreateAsaasCustomer(phone: string): Promise<string> {
  const digits = cleanPhone(phone);
  const apiKey = getApiKey();

  // 1. Search existing customer by mobilePhone
  try {
    const searchUrl = `${ASAAS_BASE_URL}/customers?mobilePhone=${encodeURIComponent(digits)}`;
    const res = await fetch(searchUrl, {
      headers: {
        access_token: apiKey,
        "User-Agent": "GoOrNo-Backend/1.0",
      },
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
  const postRes = await fetch(`${ASAAS_BASE_URL}/customers`, {
    method: "POST",
    headers: {
      access_token: apiKey,
      "User-Agent": "GoOrNo-Backend/1.0",
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

  const planInfo = OFFICIAL_PLANS.find((p) => p.id === params.plan) || OFFICIAL_PLANS[0];
  const eligible = await isUserEligibleForPromo(digits);

  const value = eligible ? planInfo.promoPrice : planInfo.normalPrice;
  const promoApplied = eligible;
  const description = `GoOrNo Copiloto - ${planInfo.name} (${digits.slice(-4)})`;

  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dueDate = tomorrow.toISOString().slice(0, 10);

  const addressKey = await getPixAddressKey();

  // Create Static PIX QR Code with Asaas (does not require customer CPF/CNPJ, frictionless for driver)
  const qrRes = await fetch(`${ASAAS_BASE_URL}/pix/qrCodes/static`, {
    method: "POST",
    headers: {
      access_token: apiKey,
      "User-Agent": "GoOrNo-Backend/1.0",
      "Content-Type": "application/json",
    },
    body: JSON.stringify({
      addressKey: addressKey,
      description: description,
      value: value,
      format: "ALL",
      allowsMultiplePayments: false,
      externalReference: `goorno_tel_${digits}`,
    }),
  });

  if (!qrRes.ok) {
    const errText = await qrRes.text();
    console.error(`[asaas] Erro ao gerar QR Code PIX estático: ${qrRes.status} - ${errText}`);
    throw new Error(`Erro ao gerar cobrança no Asaas: ${errText}`);
  }

  const qrData: any = await qrRes.json();
  const paymentId = qrData.id;

  // Track pending payment in memory for webhook reconciliation
  recordPendingPix(paymentId, digits);

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
  const headers = {
    access_token: apiKey,
    "User-Agent": "GoOrNo-Backend/1.0",
  };

  // 1. Check if a payment was created for this static PIX QR code
  try {
    const qrPaymentsRes = await fetch(
      `${ASAAS_BASE_URL}/payments?pixQrCodeId=${encodeURIComponent(paymentId)}`,
      { headers }
    );
    if (qrPaymentsRes.ok) {
      const qrData: any = await qrPaymentsRes.json();
      if (Array.isArray(qrData.data) && qrData.data.length > 0) {
        const p = qrData.data[0];
        const confirmed = ["RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH"].includes(p.status);
        const mappedPhone = getPendingPixPhone(paymentId);
        return {
          confirmed,
          status: p.status,
          value: Number(p.value || 0),
          externalReference: p.externalReference || (mappedPhone ? `goorno_tel_${mappedPhone}` : undefined),
        };
      }
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar cobranças por pixQrCodeId:", err);
  }

  // 2. Direct payment check (if paymentId was created via standard /v3/payments)
  try {
    const res = await fetch(`${ASAAS_BASE_URL}/payments/${encodeURIComponent(paymentId)}`, {
      headers,
    });
    if (res.ok) {
      const data: any = await res.json();
      const confirmed = ["RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH"].includes(data.status);
      return {
        confirmed,
        status: data.status,
        value: Number(data.value || 0),
        externalReference: data.externalReference,
      };
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar pagamento direto:", err);
  }

  return { confirmed: false, status: "PENDING", value: 0 };
}

import fs from "fs";
import path from "path";

const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "expedicao-brasil";
const FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || "";
const COL_LICENSES = "goorno_licencas";
const COL_VOUCHERS = "goorno_vouchers";

const IS_VERCEL = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
const DATA_DIR = IS_VERCEL ? "/tmp" : path.resolve(process.cwd(), ".data");
const LOCAL_CACHE_FILE = path.join(DATA_DIR, "goorno-licenses-cache.json");
const LOCAL_VOUCHERS_FILE = path.join(DATA_DIR, "goorno-vouchers-cache.json");

export interface VoucherRecord {
  id: string; // uppercase code e.g. "BEMVINDO7"
  code: string;
  description: string;
  days: number; // e.g. 7, 15, 30, 999
  isLifetime: boolean;
  maxUses: number; // 0 = unlimited
  usedCount: number;
  active: boolean;
  createdAt: string;
}

export interface LicenseTransfer {
  date: string;
  fromDevice: string;
  toDevice: string;
  ip?: string;
}

export interface LicenseRecord {
  id: string; // Digits only e.g. "11999998888"
  whatsapp: string;
  deviceId: string;
  deviceModel?: string;
  status: "trial" | "active" | "expired" | "blocked";
  plano: "trial" | "mensal" | "vitalicio" | "cortesia";
  trialEndsAt: string;
  validoAte: string;
  createdAt: string;
  lastSeenAt: string;
  deviceTransfersCount: number;
  transfers: LicenseTransfer[];
  fraudSuspicion: boolean;
  fraudReason?: string;
  notes?: string;
}

function firestoreUrl(collection: string, docId?: string): string {
  const base = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents/${collection}`;
  const pathUrl = docId ? `${base}/${encodeURIComponent(docId)}` : base;
  return FIREBASE_API_KEY ? `${pathUrl}?key=${encodeURIComponent(FIREBASE_API_KEY)}` : pathUrl;
}

export function cleanPhone(phone: string): string {
  if (!phone) return "";
  let digits = phone.replace(/\D/g, "");
  // Remove Brazilian country code 55 if present with 12 or 13 digits
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) {
    digits = digits.substring(2);
  }
  return digits;
}

export function formatPhoneBr(digits: string): string {
  const clean = cleanPhone(digits);
  if (clean.length === 11) {
    return `(${clean.slice(0, 2)}) ${clean.slice(2, 7)}-${clean.slice(7)}`;
  }
  if (clean.length === 10) {
    return `(${clean.slice(0, 2)}) ${clean.slice(2, 6)}-${clean.slice(6)}`;
  }
  return clean;
}

// Local file memory cache fallback
function readLocalCache(): Record<string, LicenseRecord> {
  try {
    if (fs.existsSync(LOCAL_CACHE_FILE)) {
      return JSON.parse(fs.readFileSync(LOCAL_CACHE_FILE, "utf-8"));
    }
  } catch {}
  return {};
}

function saveLocalCache(cache: Record<string, LicenseRecord>) {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.writeFileSync(LOCAL_CACHE_FILE, JSON.stringify(cache, null, 2), "utf-8");
  } catch {}
}

export async function getLicense(phone: string): Promise<LicenseRecord | null> {
  const id = cleanPhone(phone);
  if (!id) return null;

  try {
    const res = await fetch(firestoreUrl(COL_LICENSES, `tel_${id}`));
    if (res.ok) {
      const json: any = await res.json();
      const raw = json?.fields?.payload?.stringValue;
      if (raw) {
        const doc: LicenseRecord = JSON.parse(raw);
        const cache = readLocalCache();
        cache[id] = doc;
        saveLocalCache(cache);
        return doc;
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao buscar no Firestore, tentando cache local:", err);
  }

  const cache = readLocalCache();
  return cache[id] || null;
}

export async function saveLicense(license: LicenseRecord): Promise<boolean> {
  const id = cleanPhone(license.id || license.whatsapp);
  if (!id) return false;
  license.id = id;
  license.lastSeenAt = new Date().toISOString();

  // Save to cache
  const cache = readLocalCache();
  cache[id] = license;
  saveLocalCache(cache);

  // Save to Firestore
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(license) },
        updatedAt: { stringValue: new Date().toISOString() },
      },
    };
    const res = await fetch(firestoreUrl(COL_LICENSES, `tel_${id}`), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return res.ok;
  } catch (err) {
    console.warn("[licenseStore] Erro ao persistir no Firestore:", err);
    return false;
  }
}

export async function listAllLicenses(): Promise<LicenseRecord[]> {
  const list: LicenseRecord[] = [];
  const mapById = new Map<string, LicenseRecord>();

  try {
    const res = await fetch(`${firestoreUrl(COL_LICENSES)}?pageSize=300`);
    if (res.ok) {
      const json: any = await res.json();
      const docs = json.documents || [];
      for (const d of docs) {
        const raw = d.fields?.payload?.stringValue;
        if (raw) {
          try {
            const parsed: LicenseRecord = JSON.parse(raw);
            mapById.set(parsed.id, parsed);
          } catch {}
        }
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao listar do Firestore:", err);
  }

  // Merge with local cache
  const cache = readLocalCache();
  for (const [id, item] of Object.entries(cache)) {
    if (!mapById.has(id)) {
      mapById.set(id, item);
    }
  }

  return Array.from(mapById.values()).sort((a, b) => b.lastSeenAt.localeCompare(a.lastSeenAt));
}

export interface CheckLicenseResult {
  allowed: boolean;
  status: "trial" | "active" | "expired" | "blocked";
  plano: "trial" | "mensal" | "vitalicio" | "cortesia";
  trialDaysRemaining: number;
  validoAte: string;
  message: string;
  deviceMismatch?: boolean;
  fraudAlert?: boolean;
  license?: LicenseRecord;
}

export async function checkOrActivateLicense(params: {
  whatsapp: string;
  deviceId: string;
  deviceModel?: string;
  action?: "check" | "transfer";
  clientIp?: string;
}): Promise<CheckLicenseResult> {
  const phone = cleanPhone(params.whatsapp);
  if (phone.length < 10) {
    return {
      allowed: false,
      status: "expired",
      plano: "trial",
      trialDaysRemaining: 0,
      validoAte: "",
      message: "Número de WhatsApp inválido (informe DDD + 9 dígitos).",
    };
  }

  let record = await getLicense(phone);
  const now = new Date();

  // If new user, create 7-day Trial
  if (!record) {
    const trialEnds = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1000);
    record = {
      id: phone,
      whatsapp: formatPhoneBr(phone),
      deviceId: params.deviceId,
      deviceModel: params.deviceModel || "Android Device",
      status: "trial",
      plano: "trial",
      trialEndsAt: trialEnds.toISOString(),
      validoAte: trialEnds.toISOString(),
      createdAt: now.toISOString(),
      lastSeenAt: now.toISOString(),
      deviceTransfersCount: 0,
      transfers: [],
      fraudSuspicion: false,
    };
    await saveLicense(record);
    return {
      allowed: true,
      status: "trial",
      plano: "trial",
      trialDaysRemaining: 7,
      validoAte: trialEnds.toISOString(),
      message: "Bem-vindo ao GoOrNo! Seu período de teste de 7 dias grátis foi ativado.",
      license: record,
    };
  }

  // Update last seen & device model
  record.lastSeenAt = now.toISOString();
  if (params.deviceModel) record.deviceModel = params.deviceModel;

  // Check if blocked
  if (record.status === "blocked") {
    return {
      allowed: false,
      status: "blocked",
      plano: record.plano,
      trialDaysRemaining: 0,
      validoAte: record.validoAte,
      message: "Esta licença foi bloqueada pela administração do sistema.",
      fraudAlert: record.fraudSuspicion,
      license: record,
    };
  }

  // Device Lock Verification
  const isSameDevice = !record.deviceId || record.deviceId === params.deviceId;
  if (!isSameDevice) {
    if (params.action === "transfer") {
      // Calculate transfers in the last 30 days
      const thirtyDaysAgo = now.getTime() - 30 * 24 * 60 * 60 * 1000;
      const recentTransfers = (record.transfers || []).filter(
        (t) => new Date(t.date).getTime() > thirtyDaysAgo
      );

      // Max 2 automatic transfers per month
      if (recentTransfers.length >= 2) {
        record.fraudSuspicion = true;
        record.fraudReason = `Tentativa excessiva de troca de aparelho (${recentTransfers.length + 1} trocas em menos de 30 dias). Possível compartilhamento de conta.`;
        await saveLicense(record);

        return {
          allowed: false,
          status: record.status,
          plano: record.plano,
          trialDaysRemaining: 0,
          validoAte: record.validoAte,
          message: "Limite de trocas de aparelho atingido (máx. 2 por mês). Suspeita de compartilhamento detectada. Contate o suporte.",
          deviceMismatch: true,
          fraudAlert: true,
          license: record,
        };
      }

      // Allow transfer
      const transferEntry: LicenseTransfer = {
        date: now.toISOString(),
        fromDevice: record.deviceId,
        toDevice: params.deviceId,
        ip: params.clientIp,
      };
      record.transfers = [...(record.transfers || []), transferEntry];
      record.deviceTransfersCount = record.transfers.length;
      record.deviceId = params.deviceId;
      await saveLicense(record);
    } else {
      // Prompt user to confirm transfer
      return {
        allowed: false,
        status: record.status,
        plano: record.plano,
        trialDaysRemaining: 0,
        validoAte: record.validoAte,
        message: "Este WhatsApp está vinculado a outro aparelho. Deseja transferir sua licença para este aparelho?",
        deviceMismatch: true,
        license: record,
      };
    }
  }

  // Check Expiration
  const trialEndMs = new Date(record.trialEndsAt || 0).getTime();
  const validUntilMs = new Date(record.validoAte || 0).getTime();
  const nowMs = now.getTime();

  if (record.plano === "trial") {
    if (nowMs > trialEndMs) {
      record.status = "expired";
      await saveLicense(record);
      return {
        allowed: false,
        status: "expired",
        plano: "trial",
        trialDaysRemaining: 0,
        validoAte: record.trialEndsAt,
        message: "Seu teste gratuito de 7 dias expirou. Ative o plano Pro para continuar rodando com o copiloto!",
        license: record,
      };
    }
    const daysLeft = Math.max(1, Math.ceil((trialEndMs - nowMs) / (1000 * 60 * 60 * 24)));
    await saveLicense(record);
    return {
      allowed: true,
      status: "trial",
      plano: "trial",
      trialDaysRemaining: daysLeft,
      validoAte: record.trialEndsAt,
      message: `Modo de Teste Ativo (${daysLeft} dias restantes).`,
      license: record,
    };
  }

  // Paid or Courtesy Plan
  if (record.plano === "vitalicio" || record.plano === "cortesia") {
    record.status = "active";
    await saveLicense(record);
    return {
      allowed: true,
      status: "active",
      plano: record.plano,
      trialDaysRemaining: 999,
      validoAte: record.validoAte,
      message: "Licença Vitalícia / Cortesia Ativa!",
      license: record,
    };
  }

  // Monthly Pro
  if (nowMs > validUntilMs) {
    record.status = "expired";
    await saveLicense(record);
    return {
      allowed: false,
      status: "expired",
      plano: "mensal",
      trialDaysRemaining: 0,
      validoAte: record.validoAte,
      message: "Sua assinatura mensal expirou. Renove via PIX por apenas R$ 9,90 para liberar o copiloto.",
      license: record,
    };
  }

  const daysLeft = Math.max(1, Math.ceil((validUntilMs - nowMs) / (1000 * 60 * 60 * 24)));
  record.status = "active";
  await saveLicense(record);
  return {
    allowed: true,
    status: "active",
    plano: "mensal",
    trialDaysRemaining: daysLeft,
    validoAte: record.validoAte,
    message: `Assinatura Pro Mensal Ativa (${daysLeft} dias restantes).`,
    license: record,
  };
}

export async function applyPayment(params: {
  phone: string;
  amount: number;
  paymentId: string;
}): Promise<LicenseRecord> {
  const phone = cleanPhone(params.phone);
  let record = await getLicense(phone);
  const now = new Date();

  const isLifetime = params.amount >= 35; // e.g. R$ 39,90
  const isMonthly = !isLifetime; // e.g. R$ 9,90

  let currentValidMs = record && record.validoAte ? new Date(record.validoAte).getTime() : 0;
  if (currentValidMs < now.getTime()) {
    currentValidMs = now.getTime();
  }

  const newValidDate = isLifetime
    ? new Date("2099-12-31T23:59:59.000Z")
    : new Date(currentValidMs + 30 * 24 * 60 * 60 * 1000);

  if (!record) {
    record = {
      id: phone,
      whatsapp: formatPhoneBr(phone),
      deviceId: "",
      status: "active",
      plano: isLifetime ? "vitalicio" : "mensal",
      trialEndsAt: now.toISOString(),
      validoAte: newValidDate.toISOString(),
      createdAt: now.toISOString(),
      lastSeenAt: now.toISOString(),
      deviceTransfersCount: 0,
      transfers: [],
      fraudSuspicion: false,
      notes: `Pagamento aprovado Asaas ${params.paymentId} (R$ ${params.amount.toFixed(2)})`,
    };
  } else {
    record.status = "active";
    record.plano = isLifetime ? "vitalicio" : "mensal";
    record.validoAte = newValidDate.toISOString();
    record.lastSeenAt = now.toISOString();
    record.notes = `${record.notes || ""}\nPagamento Asaas ${params.paymentId} (R$ ${params.amount.toFixed(2)}) em ${now.toISOString()}`.trim();
  }

  await saveLicense(record);
  return record;
}

// Local vouchers cache fallback
function readLocalVouchersCache(): Record<string, VoucherRecord> {
  try {
    if (fs.existsSync(LOCAL_VOUCHERS_FILE)) {
      return JSON.parse(fs.readFileSync(LOCAL_VOUCHERS_FILE, "utf-8"));
    }
  } catch {}
  return {};
}

function saveLocalVouchersCache(cache: Record<string, VoucherRecord>) {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.writeFileSync(LOCAL_VOUCHERS_FILE, JSON.stringify(cache, null, 2), "utf-8");
  } catch {}
}

export async function getVoucher(code: string): Promise<VoucherRecord | null> {
  const cleanCode = code.trim().toUpperCase();
  if (!cleanCode) return null;

  try {
    const res = await fetch(firestoreUrl(COL_VOUCHERS, `vouch_${cleanCode}`));
    if (res.ok) {
      const json: any = await res.json();
      const raw = json?.fields?.payload?.stringValue;
      if (raw) {
        const doc: VoucherRecord = JSON.parse(raw);
        const cache = readLocalVouchersCache();
        cache[cleanCode] = doc;
        saveLocalVouchersCache(cache);
        return doc;
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao ler voucher do Firestore:", err);
  }

  const cache = readLocalVouchersCache();
  return cache[cleanCode] || null;
}

export async function saveVoucher(voucher: VoucherRecord): Promise<boolean> {
  const code = voucher.code.trim().toUpperCase();
  if (!code) return false;
  voucher.id = code;
  voucher.code = code;

  // Save to cache
  const cache = readLocalVouchersCache();
  cache[code] = voucher;
  saveLocalVouchersCache(cache);

  // Save to Firestore
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(voucher) },
        updatedAt: { stringValue: new Date().toISOString() },
      },
    };
    const res = await fetch(firestoreUrl(COL_VOUCHERS, `vouch_${code}`), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return res.ok;
  } catch (err) {
    console.warn("[licenseStore] Erro ao salvar voucher no Firestore:", err);
    return false;
  }
}

export async function deleteVoucher(code: string): Promise<boolean> {
  const cleanCode = code.trim().toUpperCase();
  if (!cleanCode) return false;

  const cache = readLocalVouchersCache();
  delete cache[cleanCode];
  saveLocalVouchersCache(cache);

  try {
    const res = await fetch(firestoreUrl(COL_VOUCHERS, `vouch_${cleanCode}`), {
      method: "DELETE",
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function listAllVouchers(): Promise<VoucherRecord[]> {
  const mapById = new Map<string, VoucherRecord>();

  try {
    const res = await fetch(`${firestoreUrl(COL_VOUCHERS)}?pageSize=300`);
    if (res.ok) {
      const json: any = await res.json();
      const docs = json.documents || [];
      for (const d of docs) {
        const raw = d.fields?.payload?.stringValue;
        if (raw) {
          try {
            const parsed: VoucherRecord = JSON.parse(raw);
            mapById.set(parsed.code, parsed);
          } catch {}
        }
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao listar vouchers do Firestore:", err);
  }

  // Merge with local cache
  const cache = readLocalVouchersCache();
  for (const [code, item] of Object.entries(cache)) {
    if (!mapById.has(code)) {
      mapById.set(code, item);
    }
  }

  return Array.from(mapById.values()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}

export async function redeemVoucher(params: {
  phone: string;
  code: string;
  deviceId?: string;
}): Promise<{ ok: boolean; message: string; license?: LicenseRecord; voucher?: VoucherRecord }> {
  const cleanCode = params.code.trim().toUpperCase();
  if (!cleanCode) {
    return { ok: false, message: "Digite um código de cupom válido." };
  }

  const voucher = await getVoucher(cleanCode);
  if (!voucher) {
    return { ok: false, message: `Cupom "${cleanCode}" não encontrado.` };
  }

  if (!voucher.active) {
    return { ok: false, message: `O cupom "${cleanCode}" está desativado.` };
  }

  if (voucher.maxUses > 0 && voucher.usedCount >= voucher.maxUses) {
    return { ok: false, message: `Limite de utilizações do cupom "${cleanCode}" foi atingido.` };
  }

  const phone = cleanPhone(params.phone);
  if (!phone) {
    return { ok: false, message: "Informe seu número de WhatsApp para resgatar." };
  }

  let record = await getLicense(phone);
  const now = new Date();

  // Check if driver already redeemed this specific coupon
  if (record && record.notes && record.notes.includes(`Cupom ${cleanCode} resgatado`)) {
    return { ok: false, message: `Você já resgatou o cupom "${cleanCode}" anteriormente neste número.` };
  }

  let currentValidMs = record && record.validoAte ? new Date(record.validoAte).getTime() : 0;
  if (currentValidMs < now.getTime()) {
    currentValidMs = now.getTime();
  }

  const isLifetime = voucher.isLifetime || voucher.days >= 365;
  const newValidDate = isLifetime
    ? new Date("2099-12-31T23:59:59.000Z")
    : new Date(currentValidMs + voucher.days * 24 * 60 * 60 * 1000);

  if (!record) {
    record = {
      id: phone,
      whatsapp: formatPhoneBr(phone),
      deviceId: params.deviceId || "",
      status: "active",
      plano: isLifetime ? "vitalicio" : "cortesia",
      trialEndsAt: now.toISOString(),
      validoAte: newValidDate.toISOString(),
      createdAt: now.toISOString(),
      lastSeenAt: now.toISOString(),
      deviceTransfersCount: 0,
      transfers: [],
      fraudSuspicion: false,
      notes: `Cupom ${cleanCode} resgatado (+${voucher.days} dias) em ${now.toLocaleString("pt-BR")}`,
    };
  } else {
    record.status = "active";
    record.plano = isLifetime ? "vitalicio" : (record.plano === "vitalicio" ? "vitalicio" : "cortesia");
    record.validoAte = newValidDate.toISOString();
    record.lastSeenAt = now.toISOString();
    if (params.deviceId && !record.deviceId) {
      record.deviceId = params.deviceId;
    }
    record.notes = `${record.notes || ""}\nCupom ${cleanCode} resgatado (+${voucher.days} dias) em ${now.toLocaleString("pt-BR")}`.trim();
  }

  // Increment voucher usage
  voucher.usedCount++;
  await saveVoucher(voucher);
  await saveLicense(record);

  const msg = isLifetime
    ? `🎉 Parabéns! Cupom "${cleanCode}" ativado: Acesso Vitalício liberado com sucesso!`
    : `🎉 Sucesso! Cupom "${cleanCode}" ativado: +${voucher.days} dias liberados (válido até ${new Date(record.validoAte).toLocaleDateString("pt-BR")}).`;

  return { ok: true, message: msg, license: record, voucher };
}

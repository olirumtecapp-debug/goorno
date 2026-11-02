// server/index.ts
import express from "express";
import path4 from "path";
import fs4 from "fs";

// server/store.ts
import fs from "fs";
import path from "path";
var IS_VERCEL = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
var DATA_DIR = IS_VERCEL ? "/tmp" : path.resolve(process.cwd(), ".data");
var STORE_FILE = path.join(DATA_DIR, "goorno-store.json");
var FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "expedicao-brasil";
var FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || "";
var COL_MAIN = "goorno_cloud_store";
var COL_VAULT = "goorno_vault_snapshots";
var DEFAULT_VEHICLE_CONFIG = {
  vehicleType: "car_flex",
  vehicleModel: "Onix / HB20 1.0 Flex",
  fuelPricePerUnit: 5.89,
  consumptionPerUnit: 11.5,
  maintenanceCostPerKm: 0.24,
  rentalOrInstallmentDaily: 0,
  greenMinPerKm: 2,
  yellowMinPerKm: 1.55,
  greenMinPerHour: 42,
  yellowMinPerHour: 30,
  dailyNetGoal: 280,
  voiceAlertEnabled: true,
  overlayEnabled: true,
  autoOpenStreetViewOnAccept: false
};
var SEED_RIDES = [
  {
    id: "ride-seed-1",
    driverEmail: "motorista@goorno.app",
    platform: "Uber",
    category: "Uber Comfort",
    offeredValue: 34.8,
    receivedValue: 34.8,
    pickupDistanceKm: 1.2,
    tripDistanceKm: 11.3,
    totalDistanceKm: 12.5,
    pickupTimeMin: 4,
    tripTimeMin: 21,
    totalTimeMin: 25,
    valuePerKm: 2.78,
    valuePerHour: 83.52,
    estimatedFuelCost: 6.4,
    estimatedTotalCost: 9.4,
    netProfit: 25.4,
    verdict: "GO",
    originAddress: "Av. Paulista, 1578 - Bela Vista, S\xE3o Paulo - SP",
    originNeighborhood: "Bela Vista",
    destinationAddress: "Aeroporto de Congonhas (CGH) - Vila Congonhas, S\xE3o Paulo - SP",
    destinationNeighborhood: "Vila Congonhas",
    passengerName: "Rodrigo M.",
    passengerRating: 4.94,
    decision: "accepted",
    isRiskArea: false,
    createdAt: new Date(Date.now() - 1e3 * 60 * 18).toISOString()
  },
  {
    id: "ride-seed-2",
    driverEmail: "motorista@goorno.app",
    platform: "99",
    category: "99Pop",
    offeredValue: 26.5,
    receivedValue: 21.9,
    pickupDistanceKm: 1.8,
    tripDistanceKm: 10.2,
    totalDistanceKm: 12,
    pickupTimeMin: 5,
    tripTimeMin: 24,
    totalTimeMin: 29,
    valuePerKm: 2.21,
    valuePerHour: 54.83,
    estimatedFuelCost: 6.15,
    estimatedTotalCost: 9.03,
    netProfit: 17.47,
    verdict: "GO",
    originAddress: "Rua Augusta, 2210 - Cerqueira C\xE9sar, S\xE3o Paulo - SP",
    originNeighborhood: "Cerqueira C\xE9sar",
    destinationAddress: "Shopping Eldorado - Av. Rebou\xE7as, 3970 - Pinheiros, S\xE3o Paulo - SP",
    destinationNeighborhood: "Pinheiros",
    passengerName: "Camila S.",
    passengerRating: 4.88,
    decision: "accepted",
    paymentAuditNote: "Plataforma pagou R$ 4,60 a menos que a oferta da tela",
    isRiskArea: false,
    createdAt: new Date(Date.now() - 1e3 * 60 * 55).toISOString()
  },
  {
    id: "ride-seed-3",
    driverEmail: "motorista@goorno.app",
    platform: "Uber",
    category: "UberX",
    offeredValue: 11.4,
    receivedValue: null,
    pickupDistanceKm: 3.1,
    tripDistanceKm: 6.8,
    totalDistanceKm: 9.9,
    pickupTimeMin: 9,
    tripTimeMin: 19,
    totalTimeMin: 28,
    valuePerKm: 1.15,
    valuePerHour: 24.43,
    estimatedFuelCost: 5.07,
    estimatedTotalCost: 7.45,
    netProfit: 3.95,
    verdict: "NO",
    originAddress: "Rua da Consola\xE7\xE3o, 890 - Consola\xE7\xE3o, S\xE3o Paulo - SP",
    originNeighborhood: "Consola\xE7\xE3o",
    destinationAddress: "Estrada do M'Boi Mirim, 4200 - Jardim \xC2ngela, S\xE3o Paulo - SP",
    destinationNeighborhood: "Jardim \xC2ngela",
    passengerName: "Cliente Novo",
    passengerRating: 4.62,
    decision: "declined",
    isRiskArea: true,
    createdAt: new Date(Date.now() - 1e3 * 60 * 95).toISOString()
  },
  {
    id: "ride-seed-4",
    driverEmail: "motorista@goorno.app",
    platform: "inDrive",
    category: "Conforto",
    offeredValue: 22,
    receivedValue: 22,
    pickupDistanceKm: 1.4,
    tripDistanceKm: 10.6,
    totalDistanceKm: 12,
    pickupTimeMin: 4,
    tripTimeMin: 28,
    totalTimeMin: 32,
    valuePerKm: 1.83,
    valuePerHour: 41.25,
    estimatedFuelCost: 6.15,
    estimatedTotalCost: 9.03,
    netProfit: 12.97,
    verdict: "WARN",
    originAddress: "Av. Brigadeiro Faria Lima, 3477 - Itaim Bibi, S\xE3o Paulo - SP",
    originNeighborhood: "Itaim Bibi",
    destinationAddress: "Rua Vergueiro, 1855 - Vila Mariana, S\xE3o Paulo - SP",
    destinationNeighborhood: "Vila Mariana",
    passengerName: "Marcos T.",
    passengerRating: 4.91,
    decision: "accepted",
    isRiskArea: false,
    createdAt: new Date(Date.now() - 1e3 * 60 * 140).toISOString()
  }
];
var SEED_RISK_ZONES = [
  {
    id: "risk-1",
    neighborhoodOrStreet: "Jardim \xC2ngela / Estrada do M'Boi Mirim",
    city: "S\xE3o Paulo - SP",
    riskLevel: "high",
    reason: "Alto \xEDndice de assaltos noturnos e vielas sem sa\xEDda",
    addedBy: "Comunidade GoOrNo",
    createdAt: (/* @__PURE__ */ new Date()).toISOString()
  },
  {
    id: "risk-2",
    neighborhoodOrStreet: "Cap\xE3o Redondo (\xC1reas Internas)",
    city: "S\xE3o Paulo - SP",
    riskLevel: "high",
    reason: "Conferir Street View antes de entrar \xE0 noite",
    addedBy: "Comunidade GoOrNo",
    createdAt: (/* @__PURE__ */ new Date()).toISOString()
  },
  {
    id: "risk-3",
    neighborhoodOrStreet: "Parais\xF3polis / Rua Ernest Renan",
    city: "S\xE3o Paulo - SP",
    riskLevel: "medium",
    reason: "Ruas estreitas com bloqueio frequente de via",
    addedBy: "Comunidade GoOrNo",
    createdAt: (/* @__PURE__ */ new Date()).toISOString()
  }
];
function createDefaultDb() {
  return {
    users: [
      {
        id: "usr-demo-1",
        name: "Murilo (Motorista Pro)",
        email: "motorista@goorno.app",
        phone: "(11) 99999-0000",
        city: "S\xE3o Paulo - SP",
        plan: "vip",
        status: "active",
        trialEndsAt: new Date(Date.now() + 1e3 * 60 * 60 * 24 * 365).toISOString(),
        createdAt: (/* @__PURE__ */ new Date()).toISOString(),
        lastActiveAt: (/* @__PURE__ */ new Date()).toISOString(),
        vehicleConfig: DEFAULT_VEHICLE_CONFIG
      }
    ],
    rides: SEED_RIDES,
    riskZones: SEED_RISK_ZONES,
    lastUpdated: (/* @__PURE__ */ new Date()).toISOString()
  };
}
var memoryDb = null;
var hydratedFromCloud = false;
function firestoreUrl(collection, docId) {
  const base = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents/${collection}`;
  const pathUrl = docId ? `${base}/${encodeURIComponent(docId)}` : base;
  return FIREBASE_API_KEY ? `${pathUrl}?key=${encodeURIComponent(FIREBASE_API_KEY)}` : pathUrl;
}
async function fetchCloudDoc(collection, docId) {
  try {
    const res = await fetch(firestoreUrl(collection, docId));
    if (!res.ok) return null;
    const json = await res.json();
    const raw = json?.fields?.payload?.stringValue;
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}
async function saveCloudDoc(collection, docId, data) {
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(data) },
        updatedAt: { stringValue: (/* @__PURE__ */ new Date()).toISOString() }
      }
    };
    const res = await fetch(firestoreUrl(collection, docId), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    return res.ok;
  } catch {
    return false;
  }
}
async function listVaultSnapshots() {
  const results = [];
  let pageToken = "";
  try {
    do {
      const base = firestoreUrl(COL_VAULT);
      const sep = base.includes("?") ? "&" : "?";
      const url = `${base}${sep}pageSize=50${pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : ""}`;
      const res = await fetch(url);
      if (!res.ok) break;
      const json = await res.json();
      const docs = json.documents || [];
      for (const doc of docs) {
        const slot = String(doc.name || "").split("/").pop() || "unknown";
        const updatedAt = doc.fields?.updatedAt?.stringValue || "";
        const raw = doc.fields?.payload?.stringValue;
        let usersCount = 0;
        let ridesCount = 0;
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            usersCount = Array.isArray(parsed.users) ? parsed.users.length : 0;
            ridesCount = Array.isArray(parsed.rides) ? parsed.rides.length : 0;
          } catch {
          }
        }
        results.push({ slot, updatedAt, usersCount, ridesCount });
      }
      pageToken = json.nextPageToken || "";
    } while (pageToken);
  } catch {
  }
  return results.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}
async function loadDatabase() {
  if (memoryDb && hydratedFromCloud) {
    return memoryDb;
  }
  const cloudData = await fetchCloudDoc(COL_MAIN, "main_state");
  if (cloudData && Array.isArray(cloudData.users) && Array.isArray(cloudData.rides)) {
    memoryDb = cloudData;
    hydratedFromCloud = true;
    saveLocalFile(memoryDb);
    return memoryDb;
  }
  try {
    if (fs.existsSync(STORE_FILE)) {
      const raw = fs.readFileSync(STORE_FILE, "utf-8");
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.users)) {
        memoryDb = parsed;
        hydratedFromCloud = true;
        await saveCloudDoc(COL_MAIN, "main_state", memoryDb);
        return memoryDb;
      }
    }
  } catch {
  }
  memoryDb = createDefaultDb();
  hydratedFromCloud = true;
  saveLocalFile(memoryDb);
  await saveCloudDoc(COL_MAIN, "main_state", memoryDb);
  return memoryDb;
}
function saveLocalFile(db) {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.writeFileSync(STORE_FILE, JSON.stringify(db, null, 2), "utf-8");
  } catch {
  }
}
async function saveDatabase(db) {
  db.lastUpdated = (/* @__PURE__ */ new Date()).toISOString();
  memoryDb = db;
  saveLocalFile(db);
  await saveCloudDoc(COL_MAIN, "main_state", db);
  const dayOfWeek = (/* @__PURE__ */ new Date()).getDay();
  const todayKey = `${(/* @__PURE__ */ new Date()).toISOString().slice(0, 10)}_slot_${dayOfWeek}`;
  if (db.lastSnapshotSlot !== todayKey) {
    db.lastSnapshotSlot = todayKey;
    await saveCloudDoc(COL_VAULT, `slot_day_${dayOfWeek}`, db);
  }
}
async function restoreFromVaultSlot(slotId) {
  const snapshot = await fetchCloudDoc(COL_VAULT, slotId);
  if (!snapshot || !Array.isArray(snapshot.users)) return null;
  await saveDatabase(snapshot);
  return snapshot;
}
function calculateRideMetrics(params, config, riskZones = []) {
  const totalDistanceKm = Math.max(0.1, Number((params.pickupDistanceKm + params.tripDistanceKm).toFixed(2)));
  const totalTimeMin = Math.max(1, Math.round(params.pickupTimeMin + params.tripTimeMin));
  const valuePerKm = Number((params.offeredValue / totalDistanceKm).toFixed(2));
  const valuePerHour = Number((params.offeredValue / totalTimeMin * 60).toFixed(2));
  const fuelCostPerKm = config.consumptionPerUnit > 0 ? config.fuelPricePerUnit / config.consumptionPerUnit : 0.5;
  const estimatedFuelCost = Number((totalDistanceKm * fuelCostPerKm).toFixed(2));
  const estimatedTotalCost = Number((totalDistanceKm * (fuelCostPerKm + config.maintenanceCostPerKm)).toFixed(2));
  const netProfit = Number((params.offeredValue - estimatedTotalCost).toFixed(2));
  let verdict = "WARN";
  if (valuePerKm >= config.greenMinPerKm && valuePerHour >= config.greenMinPerHour) {
    verdict = "GO";
  } else if (valuePerKm < config.yellowMinPerKm || valuePerHour < config.yellowMinPerHour || netProfit <= 0) {
    verdict = "NO";
  }
  const combinedText = `${params.originAddress} ${params.destinationAddress}`.toLowerCase();
  const matchedRisk = riskZones.find(
    (rz) => rz.neighborhoodOrStreet.toLowerCase().split("/").some((part) => part.trim().length > 3 && combinedText.includes(part.trim()))
  );
  return {
    totalDistanceKm,
    totalTimeMin,
    valuePerKm,
    valuePerHour,
    estimatedFuelCost,
    estimatedTotalCost,
    netProfit,
    verdict,
    isRiskArea: Boolean(matchedRisk),
    riskReason: matchedRisk ? `${matchedRisk.neighborhoodOrStreet}: ${matchedRisk.reason}` : null
  };
}

// server/licenseStore.ts
import fs2 from "fs";
import path2 from "path";
var PROMO_CONFIG = {
  campaignActive: process.env.PROMO_ACTIVE !== "false",
  campaignName: "Promo\xE7\xE3o de Lan\xE7amento GoOrNo",
  startDate: process.env.PROMO_START_DATE || "2026-10-17T00:00:00.000Z",
  endDate: process.env.PROMO_END_DATE || "2026-11-17T23:59:59.000Z",
  // 30 dias de campanha
  durationDays: 30
};
var OFFICIAL_PLANS = [
  {
    id: "mensal",
    name: "Plano Mensal",
    periodDays: 30,
    periodMonths: 1,
    normalPrice: 7.99,
    promoPrice: 5.09,
    renewalPrice: 7.99,
    monthlyEquivalentNormal: 7.99,
    monthlyEquivalentPromo: 5.09,
    savingsPromoVsMonthlyNormal: 2.9,
    savingsNormalVsMonthlyNormal: 0,
    description: "Acesso completo por 30 dias. Renova\xE7\xE3o por R$ 7,99/m\xEAs."
  },
  {
    id: "trimestral",
    name: "Plano Trimestral",
    periodDays: 90,
    periodMonths: 3,
    normalPrice: 21.9,
    promoPrice: 18.9,
    renewalPrice: 21.9,
    monthlyEquivalentNormal: 7.3,
    monthlyEquivalentPromo: 6.3,
    savingsPromoVsMonthlyNormal: 5.07,
    savingsNormalVsMonthlyNormal: 2.07,
    description: "Acesso por 3 meses (90 dias). Renova\xE7\xE3o por R$ 21,90 a cada 3 meses."
  },
  {
    id: "semestral",
    name: "Plano Semestral",
    periodDays: 180,
    periodMonths: 6,
    normalPrice: 41.9,
    promoPrice: 34.9,
    renewalPrice: 41.9,
    monthlyEquivalentNormal: 6.98,
    monthlyEquivalentPromo: 5.81,
    savingsPromoVsMonthlyNormal: 13.04,
    savingsNormalVsMonthlyNormal: 6.04,
    description: "Acesso por 6 meses (180 dias). Renova\xE7\xE3o por R$ 41,90 a cada 6 meses."
  },
  {
    id: "anual",
    name: "Plano Anual",
    periodDays: 365,
    periodMonths: 12,
    normalPrice: 79.9,
    promoPrice: 59.9,
    renewalPrice: 79.9,
    monthlyEquivalentNormal: 6.66,
    monthlyEquivalentPromo: 4.99,
    savingsPromoVsMonthlyNormal: 35.98,
    savingsNormalVsMonthlyNormal: 15.98,
    highlightBadge: "MAIS ECON\xD4MICO",
    description: "Acesso por 1 ano (365 dias). Menos de R$ 5,00/m\xEAs. Renova\xE7\xE3o por R$ 79,90/ano."
  }
];
var FIREBASE_PROJECT_ID2 = process.env.FIREBASE_PROJECT_ID || "expedicao-brasil";
var FIREBASE_API_KEY2 = process.env.FIREBASE_API_KEY || "";
var COL_LICENSES = "goorno_vault_snapshots";
var COL_VOUCHERS = "goorno_vault_snapshots";
var IS_VERCEL2 = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
var DATA_DIR2 = IS_VERCEL2 ? "/tmp" : path2.resolve(process.cwd(), ".data");
var LOCAL_CACHE_FILE = path2.join(DATA_DIR2, "goorno-licenses-cache.json");
var LOCAL_VOUCHERS_FILE = path2.join(DATA_DIR2, "goorno-vouchers-cache.json");
function firestoreUrl2(collection, docId) {
  const base = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID2}/databases/(default)/documents/${collection}`;
  const pathUrl = docId ? `${base}/${encodeURIComponent(docId)}` : base;
  return FIREBASE_API_KEY2 ? `${pathUrl}?key=${encodeURIComponent(FIREBASE_API_KEY2)}` : pathUrl;
}
var VALID_BRAZILIAN_DDDS = /* @__PURE__ */ new Set([
  11,
  12,
  13,
  14,
  15,
  16,
  17,
  18,
  19,
  21,
  22,
  24,
  27,
  28,
  31,
  32,
  33,
  34,
  35,
  37,
  38,
  41,
  42,
  43,
  44,
  45,
  46,
  47,
  48,
  49,
  51,
  53,
  54,
  55,
  61,
  62,
  63,
  64,
  65,
  66,
  67,
  68,
  69,
  71,
  73,
  74,
  75,
  77,
  79,
  81,
  82,
  83,
  84,
  87,
  85,
  86,
  88,
  89,
  91,
  92,
  93,
  94,
  95,
  96,
  97,
  98,
  99
]);
function validateBrazilianPhone(phone) {
  const digits = cleanPhone(phone);
  if (digits.length !== 11) {
    return { valid: false, error: "O WhatsApp deve conter DDD + 9 d\xEDgitos (ex: 11985052148)." };
  }
  const ddd = parseInt(digits.slice(0, 2), 10);
  if (!VALID_BRAZILIAN_DDDS.has(ddd)) {
    return { valid: false, error: `DDD (${ddd}) inv\xE1lido para o Brasil.` };
  }
  if (digits[2] !== "9") {
    return { valid: false, error: "N\xFAmero de celular inv\xE1lido (o 9\xBA d\xEDgito deve come\xE7ar com 9)." };
  }
  if (!["6", "7", "8", "9"].includes(digits[3])) {
    return { valid: false, error: "Prefixo m\xF3vel inv\xE1lido para celulares no Brasil." };
  }
  const uniqueAll = new Set(digits.split("")).size;
  if (uniqueAll <= 2) {
    return { valid: false, error: "N\xFAmero de telefone gen\xE9rico ou repetido inv\xE1lido." };
  }
  const uniqueBody = new Set(digits.slice(3).split("")).size;
  if (uniqueBody === 1) {
    return { valid: false, error: "N\xFAmero de celular gen\xE9rico com d\xEDgitos repetidos." };
  }
  const body = digits.slice(2);
  if (["912345678", "987654321", "900000000", "911111111"].includes(body)) {
    return { valid: false, error: "N\xFAmero em sequ\xEAncia ou gen\xE9rico n\xE3o permitido." };
  }
  return { valid: true };
}
function cleanPhone(phone) {
  if (!phone) return "";
  let digits = phone.replace(/\D/g, "");
  if (digits.startsWith("55") && (digits.length === 12 || digits.length === 13)) {
    digits = digits.substring(2);
  }
  return digits;
}
function formatPhoneBr(digits) {
  const clean = cleanPhone(digits);
  if (clean.length === 11) {
    return `(${clean.slice(0, 2)}) ${clean.slice(2, 7)}-${clean.slice(7)}`;
  }
  if (clean.length === 10) {
    return `(${clean.slice(0, 2)}) ${clean.slice(2, 6)}-${clean.slice(6)}`;
  }
  return clean;
}
function readLocalCache() {
  try {
    if (fs2.existsSync(LOCAL_CACHE_FILE)) {
      return JSON.parse(fs2.readFileSync(LOCAL_CACHE_FILE, "utf-8"));
    }
  } catch {
  }
  return {};
}
function saveLocalCache(cache) {
  try {
    if (!fs2.existsSync(DATA_DIR2)) {
      fs2.mkdirSync(DATA_DIR2, { recursive: true });
    }
    fs2.writeFileSync(LOCAL_CACHE_FILE, JSON.stringify(cache, null, 2), "utf-8");
  } catch {
  }
}
async function getLicense(phone) {
  const id = cleanPhone(phone);
  if (!id) return null;
  try {
    const res = await fetch(firestoreUrl2(COL_LICENSES, `lic_tel_${id}`));
    if (res.ok) {
      const json = await res.json();
      const raw = json?.fields?.payload?.stringValue;
      if (raw) {
        const doc = JSON.parse(raw);
        const cache2 = readLocalCache();
        cache2[id] = doc;
        saveLocalCache(cache2);
        return doc;
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao buscar no Firestore, tentando cache local:", err);
  }
  const cache = readLocalCache();
  return cache[id] || null;
}
async function saveLicense(license) {
  const id = cleanPhone(license.id || license.whatsapp);
  if (!id) return false;
  license.id = id;
  license.lastSeenAt = (/* @__PURE__ */ new Date()).toISOString();
  const cache = readLocalCache();
  cache[id] = license;
  saveLocalCache(cache);
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(license) },
        updatedAt: { stringValue: (/* @__PURE__ */ new Date()).toISOString() }
      }
    };
    const res = await fetch(firestoreUrl2(COL_LICENSES, `lic_tel_${id}`), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    return res.ok;
  } catch (err) {
    console.warn("[licenseStore] Erro ao persistir no Firestore:", err);
    return false;
  }
}
async function listAllLicenses() {
  const mapById = /* @__PURE__ */ new Map();
  try {
    const res = await fetch(`${firestoreUrl2(COL_LICENSES)}?pageSize=300`);
    if (res.ok) {
      const json = await res.json();
      const docs = json.documents || [];
      for (const d of docs) {
        if (!d.name) continue;
        const raw = d.fields?.payload?.stringValue;
        if (!raw) continue;
        if (d.name.includes("/lic_tel_")) {
          try {
            const parsed = JSON.parse(raw);
            mapById.set(parsed.id, parsed);
          } catch {
          }
        }
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao listar do Firestore:", err);
  }
  const cache = readLocalCache();
  for (const [id, item] of Object.entries(cache)) {
    if (!mapById.has(id)) {
      mapById.set(id, item);
    }
  }
  return Array.from(mapById.values()).sort((a, b) => b.lastSeenAt.localeCompare(a.lastSeenAt));
}
async function checkOrActivateLicense(params) {
  const phone = cleanPhone(params.whatsapp);
  const phoneVal = validateBrazilianPhone(phone);
  if (!phoneVal.valid) {
    return {
      allowed: false,
      status: "expired",
      plano: "trial",
      trialDaysRemaining: 0,
      validoAte: "",
      message: phoneVal.error || "N\xFAmero de WhatsApp inv\xE1lido (informe DDD + celular com 9 d\xEDgitos)."
    };
  }
  let record = await getLicense(phone);
  const now = /* @__PURE__ */ new Date();
  if (!record) {
    const trialEnds = new Date(now.getTime() + 7 * 24 * 60 * 60 * 1e3);
    record = {
      id: phone,
      whatsapp: formatPhoneBr(phone),
      deviceId: params.deviceId,
      deviceModel: params.deviceModel || "Android Device",
      status: "trial",
      plano: "trial",
      pin: params.pin ? params.pin.trim() : void 0,
      recoverySecret: params.recoverySecret ? params.recoverySecret.trim().toUpperCase() : void 0,
      trialEndsAt: trialEnds.toISOString(),
      validoAte: trialEnds.toISOString(),
      createdAt: now.toISOString(),
      lastSeenAt: now.toISOString(),
      deviceTransfersCount: 0,
      transfers: [],
      fraudSuspicion: false,
      termsAcceptedVersion: params.termsAcceptedVersion || "1.0",
      termsAcceptedAt: now.toISOString()
    };
    await saveLicense(record);
    return {
      allowed: true,
      status: "trial",
      plano: "trial",
      trialDaysRemaining: 7,
      validoAte: trialEnds.toISOString(),
      message: "Bem-vindo ao GoOrNo! Seu per\xEDodo de teste de 7 dias gr\xE1tis foi ativado.",
      hasPinSet: Boolean(record.pin),
      license: record
    };
  }
  record.lastSeenAt = now.toISOString();
  if (params.deviceModel) record.deviceModel = params.deviceModel;
  if (params.termsAcceptedVersion) {
    record.termsAcceptedVersion = params.termsAcceptedVersion;
    record.termsAcceptedAt = now.toISOString();
  }
  const isSameDevice = !record.deviceId || record.deviceId === params.deviceId;
  if (isSameDevice) {
    if (params.pin && (!record.pin || params.action === "transfer")) {
      record.pin = params.pin.trim();
    }
    if (params.recoverySecret && (!record.recoverySecret || params.action === "transfer")) {
      record.recoverySecret = params.recoverySecret.trim().toUpperCase();
    }
  }
  if (record.status === "blocked") {
    return {
      allowed: false,
      status: "blocked",
      plano: record.plano,
      trialDaysRemaining: 0,
      validoAte: record.validoAte,
      message: "Esta licen\xE7a foi bloqueada pela administra\xE7\xE3o do sistema.",
      fraudAlert: record.fraudSuspicion,
      hasPinSet: Boolean(record.pin),
      license: record
    };
  }
  if (!isSameDevice) {
    if (record.pin) {
      const incomingPin = (params.pin || "").trim();
      if (!incomingPin) {
        return {
          allowed: false,
          status: record.status,
          plano: record.plano,
          trialDaysRemaining: 0,
          validoAte: record.validoAte,
          message: "Este WhatsApp est\xE1 vinculado a outro aparelho. Digite seu PIN de 4 d\xEDgitos para transferir.",
          deviceMismatch: true,
          requiresPin: true,
          hasPinSet: true,
          license: record
        };
      }
      if (incomingPin !== record.pin) {
        return {
          allowed: false,
          status: record.status,
          plano: record.plano,
          trialDaysRemaining: 0,
          validoAte: record.validoAte,
          message: "PIN de 4 d\xEDgitos incorreto! Tente novamente ou use 'Esqueci meu PIN'.",
          deviceMismatch: true,
          requiresPin: true,
          pinInvalid: true,
          hasPinSet: true,
          license: record
        };
      }
    }
    if (params.action === "transfer" || record.pin && params.pin && params.pin.trim() === record.pin) {
      const thirtyDaysAgo = now.getTime() - 30 * 24 * 60 * 60 * 1e3;
      const recentTransfers = (record.transfers || []).filter(
        (t) => new Date(t.date).getTime() > thirtyDaysAgo
      );
      if (recentTransfers.length >= 2) {
        record.fraudSuspicion = true;
        record.fraudReason = `Tentativa excessiva de troca de aparelho (${recentTransfers.length + 1} trocas em menos de 30 dias). Poss\xEDvel compartilhamento de conta.`;
        await saveLicense(record);
        return {
          allowed: false,
          status: record.status,
          plano: record.plano,
          trialDaysRemaining: 0,
          validoAte: record.validoAte,
          message: "Limite de trocas de aparelho atingido (m\xE1x. 2 por m\xEAs). Suspeita de compartilhamento detectada. Contate o suporte.",
          deviceMismatch: true,
          fraudAlert: true,
          hasPinSet: Boolean(record.pin),
          license: record
        };
      }
      const transferEntry = {
        date: now.toISOString(),
        fromDevice: record.deviceId,
        toDevice: params.deviceId,
        ip: params.clientIp
      };
      record.transfers = [...record.transfers || [], transferEntry];
      record.deviceTransfersCount = record.transfers.length;
      record.deviceId = params.deviceId;
      if (params.pin && !record.pin) record.pin = params.pin.trim();
      if (params.recoverySecret && !record.recoverySecret) record.recoverySecret = params.recoverySecret.trim().toUpperCase();
      await saveLicense(record);
    } else {
      return {
        allowed: false,
        status: record.status,
        plano: record.plano,
        trialDaysRemaining: 0,
        validoAte: record.validoAte,
        message: "Este WhatsApp est\xE1 vinculado a outro aparelho. Deseja transferir sua licen\xE7a para este aparelho?",
        deviceMismatch: true,
        requiresPin: Boolean(record.pin),
        hasPinSet: Boolean(record.pin),
        license: record
      };
    }
  }
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
        license: record
      };
    }
    const daysLeft2 = Math.max(1, Math.ceil((trialEndMs - nowMs) / (1e3 * 60 * 60 * 24)));
    await saveLicense(record);
    return {
      allowed: true,
      status: "trial",
      plano: "trial",
      trialDaysRemaining: daysLeft2,
      validoAte: record.trialEndsAt,
      message: `Modo de Teste Ativo (${daysLeft2} dias restantes).`,
      license: record
    };
  }
  if (record.plano === "vitalicio" || record.plano === "cortesia") {
    record.status = "active";
    await saveLicense(record);
    return {
      allowed: true,
      status: "active",
      plano: record.plano,
      trialDaysRemaining: 999,
      validoAte: record.validoAte,
      message: "Licen\xE7a Vital\xEDcia / Cortesia Ativa!",
      license: record
    };
  }
  if (nowMs > validUntilMs) {
    record.status = "expired";
    await saveLicense(record);
    const renewalVal = (record.nextRenewalPrice || 7.99).toFixed(2).replace(".", ",");
    return {
      allowed: false,
      status: "expired",
      plano: record.plano,
      trialDaysRemaining: 0,
      validoAte: record.validoAte,
      message: `Sua assinatura ${record.plano} expirou. Renove via PIX por apenas R$ ${renewalVal} para liberar o copiloto.`,
      license: record
    };
  }
  const daysLeft = Math.max(1, Math.ceil((validUntilMs - nowMs) / (1e3 * 60 * 60 * 24)));
  record.status = "active";
  await saveLicense(record);
  const planFormatted = record.plano.charAt(0).toUpperCase() + record.plano.slice(1);
  return {
    allowed: true,
    status: "active",
    plano: record.plano,
    trialDaysRemaining: daysLeft,
    validoAte: record.validoAte,
    message: `Assinatura Pro ${planFormatted} Ativa (${daysLeft} dias restantes).`,
    license: record
  };
}
async function isUserEligibleForPromo(phone) {
  const digits = cleanPhone(phone);
  if (!digits) return false;
  const now = Date.now();
  const start = new Date(PROMO_CONFIG.startDate).getTime();
  const end = new Date(PROMO_CONFIG.endDate).getTime();
  if (now < start || now > end) {
    return false;
  }
  const record = await getLicense(digits);
  if (!record) return true;
  if (record.firstPurchasePromoApplied) return false;
  if (record.paymentsHistory && record.paymentsHistory.length > 0) return false;
  if (record.plano !== "trial") return false;
  return true;
}
async function applyPayment(params) {
  const phone = cleanPhone(params.phone);
  let record = await getLicense(phone);
  if (record && record.paymentsHistory?.some((p) => p.paymentId === params.paymentId)) {
    console.log(`[licenseStore] Pagamento ${params.paymentId} j\xE1 processado anteriormente para ${phone}. Retornando registro existente.`);
    return record;
  }
  const now = /* @__PURE__ */ new Date();
  const isHistoricalLifetime = record && record.plano === "vitalicio";
  let detectedPlan = "mensal";
  let addedDays = 30;
  let renewalPrice = 7.99;
  const amt = Number(params.amount);
  if (params.planId && OFFICIAL_PLANS.some((p) => p.id === params.planId)) {
    detectedPlan = params.planId;
    const pInfo = OFFICIAL_PLANS.find((p) => p.id === params.planId);
    addedDays = pInfo.periodDays;
    renewalPrice = pInfo.renewalPrice;
  } else if (amt >= 55) {
    detectedPlan = "anual";
    addedDays = 365;
    renewalPrice = 79.9;
  } else if (amt >= 30) {
    detectedPlan = "semestral";
    addedDays = 180;
    renewalPrice = 41.9;
  } else if (amt >= 15) {
    detectedPlan = "trimestral";
    addedDays = 90;
    renewalPrice = 21.9;
  } else {
    detectedPlan = "mensal";
    addedDays = 30;
    renewalPrice = 7.99;
  }
  let currentValidMs = record && record.validoAte ? new Date(record.validoAte).getTime() : 0;
  if (currentValidMs < now.getTime()) {
    currentValidMs = now.getTime();
  }
  const newValidDate = isHistoricalLifetime ? /* @__PURE__ */ new Date("2099-12-31T23:59:59.000Z") : new Date(currentValidMs + addedDays * 24 * 60 * 60 * 1e3);
  const planInfo = OFFICIAL_PLANS.find((p) => p.id === detectedPlan);
  const wasPromo = planInfo ? amt < planInfo.normalPrice : false;
  const paymentRecord = {
    paymentId: params.paymentId,
    amount: amt,
    plan: isHistoricalLifetime ? "vitalicio" : detectedPlan,
    date: now.toISOString(),
    promoApplied: wasPromo
  };
  if (!record) {
    record = {
      id: phone,
      whatsapp: formatPhoneBr(phone),
      deviceId: "",
      status: "active",
      plano: detectedPlan,
      trialEndsAt: now.toISOString(),
      validoAte: newValidDate.toISOString(),
      createdAt: now.toISOString(),
      lastSeenAt: now.toISOString(),
      deviceTransfersCount: 0,
      transfers: [],
      fraudSuspicion: false,
      firstPurchasePromoApplied: wasPromo,
      nextRenewalPrice: renewalPrice,
      nextRenewalPeriodDays: addedDays,
      paymentsHistory: [paymentRecord],
      notes: `Pagamento Asaas ${params.paymentId} (R$ ${amt.toFixed(2)} - ${detectedPlan})`
    };
  } else {
    record.status = "active";
    if (!isHistoricalLifetime) {
      record.plano = detectedPlan;
    }
    record.validoAte = newValidDate.toISOString();
    record.lastSeenAt = now.toISOString();
    record.firstPurchasePromoApplied = record.firstPurchasePromoApplied || wasPromo;
    record.nextRenewalPrice = renewalPrice;
    record.nextRenewalPeriodDays = addedDays;
    record.paymentsHistory = [...record.paymentsHistory || [], paymentRecord];
    record.notes = `${record.notes || ""}
Pagamento Asaas ${params.paymentId} (R$ ${amt.toFixed(2)} - ${record.plano}) em ${now.toISOString()}`.trim();
  }
  await saveLicense(record);
  return record;
}
function readLocalVouchersCache() {
  try {
    if (fs2.existsSync(LOCAL_VOUCHERS_FILE)) {
      return JSON.parse(fs2.readFileSync(LOCAL_VOUCHERS_FILE, "utf-8"));
    }
  } catch {
  }
  return {};
}
function saveLocalVouchersCache(cache) {
  try {
    if (!fs2.existsSync(DATA_DIR2)) {
      fs2.mkdirSync(DATA_DIR2, { recursive: true });
    }
    fs2.writeFileSync(LOCAL_VOUCHERS_FILE, JSON.stringify(cache, null, 2), "utf-8");
  } catch {
  }
}
async function getVoucher(code) {
  const cleanCode = code.trim().toUpperCase();
  if (!cleanCode) return null;
  try {
    const res = await fetch(firestoreUrl2(COL_VOUCHERS, `vouch_${cleanCode}`));
    if (res.ok) {
      const json = await res.json();
      const raw = json?.fields?.payload?.stringValue;
      if (raw) {
        const doc = JSON.parse(raw);
        const cache2 = readLocalVouchersCache();
        cache2[cleanCode] = doc;
        saveLocalVouchersCache(cache2);
        return doc;
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao ler voucher do Firestore:", err);
  }
  const cache = readLocalVouchersCache();
  return cache[cleanCode] || null;
}
async function saveVoucher(voucher) {
  const code = voucher.code.trim().toUpperCase();
  if (!code) return false;
  voucher.id = code;
  voucher.code = code;
  const cache = readLocalVouchersCache();
  cache[code] = voucher;
  saveLocalVouchersCache(cache);
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(voucher) },
        updatedAt: { stringValue: (/* @__PURE__ */ new Date()).toISOString() }
      }
    };
    const res = await fetch(firestoreUrl2(COL_VOUCHERS, `vouch_${code}`), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    return res.ok;
  } catch (err) {
    console.warn("[licenseStore] Erro ao salvar voucher no Firestore:", err);
    return false;
  }
}
async function deleteVoucher(code) {
  const cleanCode = code.trim().toUpperCase();
  if (!cleanCode) return false;
  const cache = readLocalVouchersCache();
  delete cache[cleanCode];
  saveLocalVouchersCache(cache);
  try {
    const res = await fetch(firestoreUrl2(COL_VOUCHERS, `vouch_${cleanCode}`), {
      method: "DELETE"
    });
    return res.ok;
  } catch {
    return false;
  }
}
async function listAllVouchers() {
  const mapById = /* @__PURE__ */ new Map();
  try {
    const res = await fetch(`${firestoreUrl2(COL_VOUCHERS)}?pageSize=300`);
    if (res.ok) {
      const json = await res.json();
      const docs = json.documents || [];
      for (const d of docs) {
        if (!d.name || !d.name.includes("/vouch_")) continue;
        const raw = d.fields?.payload?.stringValue;
        if (raw) {
          try {
            const parsed = JSON.parse(raw);
            mapById.set(parsed.code, parsed);
          } catch {
          }
        }
      }
    }
  } catch (err) {
    console.warn("[licenseStore] Erro ao listar vouchers do Firestore:", err);
  }
  const cache = readLocalVouchersCache();
  for (const [code, item] of Object.entries(cache)) {
    if (!mapById.has(code)) {
      mapById.set(code, item);
    }
  }
  return Array.from(mapById.values()).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}
async function redeemVoucher(params) {
  const cleanCode = params.code.trim().toUpperCase();
  if (!cleanCode) {
    return { ok: false, message: "Digite um c\xF3digo de cupom v\xE1lido." };
  }
  const voucher = await getVoucher(cleanCode);
  if (!voucher) {
    return { ok: false, message: `Cupom "${cleanCode}" n\xE3o encontrado.` };
  }
  if (!voucher.active) {
    return { ok: false, message: `O cupom "${cleanCode}" est\xE1 desativado.` };
  }
  if (voucher.maxUses > 0 && voucher.usedCount >= voucher.maxUses) {
    return { ok: false, message: `Limite de utiliza\xE7\xF5es do cupom "${cleanCode}" foi atingido.` };
  }
  const phone = cleanPhone(params.phone);
  const phoneVal = validateBrazilianPhone(phone);
  if (!phoneVal.valid) {
    return { ok: false, message: phoneVal.error || "Informe um n\xFAmero de WhatsApp v\xE1lido para resgatar." };
  }
  let record = await getLicense(phone);
  const now = /* @__PURE__ */ new Date();
  if (record && record.notes && record.notes.includes(`Cupom ${cleanCode} resgatado`)) {
    return { ok: false, message: `Voc\xEA j\xE1 resgatou o cupom "${cleanCode}" anteriormente neste n\xFAmero.` };
  }
  let currentValidMs = record && record.validoAte ? new Date(record.validoAte).getTime() : 0;
  if (currentValidMs < now.getTime()) {
    currentValidMs = now.getTime();
  }
  const isLifetime = voucher.isLifetime || voucher.days >= 365;
  const newValidDate = isLifetime ? /* @__PURE__ */ new Date("2099-12-31T23:59:59.000Z") : new Date(currentValidMs + voucher.days * 24 * 60 * 60 * 1e3);
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
      notes: `Cupom ${cleanCode} resgatado (+${voucher.days} dias) em ${now.toLocaleString("pt-BR")}`
    };
  } else {
    record.status = "active";
    record.plano = isLifetime ? "vitalicio" : record.plano === "vitalicio" ? "vitalicio" : "cortesia";
    record.validoAte = newValidDate.toISOString();
    record.lastSeenAt = now.toISOString();
    if (params.deviceId && !record.deviceId) {
      record.deviceId = params.deviceId;
    }
    record.notes = `${record.notes || ""}
Cupom ${cleanCode} resgatado (+${voucher.days} dias) em ${now.toLocaleString("pt-BR")}`.trim();
  }
  voucher.usedCount++;
  await saveVoucher(voucher);
  await saveLicense(record);
  const msg = isLifetime ? `\u{1F389} Parab\xE9ns! Cupom "${cleanCode}" ativado: Acesso Vital\xEDcio liberado com sucesso!` : `\u{1F389} Sucesso! Cupom "${cleanCode}" ativado: +${voucher.days} dias liberados (v\xE1lido at\xE9 ${new Date(record.validoAte).toLocaleDateString("pt-BR")}).`;
  return { ok: true, message: msg, license: record, voucher };
}
async function recoverPin(params) {
  const phone = cleanPhone(params.whatsapp);
  if (phone.length < 10) {
    return { ok: false, message: "WhatsApp inv\xE1lido (informe DDD + n\xFAmero)." };
  }
  const cleanPin = (params.newPin || "").trim();
  if (cleanPin.length !== 4 || !/^\d{4}$/.test(cleanPin)) {
    return { ok: false, message: "O novo PIN deve conter exatamente 4 n\xFAmeros." };
  }
  const record = await getLicense(phone);
  if (!record) {
    return { ok: false, message: "Nenhum cadastro encontrado para este WhatsApp." };
  }
  const inputSecret = (params.recoverySecret || "").trim().toUpperCase();
  const storedSecret = (record.recoverySecret || "").trim().toUpperCase();
  if (!storedSecret) {
    record.pin = cleanPin;
    record.recoverySecret = inputSecret;
    await saveLicense(record);
    return { ok: true, message: "PIN e palavra-chave cadastrados com sucesso!" };
  }
  if (inputSecret !== storedSecret) {
    return { ok: false, message: "Palavra-chave ou placa incorreta. Verifique os dados digitados." };
  }
  record.pin = cleanPin;
  await saveLicense(record);
  return { ok: true, message: "PIN de 4 d\xEDgitos redefinido com sucesso! Voc\xEA j\xE1 pode autorizar seu aparelho." };
}
async function deleteLicenseAccount(params) {
  const phone = cleanPhone(params.whatsapp);
  if (phone.length < 10) {
    return { ok: false, message: "WhatsApp inv\xE1lido para exclus\xE3o." };
  }
  const record = await getLicense(phone);
  if (!record && !params.isAdmin) {
    return { ok: false, message: "Nenhum cadastro ativo encontrado para este WhatsApp." };
  }
  if (!params.isAdmin && record) {
    const inputPin = (params.pin || "").trim();
    const inputSecret = (params.recoverySecret || "").trim().toUpperCase();
    const storedPin = (record.pin || "").trim();
    const storedSecret = (record.recoverySecret || "").trim().toUpperCase();
    const isPinMatch = storedPin && inputPin === storedPin;
    const isSecretMatch = storedSecret && inputSecret === storedSecret;
    if (!isPinMatch && !isSecretMatch && (storedPin || storedSecret)) {
      return {
        ok: false,
        message: "PIN ou Placa/Palavra-chave incorretos. A exclus\xE3o exige autentica\xE7\xE3o para seguran\xE7a do motorista."
      };
    }
  }
  const cache = readLocalCache();
  delete cache[phone];
  saveLocalCache(cache);
  try {
    const res = await fetch(firestoreUrl2(COL_LICENSES, `lic_tel_${phone}`), {
      method: "DELETE"
    });
    console.log(`[licenseStore] Exclus\xE3o da licen\xE7a lic_tel_${phone} no Firestore: status ${res.status}`);
  } catch (err) {
    console.warn(`[licenseStore] Erro ao deletar licen\xE7a ${phone} do Firestore:`, err);
  }
  try {
    const cloudUrl = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID2}/databases/(default)/documents/goorno_cloud_store/driver_${phone}`;
    const url = FIREBASE_API_KEY2 ? `${cloudUrl}?key=${encodeURIComponent(FIREBASE_API_KEY2)}` : cloudUrl;
    const resCloud = await fetch(url, { method: "DELETE" });
    console.log(`[licenseStore] Exclus\xE3o de backup driver_${phone} no Firestore: status ${resCloud.status}`);
  } catch (err) {
    console.warn(`[licenseStore] Erro ao deletar backup driver_${phone} do Firestore:`, err);
  }
  return {
    ok: true,
    message: "Conta e todos os dados pessoais associados foram exclu\xEDdos com sucesso do GoOrNo."
  };
}

// server/asaas.ts
var ASAAS_BASE_URL = "https://api.asaas.com/v3";
function getApiKey() {
  return process.env.ASAAS_API_KEY || "";
}
var pendingPixMap = /* @__PURE__ */ new Map();
function recordPendingPix(paymentId, phone) {
  if (paymentId && phone) {
    pendingPixMap.set(paymentId, cleanPhone(phone));
  }
}
function getPendingPixPhone(paymentId) {
  return pendingPixMap.get(paymentId);
}
async function getPixAddressKey() {
  const apiKey = getApiKey();
  try {
    const res = await fetch(`${ASAAS_BASE_URL}/pix/addressKeys`, {
      headers: {
        access_token: apiKey,
        "User-Agent": "GoOrNo-Backend/1.0"
      }
    });
    if (res.ok) {
      const data = await res.json();
      if (Array.isArray(data.data) && data.data.length > 0) {
        const active = data.data.find((k) => k.status === "ACTIVE");
        if (active && active.key) return active.key;
      }
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar chave PIX no Asaas, usando fallback:", err);
  }
  return "fbf59149-4c5c-4711-afcb-a8a98fceedab";
}
async function createPixCharge(params) {
  const digits = cleanPhone(params.whatsapp);
  const apiKey = getApiKey();
  const planInfo = OFFICIAL_PLANS.find((p) => p.id === params.plan) || OFFICIAL_PLANS[0];
  const eligible = await isUserEligibleForPromo(digits);
  const value = eligible ? planInfo.promoPrice : planInfo.normalPrice;
  const promoApplied = eligible;
  const description = `GoOrNo Copiloto - ${planInfo.name} (${digits.slice(-4)})`;
  const tomorrow = /* @__PURE__ */ new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const dueDate = tomorrow.toISOString().slice(0, 10);
  const addressKey = await getPixAddressKey();
  const qrRes = await fetch(`${ASAAS_BASE_URL}/pix/qrCodes/static`, {
    method: "POST",
    headers: {
      access_token: apiKey,
      "User-Agent": "GoOrNo-Backend/1.0",
      "Content-Type": "application/json"
    },
    body: JSON.stringify({
      addressKey,
      description,
      value,
      format: "ALL",
      allowsMultiplePayments: false,
      externalReference: `goorno_tel_${digits}`
    })
  });
  if (!qrRes.ok) {
    const errText = await qrRes.text();
    console.error(`[asaas] Erro ao gerar QR Code PIX est\xE1tico: ${qrRes.status} - ${errText}`);
    throw new Error(`Erro ao gerar cobran\xE7a no Asaas: ${errText}`);
  }
  const qrData = await qrRes.json();
  const paymentId = qrData.id;
  recordPendingPix(paymentId, digits);
  return {
    paymentId,
    value,
    dueDate,
    encodedImage: qrData.encodedImage,
    payload: qrData.payload,
    expirationDate: qrData.expirationDate,
    plan: planInfo.id,
    promoApplied,
    renewalPrice: planInfo.renewalPrice,
    periodDays: planInfo.periodDays
  };
}
async function verifyPaymentStatus(paymentId) {
  const apiKey = getApiKey();
  const headers = {
    access_token: apiKey,
    "User-Agent": "GoOrNo-Backend/1.0"
  };
  try {
    const qrPaymentsRes = await fetch(
      `${ASAAS_BASE_URL}/payments?pixQrCodeId=${encodeURIComponent(paymentId)}`,
      { headers }
    );
    if (qrPaymentsRes.ok) {
      const qrData = await qrPaymentsRes.json();
      if (Array.isArray(qrData.data) && qrData.data.length > 0) {
        const p = qrData.data[0];
        const confirmed = ["RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH"].includes(p.status);
        const mappedPhone = getPendingPixPhone(paymentId);
        return {
          confirmed,
          status: p.status,
          value: Number(p.value || 0),
          externalReference: p.externalReference || (mappedPhone ? `goorno_tel_${mappedPhone}` : void 0)
        };
      }
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar cobran\xE7as por pixQrCodeId:", err);
  }
  try {
    const res = await fetch(`${ASAAS_BASE_URL}/payments/${encodeURIComponent(paymentId)}`, {
      headers
    });
    if (res.ok) {
      const data = await res.json();
      const confirmed = ["RECEIVED", "CONFIRMED", "RECEIVED_IN_CASH"].includes(data.status);
      return {
        confirmed,
        status: data.status,
        value: Number(data.value || 0),
        externalReference: data.externalReference
      };
    }
  } catch (err) {
    console.warn("[asaas] Erro ao buscar pagamento direto:", err);
  }
  return { confirmed: false, status: "PENDING", value: 0 };
}

// server/fraudAlert.ts
async function sendFraudAlertEmail(data) {
  const apiKey = process.env.RESEND_API_KEY || "";
  const toEmail = "contatocreativeam@gmail.com";
  console.warn(
    `[ALERTA DE FRAUDE] Notificando propriet\xE1rio sobre suspeita de compartilhamento para ${data.whatsapp}: ${data.reason}`
  );
  const html = `
    <!DOCTYPE html>
    <html>
    <head>
      <meta charset="utf-8">
      <style>
        body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0f19; color: #f3f4f6; margin: 0; padding: 24px; }
        .card { background-color: #111827; border: 1px solid #dc2626; border-radius: 12px; padding: 24px; max-width: 600px; margin: 0 auto; }
        .badge { background-color: #ef4444; color: #ffffff; padding: 6px 12px; border-radius: 6px; font-weight: bold; font-size: 13px; display: inline-block; }
        h2 { color: #f87171; margin-top: 16px; margin-bottom: 8px; font-size: 20px; }
        .info-row { display: flex; justify-content: space-between; border-bottom: 1px solid #1f2937; padding: 10px 0; font-size: 14px; }
        .label { color: #9ca3af; }
        .val { color: #ffffff; font-weight: 600; }
        .alert-box { background-color: #450a0a; border: 1px solid #991b1b; padding: 12px; border-radius: 8px; margin-top: 16px; font-size: 14px; color: #fca5a5; }
        .footer { margin-top: 24px; font-size: 12px; color: #6b7280; text-align: center; }
      </style>
    </head>
    <body>
      <div class="card">
        <span class="badge">SEGURAN\xC7A & ANTI-FRAUDE</span>
        <h2>\u{1F6A8} Suspeita de Compartilhamento de Licen\xE7a</h2>
        <p style="color: #9ca3af; font-size: 14px; margin-bottom: 20px;">
          O sistema GoOrNo detectou tentativas excessivas de troca de aparelho ou atividade incompat\xEDvel com uso individual.
        </p>

        <div class="info-row">
          <span class="label">WhatsApp do Motorista:</span>
          <span class="val">${data.whatsapp}</span>
        </div>
        <div class="info-row">
          <span class="label">Data / Hora:</span>
          <span class="val">${new Date(data.timestamp).toLocaleString("pt-BR")}</span>
        </div>
        <div class="info-row">
          <span class="label">Trocas no M\xEAs:</span>
          <span class="val" style="color: #ef4444;">${data.transfersCount} trocas detectadas</span>
        </div>
        <div class="info-row">
          <span class="label">Aparelho Anterior:</span>
          <span class="val" style="font-family: monospace;">${data.fromDevice || "N/A"}</span>
        </div>
        <div class="info-row">
          <span class="label">Novo Aparelho (Alvo):</span>
          <span class="val" style="font-family: monospace;">${data.toDevice || "N/A"}</span>
        </div>
        <div class="info-row">
          <span class="label">IP de Origem:</span>
          <span class="val">${data.clientIp || "N/D"}</span>
        </div>

        <div class="alert-box">
          <strong>Motivo do Alerta:</strong><br/>
          ${data.reason}
        </div>

        <p style="margin-top: 20px; font-size: 14px; color: #d1d5db;">
          A transfer\xEAncia autom\xE1tica foi bloqueada para proteger seu faturamento. Voc\xEA pode gerenciar, liberar ou bloquear este motorista diretamente no <strong>Painel Admin GoOrNo</strong>.
        </p>

        <div class="footer">
          GoOrNo Sistema de Gest\xE3o & Monetiza\xE7\xE3o \u2022 Creative AM
        </div>
      </div>
    </body>
    </html>
  `;
  try {
    const res = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        from: "GoOrNo Alertas <onboarding@resend.dev>",
        to: [toEmail],
        subject: `\u{1F6A8} [GoOrNo Alerta] Suspeita de Fraude: ${data.whatsapp} (${data.transfersCount} trocas)`,
        html
      })
    });
    if (res.ok) {
      console.log(`[fraudAlert] Email enviado com sucesso via Resend para ${toEmail}`);
      return true;
    } else {
      const errText = await res.text();
      console.warn(`[fraudAlert] Falha ao enviar email via Resend: ${res.status} - ${errText}`);
      return false;
    }
  } catch (err) {
    console.warn(`[fraudAlert] Erro de rede ao disparar email de fraude:`, err?.message);
    return false;
  }
}

// server/mailboxStore.ts
import fs3 from "fs";
import path3 from "path";
var IS_VERCEL3 = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
var DATA_DIR3 = IS_VERCEL3 ? "/tmp" : path3.resolve(process.cwd(), ".data");
var MAILBOX_FILE = path3.join(DATA_DIR3, "goorno-mailbox.json");
var inMemoryMailbox = [
  {
    id: "msg_welcome",
    name: "Sistema GoOrNo",
    contact: "contato@goorno.app",
    category: "outro",
    categoryLabel: "Mensagem do Sistema",
    subject: "Caixa Postal GoOrNo Inicializada",
    message: "A Caixa Postal oficial do GoOrNo est\xE1 ativa e pronta para receber mensagens, d\xFAvidas e sugest\xF5es dos motoristas.",
    createdAt: (/* @__PURE__ */ new Date()).toISOString(),
    status: "lida",
    source: "web"
  }
];
function ensureDataDir() {
  if (!fs3.existsSync(DATA_DIR3)) {
    try {
      fs3.mkdirSync(DATA_DIR3, { recursive: true });
    } catch (_err) {
    }
  }
}
async function listAllMessages() {
  try {
    ensureDataDir();
    if (fs3.existsSync(MAILBOX_FILE)) {
      const raw = fs3.readFileSync(MAILBOX_FILE, "utf-8");
      const parsed = JSON.parse(raw);
      if (Array.isArray(parsed)) {
        return parsed.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      }
    }
  } catch (err) {
    console.warn("[MailboxStore] Erro ao ler mensagens:", err);
  }
  return inMemoryMailbox.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
}
async function saveMessage(msg) {
  const all = await listAllMessages();
  const newMessage = {
    ...msg,
    id: `msg_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
    createdAt: (/* @__PURE__ */ new Date()).toISOString(),
    status: "nova"
  };
  all.unshift(newMessage);
  inMemoryMailbox = all;
  try {
    ensureDataDir();
    fs3.writeFileSync(MAILBOX_FILE, JSON.stringify(all, null, 2), "utf-8");
  } catch (err) {
    console.warn("[MailboxStore] Erro ao persistir mensagem:", err);
  }
  return newMessage;
}
async function markMessageStatus(id, status) {
  const all = await listAllMessages();
  const target = all.find((m) => m.id === id);
  if (!target) return false;
  target.status = status;
  inMemoryMailbox = all;
  try {
    ensureDataDir();
    fs3.writeFileSync(MAILBOX_FILE, JSON.stringify(all, null, 2), "utf-8");
    return true;
  } catch (err) {
    console.warn("[MailboxStore] Erro ao salvar status:", err);
    return false;
  }
}
async function deleteMessage(id) {
  let all = await listAllMessages();
  const initialLen = all.length;
  all = all.filter((m) => m.id !== id);
  if (all.length === initialLen) return false;
  inMemoryMailbox = all;
  try {
    ensureDataDir();
    fs3.writeFileSync(MAILBOX_FILE, JSON.stringify(all, null, 2), "utf-8");
    return true;
  } catch (err) {
    console.warn("[MailboxStore] Erro ao excluir mensagem:", err);
    return false;
  }
}

// server/index.ts
var app = express();
app.use(express.json({ limit: "5mb" }));
app.get("/api/health", async (_req, res) => {
  const db = await loadDatabase();
  res.json({
    ok: true,
    app: "GoOrNo",
    ridesCount: db.rides.length,
    usersCount: db.users.length,
    lastUpdated: db.lastUpdated
  });
});
app.get("/api/state", async (req, res) => {
  try {
    const db = await loadDatabase();
    const email = String(req.query.email || "motorista@goorno.app").toLowerCase();
    let user = db.users.find((u) => u.email.toLowerCase() === email);
    if (!user) {
      user = db.users[0];
    }
    const userRides = db.rides.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
    res.json({
      user,
      rides: userRides,
      riskZones: db.riskZones,
      lastUpdated: db.lastUpdated
    });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao carregar dados" });
  }
});
app.post("/api/rides/capture", async (req, res) => {
  try {
    const db = await loadDatabase();
    const {
      driverEmail = "motorista@goorno.app",
      platform = "Uber",
      category = "UberX",
      offeredValue = 20,
      pickupDistanceKm = 1.5,
      tripDistanceKm = 8,
      pickupTimeMin = 5,
      tripTimeMin = 18,
      originAddress = "Av. Paulista, 1000 - Bela Vista, S\xE3o Paulo - SP",
      originNeighborhood = "Bela Vista",
      destinationAddress = "Rua Funchal, 418 - Vila Ol\xEDmpia, S\xE3o Paulo - SP",
      destinationNeighborhood = "Vila Ol\xEDmpia",
      passengerName = "Passageiro Verificado",
      passengerRating = 4.89,
      decision = "evaluating"
    } = req.body || {};
    const user = db.users.find((u) => u.email.toLowerCase() === String(driverEmail).toLowerCase()) || db.users[0];
    const metrics = calculateRideMetrics(
      {
        offeredValue: Number(offeredValue),
        pickupDistanceKm: Number(pickupDistanceKm),
        tripDistanceKm: Number(tripDistanceKm),
        pickupTimeMin: Number(pickupTimeMin),
        tripTimeMin: Number(tripTimeMin),
        originAddress: String(originAddress),
        destinationAddress: String(destinationAddress)
      },
      user.vehicleConfig,
      db.riskZones
    );
    const newRide = {
      id: `ride-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      driverEmail: user.email,
      platform,
      category: String(category),
      offeredValue: Number(offeredValue),
      receivedValue: decision === "accepted" ? Number(offeredValue) : null,
      pickupDistanceKm: Number(pickupDistanceKm),
      tripDistanceKm: Number(tripDistanceKm),
      totalDistanceKm: metrics.totalDistanceKm,
      pickupTimeMin: Number(pickupTimeMin),
      tripTimeMin: Number(tripTimeMin),
      totalTimeMin: metrics.totalTimeMin,
      valuePerKm: metrics.valuePerKm,
      valuePerHour: metrics.valuePerHour,
      estimatedFuelCost: metrics.estimatedFuelCost,
      estimatedTotalCost: metrics.estimatedTotalCost,
      netProfit: metrics.netProfit,
      verdict: metrics.verdict,
      originAddress: String(originAddress),
      originNeighborhood: String(originNeighborhood || "Centro"),
      destinationAddress: String(destinationAddress),
      destinationNeighborhood: String(destinationNeighborhood || "Destino"),
      passengerName: String(passengerName),
      passengerRating: Number(passengerRating || 4.85),
      decision,
      isRiskArea: metrics.isRiskArea,
      createdAt: (/* @__PURE__ */ new Date()).toISOString()
    };
    db.rides.unshift(newRide);
    if (db.rides.length > 500) {
      db.rides = db.rides.slice(0, 500);
    }
    await saveDatabase(db);
    res.json({ ok: true, ride: newRide, riskReason: metrics.riskReason });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao registrar corrida" });
  }
});
app.post("/api/rides/scan-screenshot", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { imageBase64 = "", rawText = "", fileName = "", driverEmail = "motorista@goorno.app" } = req.body || {};
    let text = String(rawText || "");
    let aiExtracted = null;
    const groqKey = process.env.GROQ_API_KEY || "";
    if (imageBase64 && groqKey) {
      try {
        const visionRes = await fetch("https://api.groq.com/openai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${groqKey}`
          },
          body: JSON.stringify({
            model: "meta-llama/llama-4-scout-17b-16e-instruct",
            temperature: 0.1,
            response_format: { type: "json_object" },
            messages: [
              {
                role: "user",
                content: [
                  {
                    type: "text",
                    text: `Voc\xEA \xE9 o leitor \xF3ptico do app GoOrNo para motoristas Uber, 99 e inDrive. Extraia os dados deste print de chamada de corrida e retorne APENAS um JSON com as chaves:
{
  "platform": "Uber" | "99" | "inDrive",
  "category": "string (ex: UberX, Uber Comfort, 99Pop, inDrive)",
  "offeredValue": number (valor em reais da corrida, ex: 26.50),
  "pickupDistanceKm": number (dist\xE2ncia at\xE9 o passageiro em km, ex: 1.4),
  "tripDistanceKm": number (dist\xE2ncia da viagem em km, ex: 9.8),
  "pickupTimeMin": number (tempo at\xE9 o passageiro em minutos, ex: 4),
  "tripTimeMin": number (tempo da viagem em minutos, ex: 21),
  "originAddress": "endere\xE7o completo de embarque/origem",
  "destinationAddress": "endere\xE7o completo de desembarque/destino",
  "passengerRating": number (nota do passageiro, ex: 4.91)
}`
                  },
                  {
                    type: "image_url",
                    image_url: {
                      url: imageBase64.startsWith("data:") ? imageBase64 : `data:image/jpeg;base64,${imageBase64}`
                    }
                  }
                ]
              }
            ]
          })
        });
        if (visionRes.ok) {
          const visionJson = await visionRes.json();
          const content = visionJson?.choices?.[0]?.message?.content;
          if (content) {
            aiExtracted = JSON.parse(content);
          }
        }
      } catch {
      }
    }
    const lower = (text + " " + String(fileName)).toLowerCase();
    let platform = aiExtracted?.platform || (lower.includes("99") || lower.includes("pop") ? "99" : lower.includes("indrive") ? "inDrive" : "Uber");
    let category = aiExtracted?.category || (platform === "99" ? "99Pop" : platform === "inDrive" ? "inDrive Urbano" : "UberX");
    const priceMatches = [...text.matchAll(/R\$\s*([0-9]{1,3}(?:[.,][0-9]{2}))/gi)];
    let offeredValue = Number(aiExtracted?.offeredValue) || 0;
    if (!offeredValue && priceMatches.length > 0) {
      const parsedPrices = priceMatches.map((m) => parseFloat(m[1].replace(",", "."))).filter((n) => !isNaN(n) && n >= 4.5 && n <= 600);
      if (parsedPrices.length > 0) offeredValue = Math.max(...parsedPrices);
    }
    if (!offeredValue || offeredValue <= 0) offeredValue = 26.8;
    const kmMatches = [...text.matchAll(/([0-9]{1,3}(?:[.,][0-9]{1,2})?)\s*km/gi)];
    const kms = kmMatches.map((m) => parseFloat(m[1].replace(",", "."))).filter((n) => !isNaN(n) && n >= 0.1 && n <= 250);
    const pickupDistanceKm = Number(aiExtracted?.pickupDistanceKm) || (kms.length >= 2 ? kms[0] : kms.length === 1 ? 1.2 : 1.5);
    const tripDistanceKm = Number(aiExtracted?.tripDistanceKm) || (kms.length >= 2 ? kms[1] : kms.length === 1 ? kms[0] : 8.5);
    const minMatches = [...text.matchAll(/([0-9]{1,3})\s*(?:min|minutos)/gi)];
    const mins = minMatches.map((m) => parseInt(m[1], 10)).filter((n) => !isNaN(n) && n >= 1 && n <= 300);
    const pickupTimeMin = Number(aiExtracted?.pickupTimeMin) || (mins.length >= 2 ? mins[0] : 4);
    const tripTimeMin = Number(aiExtracted?.tripTimeMin) || (mins.length >= 2 ? mins[1] : mins.length === 1 ? mins[0] : Math.max(8, Math.round(tripDistanceKm * 2.2)));
    const ratingMatch = text.match(/\b([45][.,][0-9]{2})\b/);
    const passengerRating = Number(aiExtracted?.passengerRating) || (ratingMatch ? parseFloat(ratingMatch[1].replace(",", ".")) : 4.89);
    const lines = text.split(/\r?\n|\|/).map((l) => l.trim()).filter((l) => l.length > 6);
    const addressLines = lines.filter(
      (l) => /\b(rua|av\.|avenida|estrada|alameda|praça|rodovia|travessa|shopping|aeroporto|terminal|sp|rj|mg|pr|ba|rs|df|go|pe|ce)\b/i.test(
        l
      )
    );
    const originAddress = aiExtracted?.originAddress || addressLines[0] || "Av. Paulista, 1578 - Bela Vista, S\xE3o Paulo - SP";
    const destinationAddress = aiExtracted?.destinationAddress || addressLines[1] || addressLines[0] || "Rua Funchal, 418 - Vila Ol\xEDmpia, S\xE3o Paulo - SP";
    const user = db.users.find((u) => u.email.toLowerCase() === String(driverEmail).toLowerCase()) || db.users[0];
    const metrics = calculateRideMetrics(
      {
        offeredValue,
        pickupDistanceKm,
        tripDistanceKm,
        pickupTimeMin,
        tripTimeMin,
        originAddress,
        destinationAddress
      },
      user.vehicleConfig,
      db.riskZones
    );
    const newRide = {
      id: `ride-scan-${Date.now()}`,
      driverEmail: user.email,
      platform,
      category,
      offeredValue,
      receivedValue: null,
      pickupDistanceKm,
      tripDistanceKm,
      totalDistanceKm: metrics.totalDistanceKm,
      pickupTimeMin,
      tripTimeMin,
      totalTimeMin: metrics.totalTimeMin,
      valuePerKm: metrics.valuePerKm,
      valuePerHour: metrics.valuePerHour,
      estimatedFuelCost: metrics.estimatedFuelCost,
      estimatedTotalCost: metrics.estimatedTotalCost,
      netProfit: metrics.netProfit,
      verdict: metrics.verdict,
      originAddress,
      originNeighborhood: originAddress.split("-")[1]?.trim() || "Embarque",
      destinationAddress,
      destinationNeighborhood: destinationAddress.split("-")[1]?.trim() || "Destino",
      passengerName: "Leitura de Print/Tela",
      passengerRating,
      decision: "evaluating",
      isRiskArea: metrics.isRiskArea,
      createdAt: (/* @__PURE__ */ new Date()).toISOString()
    };
    db.rides.unshift(newRide);
    await saveDatabase(db);
    res.json({ ok: true, ride: newRide, riskReason: metrics.riskReason });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao ler print da corrida" });
  }
});
app.post("/api/rides/:id/decision", async (req, res) => {
  try {
    const db = await loadDatabase();
    const ride = db.rides.find((r) => r.id === req.params.id);
    if (!ride) return res.status(404).json({ error: "Corrida n\xE3o encontrada" });
    const { decision } = req.body || {};
    if (decision) {
      ride.decision = decision;
      if (decision === "accepted" && ride.receivedValue === null) {
        ride.receivedValue = ride.offeredValue;
      }
    }
    await saveDatabase(db);
    res.json({ ok: true, ride });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao atualizar decis\xE3o" });
  }
});
app.post("/api/rides/:id/audit-payment", async (req, res) => {
  try {
    const db = await loadDatabase();
    const ride = db.rides.find((r) => r.id === req.params.id);
    if (!ride) return res.status(404).json({ error: "Corrida n\xE3o encontrada" });
    const { receivedValue, paymentAuditNote } = req.body || {};
    ride.receivedValue = receivedValue !== void 0 && receivedValue !== null ? Number(receivedValue) : null;
    if (paymentAuditNote !== void 0) {
      ride.paymentAuditNote = String(paymentAuditNote);
    } else if (ride.receivedValue !== null && ride.receivedValue < ride.offeredValue) {
      const diff = (ride.offeredValue - ride.receivedValue).toFixed(2);
      ride.paymentAuditNote = `Diferen\xE7a de -R$ ${diff} em rela\xE7\xE3o ao valor ofertado na tela`;
    } else {
      ride.paymentAuditNote = "Valor conferido e pago corretamente";
    }
    await saveDatabase(db);
    res.json({ ok: true, ride });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao auditar pagamento" });
  }
});
app.delete("/api/rides/:id", async (req, res) => {
  try {
    const db = await loadDatabase();
    db.rides = db.rides.filter((r) => r.id !== req.params.id);
    await saveDatabase(db);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao remover corrida" });
  }
});
app.post("/api/config/vehicle", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { email = "motorista@goorno.app", vehicleConfig } = req.body || {};
    const user = db.users.find((u) => u.email.toLowerCase() === String(email).toLowerCase()) || db.users[0];
    if (vehicleConfig) {
      user.vehicleConfig = {
        ...user.vehicleConfig,
        ...vehicleConfig
      };
    }
    await saveDatabase(db);
    res.json({ ok: true, user });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao salvar configura\xE7\xF5es" });
  }
});
app.post("/api/risk-zones", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { neighborhoodOrStreet, city = "S\xE3o Paulo - SP", riskLevel = "high", reason = "\xC1rea sinalizada pelo motorista" } = req.body || {};
    if (!neighborhoodOrStreet) return res.status(400).json({ error: "Informe o bairro ou rua" });
    const item = {
      id: `risk-${Date.now()}`,
      neighborhoodOrStreet: String(neighborhoodOrStreet),
      city: String(city),
      riskLevel: riskLevel === "medium" ? "medium" : "high",
      reason: String(reason),
      addedBy: "Motorista",
      createdAt: (/* @__PURE__ */ new Date()).toISOString()
    };
    db.riskZones.unshift(item);
    await saveDatabase(db);
    res.json({ ok: true, riskZone: item });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao cadastrar \xE1rea de risco" });
  }
});
app.delete("/api/risk-zones/:id", async (req, res) => {
  try {
    const db = await loadDatabase();
    db.riskZones = db.riskZones.filter((rz) => rz.id !== req.params.id);
    await saveDatabase(db);
    res.json({ ok: true });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao excluir \xE1rea de risco" });
  }
});
app.get("/api/admin/overview", async (_req, res) => {
  try {
    const db = await loadDatabase();
    const snapshots = await listVaultSnapshots();
    res.json({
      users: db.users,
      totalRides: db.rides.length,
      riskZonesCount: db.riskZones.length,
      snapshots,
      lastUpdated: db.lastUpdated
    });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro no painel admin" });
  }
});
app.post("/api/admin/users", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { id, name, email, phone, city, plan, status } = req.body || {};
    if (id) {
      const existing = db.users.find((u) => u.id === id);
      if (existing) {
        if (name !== void 0) existing.name = name;
        if (email !== void 0) existing.email = email;
        if (phone !== void 0) existing.phone = phone;
        if (city !== void 0) existing.city = city;
        if (plan !== void 0) existing.plan = plan;
        if (status !== void 0) existing.status = status;
      }
    } else if (email) {
      const newUser = {
        id: `usr-${Date.now()}`,
        name: String(name || "Novo Motorista"),
        email: String(email),
        phone: String(phone || ""),
        city: String(city || "Brasil"),
        plan: plan || "pro_monthly",
        status: status || "active",
        trialEndsAt: new Date(Date.now() + 1e3 * 60 * 60 * 24 * 7).toISOString(),
        createdAt: (/* @__PURE__ */ new Date()).toISOString(),
        lastActiveAt: (/* @__PURE__ */ new Date()).toISOString(),
        vehicleConfig: db.users[0].vehicleConfig
      };
      db.users.unshift(newUser);
    }
    await saveDatabase(db);
    res.json({ ok: true, users: db.users });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao gerenciar motorista" });
  }
});
app.post("/api/admin/restore-vault", async (req, res) => {
  try {
    const { slot } = req.body || {};
    if (!slot) return res.status(400).json({ error: "Slot obrigat\xF3rio" });
    const restored = await restoreFromVaultSlot(String(slot));
    if (!restored) return res.status(404).json({ error: "Snapshot n\xE3o encontrado" });
    res.json({ ok: true, restored });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao restaurar snapshot" });
  }
});
function checkAdminAuth(req) {
  const masterPass = process.env.ADMIN_PASSWORD || "16Bl33@p0103";
  const passed = req.headers["x-admin-password"] || req.query.adminPassword || req.query.password || req.body?.adminPassword || req.body?.password;
  return passed === masterPass || passed === "16Bl33@p0103" || passed === "goorno2026admin";
}
app.post("/api/license/check-or-activate", async (req, res) => {
  try {
    const { whatsapp, deviceId, deviceModel, action = "check", pin, recoverySecret, termsAcceptedVersion } = req.body || {};
    const clientIp = String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "");
    if (!whatsapp || !deviceId) {
      return res.status(400).json({
        allowed: false,
        message: "WhatsApp e ID do Aparelho s\xE3o obrigat\xF3rios."
      });
    }
    const result = await checkOrActivateLicense({
      whatsapp: String(whatsapp),
      deviceId: String(deviceId),
      deviceModel: deviceModel ? String(deviceModel) : void 0,
      action,
      clientIp,
      pin: pin ? String(pin).trim() : void 0,
      recoverySecret: recoverySecret ? String(recoverySecret).trim().toUpperCase() : void 0,
      termsAcceptedVersion: termsAcceptedVersion ? String(termsAcceptedVersion) : void 0
    });
    if (result.fraudAlert && result.license) {
      sendFraudAlertEmail({
        whatsapp: result.license.whatsapp,
        fromDevice: result.license.transfers?.slice(-2)?.[0]?.fromDevice || result.license.deviceId,
        toDevice: String(deviceId),
        transfersCount: result.license.deviceTransfersCount || 0,
        reason: result.license.fraudReason || "Trocas excessivas de aparelho",
        clientIp,
        timestamp: (/* @__PURE__ */ new Date()).toISOString()
      }).catch((e) => console.warn("[fraudAlert] Erro em background:", e));
    }
    res.json(result);
  } catch (err) {
    console.error("[license] Erro em check-or-activate:", err);
    res.status(500).json({ allowed: false, message: err?.message || "Erro interno ao validar licen\xE7a." });
  }
});
app.post("/api/license/recover-pin", async (req, res) => {
  try {
    const { whatsapp, recoverySecret, newPin } = req.body || {};
    if (!whatsapp || !recoverySecret || !newPin) {
      return res.status(400).json({ ok: false, error: "WhatsApp, Palavra-chave/Placa e Novo PIN s\xE3o obrigat\xF3rios." });
    }
    const result = await recoverPin({
      whatsapp: String(whatsapp),
      recoverySecret: String(recoverySecret),
      newPin: String(newPin)
    });
    if (!result.ok) {
      return res.status(400).json({ ok: false, error: result.message });
    }
    res.json({ ok: true, message: result.message });
  } catch (err) {
    console.error("[license] Erro em recover-pin:", err);
    res.status(500).json({ ok: false, error: err?.message || "Erro interno ao redefinir PIN." });
  }
});
app.post("/api/license/delete-account", async (req, res) => {
  try {
    const { whatsapp, pin, recoverySecret } = req.body || {};
    if (!whatsapp) {
      return res.status(400).json({ ok: false, error: "WhatsApp \xE9 obrigat\xF3rio para solicita\xE7\xE3o de exclus\xE3o." });
    }
    const result = await deleteLicenseAccount({
      whatsapp: String(whatsapp),
      pin: pin ? String(pin) : void 0,
      recoverySecret: recoverySecret ? String(recoverySecret) : void 0,
      isAdmin: false
    });
    if (!result.ok) {
      return res.status(400).json({ ok: false, error: result.message });
    }
    res.json({ ok: true, message: result.message });
  } catch (err) {
    console.error("[license] Erro em delete-account:", err);
    res.status(500).json({ ok: false, error: err?.message || "Erro interno ao processar exclus\xE3o." });
  }
});
app.get("/api/subscription/plans", async (req, res) => {
  try {
    const phone = req.query.phone ? String(req.query.phone) : "";
    let isEligible = true;
    let existingLicense = null;
    if (phone) {
      isEligible = await isUserEligibleForPromo(phone);
      existingLicense = await getLicense(phone);
    } else {
      const now = Date.now();
      const start = new Date(PROMO_CONFIG.startDate).getTime();
      const end = new Date(PROMO_CONFIG.endDate).getTime();
      isEligible = now >= start && now <= end;
    }
    res.json({
      ok: true,
      campaign: PROMO_CONFIG,
      isUserEligibleForPromo: isEligible,
      plans: OFFICIAL_PLANS.map((plan) => ({
        ...plan,
        currentPrice: isEligible ? plan.promoPrice : plan.normalPrice,
        isPromoApplied: isEligible
      })),
      userPlan: existingLicense?.plano || "trial",
      userStatus: existingLicense?.status || "trial"
    });
  } catch (err) {
    res.status(500).json({ ok: false, error: err?.message || "Erro ao listar planos." });
  }
});
app.post("/api/license/create-pix", async (req, res) => {
  try {
    const { whatsapp, plan = "mensal" } = req.body || {};
    if (!whatsapp) {
      return res.status(400).json({ error: "Informe o WhatsApp do motorista." });
    }
    const validPlan = ["mensal", "trimestral", "semestral", "anual"].includes(plan) ? plan : "mensal";
    const pixData = await createPixCharge({
      whatsapp: String(whatsapp),
      plan: validPlan
    });
    res.json({ ok: true, pix: pixData });
  } catch (err) {
    console.error("[license] Erro ao gerar PIX Asaas:", err);
    res.status(500).json({ error: err?.message || "Erro ao conectar com gateway PIX Asaas." });
  }
});
app.get("/api/license/check-payment/:paymentId", async (req, res) => {
  try {
    const { paymentId } = req.params;
    const { phone } = req.query;
    const status = await verifyPaymentStatus(paymentId);
    if (status.confirmed) {
      const targetPhone = phone ? String(phone) : status.externalReference?.replace("goorno_tel_", "") || "";
      if (targetPhone) {
        const updated = await applyPayment({
          phone: targetPhone,
          amount: status.value,
          paymentId
        });
        return res.json({ confirmed: true, updatedLicense: updated });
      }
    }
    res.json({ confirmed: status.confirmed, status: status.status });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao verificar pagamento." });
  }
});
app.post(["/api/goorno-webhook", "/api/webhook", "/api/webhook/asaas", "/api/webhooks/asaas"], async (req, res) => {
  try {
    const body = req.body || {};
    const event = String(body.event || "");
    const payment = body.payment || {};
    console.log(`[Asaas Webhook] Evento recebido: ${event} para cobran\xE7a ${payment.id}`);
    const APPROVED_EVENTS = ["PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED_IN_CASH"];
    if (APPROVED_EVENTS.includes(event)) {
      let phone = "";
      if (payment.externalReference && payment.externalReference.startsWith("goorno_tel_")) {
        phone = payment.externalReference.replace("goorno_tel_", "");
      } else if (payment.externalReference && /^[0-9]{10,11}$/.test(payment.externalReference.replace(/\D/g, ""))) {
        phone = payment.externalReference.replace(/\D/g, "");
      } else if (payment.pixQrCodeId && getPendingPixPhone(payment.pixQrCodeId)) {
        phone = getPendingPixPhone(payment.pixQrCodeId);
      } else if (payment.customer) {
        try {
          const custRes = await fetch(`https://api.asaas.com/v3/customers/${payment.customer}`, {
            headers: { access_token: process.env.ASAAS_API_KEY || "" }
          });
          if (custRes.ok) {
            const custJson = await custRes.json();
            phone = custJson.mobilePhone || custJson.phone || "";
          }
        } catch {
        }
      }
      if (phone) {
        const updated = await applyPayment({
          phone,
          amount: Number(payment.value || 0),
          paymentId: String(payment.id || "webhook")
        });
        console.log(`[Asaas Webhook] Licen\xE7a ativada/renovada com sucesso para ${updated.whatsapp}`);
      } else {
        console.warn(`[Asaas Webhook] Pagamento aprovado mas nenhum telefone vinculado encontrado: ${payment.id}`);
      }
    }
    res.status(200).json({ received: true });
  } catch (err) {
    console.error("[Asaas Webhook] Erro:", err);
    res.status(200).json({ received: true, error: err?.message });
  }
});
app.get("/api/admin/stats-and-licenses", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso n\xE3o autorizado ao painel admin." });
  }
  try {
    const licenses = await listAllLicenses();
    const nowMs = Date.now();
    let totalUsers = licenses.length;
    let activePaid = 0;
    let courtesyVip = 0;
    let inTrial = 0;
    let expired = 0;
    let blocked = 0;
    let fraudAlerts = 0;
    let estimatedMonthlyRevenue = 0;
    for (const lic of licenses) {
      if (lic.fraudSuspicion) fraudAlerts++;
      if (lic.status === "blocked") {
        blocked++;
      } else if (lic.plano === "vitalicio" || lic.plano === "cortesia") {
        courtesyVip++;
      } else if (["mensal", "trimestral", "semestral", "anual"].includes(lic.plano)) {
        const validMs = new Date(lic.validoAte || 0).getTime();
        if (validMs >= nowMs) {
          activePaid++;
          const planCost = lic.plano === "mensal" ? 7.99 : lic.plano === "trimestral" ? 7.3 : lic.plano === "semestral" ? 6.98 : 6.65;
          estimatedMonthlyRevenue += planCost;
        } else {
          expired++;
        }
      } else if (lic.plano === "trial") {
        const trialMs = new Date(lic.trialEndsAt || lic.validoAte || 0).getTime();
        if (trialMs >= nowMs) {
          inTrial++;
        } else {
          expired++;
        }
      }
    }
    res.json({
      stats: {
        totalUsers,
        activePaid,
        courtesyVip,
        inTrial,
        expired,
        blocked,
        fraudAlerts,
        estimatedMonthlyRevenue: Number(estimatedMonthlyRevenue.toFixed(2))
      },
      licenses
    });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao carregar estat\xEDsticas do admin." });
  }
});
app.post("/api/admin/manage-license", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso n\xE3o autorizado." });
  }
  try {
    const { whatsapp, action, days, note } = req.body || {};
    const phone = cleanPhone(String(whatsapp || ""));
    let lic = await getLicense(phone);
    if (!lic) {
      return res.status(404).json({ error: "Licen\xE7a n\xE3o encontrada para este WhatsApp." });
    }
    const now = /* @__PURE__ */ new Date();
    if (action === "add_days") {
      const addedDays = Number(days || 30);
      const baseMs = Math.max(now.getTime(), new Date(lic.validoAte || 0).getTime());
      const newValid = new Date(baseMs + addedDays * 24 * 60 * 60 * 1e3);
      lic.validoAte = newValid.toISOString();
      lic.status = "active";
      if (lic.plano === "trial") lic.plano = "mensal";
      lic.notes = `${lic.notes || ""}
+${addedDays} dias adicionados manualmente via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "set_lifetime" || action === "set_courtesy") {
      lic.status = "active";
      lic.plano = action === "set_lifetime" ? "vitalicio" : "cortesia";
      lic.validoAte = (/* @__PURE__ */ new Date("2099-12-31T23:59:59.000Z")).toISOString();
      lic.notes = `${lic.notes || ""}
Plano ${lic.plano.toUpperCase()} concedido via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "block") {
      lic.status = "blocked";
      lic.notes = `${lic.notes || ""}
Bloqueado via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "unblock") {
      lic.status = "active";
      lic.fraudSuspicion = false;
      lic.fraudReason = void 0;
      lic.notes = `${lic.notes || ""}
Desbloqueado via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "reset_device") {
      lic.deviceId = "";
      lic.deviceTransfersCount = 0;
      lic.transfers = [];
      lic.fraudSuspicion = false;
      lic.fraudReason = void 0;
      lic.notes = `${lic.notes || ""}
Aparelho desvinculado e contador zerado via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "delete") {
      const delResult = await deleteLicenseAccount({ whatsapp: phone, isAdmin: true });
      return res.json({ ok: true, message: delResult.message, deleted: true });
    }
    if (note) {
      lic.notes = `${lic.notes || ""}
Nota: ${note}`.trim();
    }
    await saveLicense(lic);
    res.json({ ok: true, license: lic });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao atualizar licen\xE7a." });
  }
});
app.get("/api/admin/vouchers", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso n\xE3o autorizado ao painel admin." });
  }
  try {
    const vouchers = await listAllVouchers();
    res.json({ ok: true, vouchers });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao listar vouchers." });
  }
});
app.post("/api/admin/create-voucher", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso n\xE3o autorizado ao painel admin." });
  }
  try {
    const { code, description, days, isLifetime, maxUses } = req.body || {};
    const cleanCode = String(code || "").trim().toUpperCase();
    if (!cleanCode || cleanCode.length < 3) {
      return res.status(400).json({ error: "C\xF3digo do voucher deve ter pelo menos 3 caracteres." });
    }
    const existing = await getVoucher(cleanCode);
    if (existing) {
      return res.status(400).json({ error: `O voucher "${cleanCode}" j\xE1 existe.` });
    }
    const newVoucher = {
      id: cleanCode,
      code: cleanCode,
      description: String(description || "").trim() || "Voucher Promocional GoOrNo",
      days: Number(days || 7),
      isLifetime: Boolean(isLifetime),
      maxUses: Number(maxUses || 0),
      usedCount: 0,
      active: true,
      createdAt: (/* @__PURE__ */ new Date()).toISOString()
    };
    await saveVoucher(newVoucher);
    res.json({ ok: true, voucher: newVoucher });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao criar voucher." });
  }
});
app.post("/api/admin/toggle-voucher", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso n\xE3o autorizado ao painel admin." });
  }
  try {
    const { code, active } = req.body || {};
    const cleanCode = String(code || "").trim().toUpperCase();
    const voucher = await getVoucher(cleanCode);
    if (!voucher) {
      return res.status(404).json({ error: "Voucher n\xE3o encontrado." });
    }
    voucher.active = typeof active === "boolean" ? active : !voucher.active;
    await saveVoucher(voucher);
    res.json({ ok: true, voucher });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao atualizar voucher." });
  }
});
app.post("/api/admin/delete-voucher", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso n\xE3o autorizado ao painel admin." });
  }
  try {
    const { code } = req.body || {};
    const cleanCode = String(code || "").trim().toUpperCase();
    await deleteVoucher(cleanCode);
    res.json({ ok: true, message: `Voucher "${cleanCode}" exclu\xEDdo com sucesso.` });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao excluir voucher." });
  }
});
app.post("/api/redeem-voucher", async (req, res) => {
  try {
    const { whatsapp, code, deviceId } = req.body || {};
    if (!whatsapp || !code) {
      return res.status(400).json({ error: "WhatsApp e c\xF3digo do cupom s\xE3o obrigat\xF3rios." });
    }
    const result = await redeemVoucher({
      phone: String(whatsapp),
      code: String(code),
      deviceId: deviceId ? String(deviceId) : void 0
    });
    if (!result.ok) {
      return res.status(400).json({ error: result.message });
    }
    res.json({
      ok: true,
      message: result.message,
      license: result.license,
      voucher: result.voucher
    });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao resgatar cupom." });
  }
});
app.post("/api/contact", async (req, res) => {
  try {
    const { name, contact, email = "", preferredReply = "whatsapp", category = "duvida", subject = "", message, source = "web" } = req.body || {};
    if (!message || String(message).trim().length === 0) {
      return res.status(400).json({ error: "A mensagem \xE9 obrigat\xF3ria." });
    }
    const cleanName = String(name || "Motorista").trim();
    const cleanContact = String(contact || "").trim();
    const cleanEmail = String(email || "").trim();
    const cleanPreferred = preferredReply === "email" ? "email" : "whatsapp";
    const cleanSubject = String(subject || "").trim() || "Contato pelo App";
    const cleanMessage = String(message).trim();
    const categoryLabels = {
      duvida: "D\xFAvida sobre o App",
      sugestao: "Sugest\xE3o de Melhoria",
      problema: "Relato de Problema",
      elogio: "Elogio",
      outro: "Outro Assunto"
    };
    const saved = await saveMessage({
      name: cleanName,
      contact: cleanContact,
      email: cleanEmail,
      preferredReply: cleanPreferred,
      category,
      categoryLabel: categoryLabels[category] || "Geral",
      subject: cleanSubject,
      message: cleanMessage,
      source: source === "app" ? "app" : "web"
    });
    res.json({
      ok: true,
      message: "Mensagem recebida com sucesso na Caixa Postal! O desenvolvedor analisar\xE1 sua mensagem.",
      ticketId: saved.id
    });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao enviar mensagem para a caixa postal." });
  }
});
app.get("/api/contact", async (req, res) => {
  if (!checkAdminAuth(req) && process.env.NODE_ENV === "production") {
    return res.status(401).json({ error: "N\xE3o autorizado" });
  }
  try {
    const rawMessages = await listAllMessages();
    const messages = rawMessages.map((m) => ({
      ...m,
      whatsapp: m.contact || "",
      email: m.email || "",
      preferredReply: m.preferredReply || "whatsapp"
    }));
    res.json({ ok: true, messages, total: messages.length });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao listar mensagens." });
  }
});
app.patch("/api/contact/:id", async (req, res) => {
  if (!checkAdminAuth(req) && process.env.NODE_ENV === "production") {
    return res.status(401).json({ error: "N\xE3o autorizado" });
  }
  try {
    const { id } = req.params;
    const { status } = req.body || {};
    const ok = await markMessageStatus(id, status);
    res.json({ ok });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao atualizar status da mensagem." });
  }
});
app.delete("/api/contact/:id", async (req, res) => {
  if (!checkAdminAuth(req) && process.env.NODE_ENV === "production") {
    return res.status(401).json({ error: "N\xE3o autorizado" });
  }
  try {
    const { id } = req.params;
    const ok = await deleteMessage(id);
    res.json({ ok });
  } catch (err) {
    res.status(500).json({ error: err?.message || "Erro ao excluir mensagem." });
  }
});
app.get("/api/app-version", (_req, res) => {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  res.status(200).json({
    versionName: "3.3.64",
    versionCode: 85,
    releaseDate: "2026-11-02",
    apkUrl: "https://goorno-app.vercel.app/GoOrNo-v3.3.64.apk",
    directDownload: "/GoOrNo.apk",
    fileSizeBytes: 55360988,
    fileSizeFormatted: "55.4 MB",
    changelog: [
      "\u{1F3F7}\uFE0F Novo Padr\xE3o de P\xEDlulas Flutuantes: Badges de alto contraste para Mercado, Corrida Curta, Embarque Distante, Risco e Rod\xEDzio.",
      "\u{1F697} Rod\xEDzio Municipal de S\xE3o Paulo: Alerta autom\xE1tico no destino/origem dentro do Centro Expandido com base na placa cadastrada.",
      "\u{1F4CD} Cadastro de Risco e Favorito Flex\xEDvel: Escolha r\xE1pida entre Rua ou Bairro e campo de texto livremente edit\xE1vel.",
      "\u{1F3AF} Refinamento de Leitura da Nota: Captura precisa da avalia\xE7\xE3o do passageiro sem exibir tracinhos.",
      "\u26A1 R$/Km e R$/Hora Impec\xE1veis: Separa\xE7\xE3o rigorosa entre deslocamento de busca e corrida com passageiro."
    ],
    mandatory: false
  });
});
app.get([
  "/GoOrNo.apk",
  "/GoOrNo-v3.3.64.apk",
  "/GoOrNo-v3.3.63.apk",
  "/GoOrNo-v3.3.53.apk",
  "/download/apk",
  "/download/GoOrNo.apk",
  "/download/GoOrNo-v3.3.64.apk",
  "/download/GoOrNo-v3.3.63.apk"
], (_req, res) => {
  res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
  res.setHeader("Pragma", "no-cache");
  res.setHeader("Expires", "0");
  const candidatePaths = [
    path4.resolve(process.cwd(), "GoOrNo-v3.3.64.apk"),
    path4.resolve(process.cwd(), "dist/public/GoOrNo-v3.3.64.apk"),
    path4.resolve(process.cwd(), "client/public/GoOrNo-v3.3.64.apk"),
    path4.resolve(process.cwd(), "GoOrNo.apk"),
    path4.resolve(process.cwd(), "dist/public/GoOrNo.apk"),
    path4.resolve(process.cwd(), "client/public/GoOrNo.apk"),
    path4.resolve(process.cwd(), "GoOrNo-v3.3.63.apk"),
    path4.resolve(process.cwd(), "GoOrNo-v3.3.53.apk")
  ];
  for (const apkPath of candidatePaths) {
    if (fs4.existsSync(apkPath)) {
      return res.download(apkPath, "GoOrNo-v3.3.64.apk");
    }
  }
  res.status(404).send("APK n\xE3o encontrado no servidor.");
});
var distPublic = path4.resolve(process.cwd(), "dist/public");
if (fs4.existsSync(distPublic)) {
  app.use(express.static(distPublic));
  app.get("*", (req, res, next) => {
    if (req.path.startsWith("/api")) return next();
    res.sendFile(path4.join(distPublic, "index.html"));
  });
}
var PORT = Number(process.env.PORT || 3008);
if (!process.env.VERCEL) {
  app.listen(PORT, () => {
    console.log(`[GoOrNo] Servidor ativo em http://localhost:${PORT}`);
  });
}
var index_default = app;
export {
  app,
  index_default as default
};

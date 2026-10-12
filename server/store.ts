import fs from "fs";
import path from "path";

const IS_VERCEL = Boolean(process.env.VERCEL || process.env.AWS_LAMBDA_FUNCTION_NAME);
const DATA_DIR = IS_VERCEL ? "/tmp" : path.resolve(process.cwd(), ".data");
const STORE_FILE = path.join(DATA_DIR, "goorno-store.json");

const FIREBASE_PROJECT_ID = process.env.FIREBASE_PROJECT_ID || "expedicao-brasil";
const FIREBASE_API_KEY = process.env.FIREBASE_API_KEY || "";
const COL_MAIN = "goorno_cloud_store";
const COL_VAULT = "goorno_vault_snapshots";

export interface VehicleConfig {
  vehicleType: "car_flex" | "car_gnv" | "car_electric" | "moto";
  vehicleModel: string;
  fuelPricePerUnit: number; // R$/L or R$/m³ or R$/kWh
  consumptionPerUnit: number; // km/L or km/m³ or km/kWh
  maintenanceCostPerKm: number; // pneus, óleo, depreciação R$/km
  rentalOrInstallmentDaily: number; // R$/dia opcional
  greenMinPerKm: number; // Ex: 2.00
  yellowMinPerKm: number; // Ex: 1.50
  greenMinPerHour: number; // Ex: 45.00
  yellowMinPerHour: number; // Ex: 32.00
  dailyNetGoal: number; // Meta líquida do dia (ex: 250)
  voiceAlertEnabled: boolean;
  overlayEnabled: boolean;
  autoOpenStreetViewOnAccept: boolean;
}

export interface RideRecord {
  id: string;
  driverEmail: string;
  platform: "Uber" | "99" | "inDrive";
  category: string; // UberX, Comfort, Black, 99Pop, 99Plus, Moto, etc.
  offeredValue: number; // Valor que tocou na tela
  receivedValue: number | null; // Valor real pago (para conferência/auditoria)
  pickupDistanceKm: number; // Distância até o passageiro
  tripDistanceKm: number; // Distância da viagem
  totalDistanceKm: number;
  pickupTimeMin: number; // Tempo até embarque
  tripTimeMin: number; // Tempo da viagem
  totalTimeMin: number;
  valuePerKm: number;
  valuePerHour: number;
  estimatedFuelCost: number;
  estimatedTotalCost: number;
  netProfit: number;
  verdict: "GO" | "WARN" | "NO"; // Semáforo: Verde (Aceitar), Amarelo (Avaliar), Vermelho (Recusar)
  originAddress: string;
  originNeighborhood: string;
  destinationAddress: string;
  destinationNeighborhood: string;
  passengerName: string;
  passengerRating: number;
  decision: "accepted" | "declined" | "evaluating" | "cancelled_after_review";
  paymentAuditNote?: string;
  isRiskArea?: boolean;
  createdAt: string;
}

export interface RiskZoneRecord {
  id: string;
  neighborhoodOrStreet: string;
  city: string;
  riskLevel: "high" | "medium";
  reason: string;
  addedBy: string;
  createdAt: string;
}

export interface DriverUser {
  id: string;
  name: string;
  email: string;
  phone: string;
  city: string;
  plan: "trial" | "pro_monthly" | "pro_annual" | "vip";
  status: "active" | "blocked";
  trialEndsAt: string;
  createdAt: string;
  lastActiveAt: string;
  vehicleConfig: VehicleConfig;
}

export interface GoOrNoDatabase {
  users: DriverUser[];
  rides: RideRecord[];
  riskZones: RiskZoneRecord[];
  lastUpdated: string;
  lastSnapshotSlot?: string;
}

const DEFAULT_VEHICLE_CONFIG: VehicleConfig = {
  vehicleType: "car_flex",
  vehicleModel: "Onix / HB20 1.0 Flex",
  fuelPricePerUnit: 5.89,
  consumptionPerUnit: 11.5,
  maintenanceCostPerKm: 0.24,
  rentalOrInstallmentDaily: 0,
  greenMinPerKm: 2.0,
  yellowMinPerKm: 1.55,
  greenMinPerHour: 42.0,
  yellowMinPerHour: 30.0,
  dailyNetGoal: 280.0,
  voiceAlertEnabled: true,
  overlayEnabled: true,
  autoOpenStreetViewOnAccept: false,
};

const SEED_RIDES: RideRecord[] = [
  {
    id: "ride-seed-1",
    driverEmail: "motorista@goorno.app",
    platform: "Uber",
    category: "Uber Comfort",
    offeredValue: 34.80,
    receivedValue: 34.80,
    pickupDistanceKm: 1.2,
    tripDistanceKm: 11.3,
    totalDistanceKm: 12.5,
    pickupTimeMin: 4,
    tripTimeMin: 21,
    totalTimeMin: 25,
    valuePerKm: 2.78,
    valuePerHour: 83.52,
    estimatedFuelCost: 6.40,
    estimatedTotalCost: 9.40,
    netProfit: 25.40,
    verdict: "GO",
    originAddress: "Av. Paulista, 1578 - Bela Vista, São Paulo - SP",
    originNeighborhood: "Bela Vista",
    destinationAddress: "Aeroporto de Congonhas (CGH) - Vila Congonhas, São Paulo - SP",
    destinationNeighborhood: "Vila Congonhas",
    passengerName: "Rodrigo M.",
    passengerRating: 4.94,
    decision: "accepted",
    isRiskArea: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 18).toISOString(),
  },
  {
    id: "ride-seed-2",
    driverEmail: "motorista@goorno.app",
    platform: "99",
    category: "99Pop",
    offeredValue: 26.50,
    receivedValue: 21.90,
    pickupDistanceKm: 1.8,
    tripDistanceKm: 10.2,
    totalDistanceKm: 12.0,
    pickupTimeMin: 5,
    tripTimeMin: 24,
    totalTimeMin: 29,
    valuePerKm: 2.21,
    valuePerHour: 54.83,
    estimatedFuelCost: 6.15,
    estimatedTotalCost: 9.03,
    netProfit: 17.47,
    verdict: "GO",
    originAddress: "Rua Augusta, 2210 - Cerqueira César, São Paulo - SP",
    originNeighborhood: "Cerqueira César",
    destinationAddress: "Shopping Eldorado - Av. Rebouças, 3970 - Pinheiros, São Paulo - SP",
    destinationNeighborhood: "Pinheiros",
    passengerName: "Camila S.",
    passengerRating: 4.88,
    decision: "accepted",
    paymentAuditNote: "Plataforma pagou R$ 4,60 a menos que a oferta da tela",
    isRiskArea: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 55).toISOString(),
  },
  {
    id: "ride-seed-3",
    driverEmail: "motorista@goorno.app",
    platform: "Uber",
    category: "UberX",
    offeredValue: 11.40,
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
    originAddress: "Rua da Consolação, 890 - Consolação, São Paulo - SP",
    originNeighborhood: "Consolação",
    destinationAddress: "Estrada do M'Boi Mirim, 4200 - Jardim Ângela, São Paulo - SP",
    destinationNeighborhood: "Jardim Ângela",
    passengerName: "Cliente Novo",
    passengerRating: 4.62,
    decision: "declined",
    isRiskArea: true,
    createdAt: new Date(Date.now() - 1000 * 60 * 95).toISOString(),
  },
  {
    id: "ride-seed-4",
    driverEmail: "motorista@goorno.app",
    platform: "inDrive",
    category: "Conforto",
    offeredValue: 22.00,
    receivedValue: 22.00,
    pickupDistanceKm: 1.4,
    tripDistanceKm: 10.6,
    totalDistanceKm: 12.0,
    pickupTimeMin: 4,
    tripTimeMin: 28,
    totalTimeMin: 32,
    valuePerKm: 1.83,
    valuePerHour: 41.25,
    estimatedFuelCost: 6.15,
    estimatedTotalCost: 9.03,
    netProfit: 12.97,
    verdict: "WARN",
    originAddress: "Av. Brigadeiro Faria Lima, 3477 - Itaim Bibi, São Paulo - SP",
    originNeighborhood: "Itaim Bibi",
    destinationAddress: "Rua Vergueiro, 1855 - Vila Mariana, São Paulo - SP",
    destinationNeighborhood: "Vila Mariana",
    passengerName: "Marcos T.",
    passengerRating: 4.91,
    decision: "accepted",
    isRiskArea: false,
    createdAt: new Date(Date.now() - 1000 * 60 * 140).toISOString(),
  },
];

const SEED_RISK_ZONES: RiskZoneRecord[] = [
  {
    id: "risk-1",
    neighborhoodOrStreet: "Jardim Ângela / Estrada do M'Boi Mirim",
    city: "São Paulo - SP",
    riskLevel: "high",
    reason: "Alto índice de assaltos noturnos e vielas sem saída",
    addedBy: "Comunidade GoOrNo",
    createdAt: new Date().toISOString(),
  },
  {
    id: "risk-2",
    neighborhoodOrStreet: "Capão Redondo (Áreas Internas)",
    city: "São Paulo - SP",
    riskLevel: "high",
    reason: "Conferir Street View antes de entrar à noite",
    addedBy: "Comunidade GoOrNo",
    createdAt: new Date().toISOString(),
  },
  {
    id: "risk-3",
    neighborhoodOrStreet: "Paraisópolis / Rua Ernest Renan",
    city: "São Paulo - SP",
    riskLevel: "medium",
    reason: "Ruas estreitas com bloqueio frequente de via",
    addedBy: "Comunidade GoOrNo",
    createdAt: new Date().toISOString(),
  },
];

function createDefaultDb(): GoOrNoDatabase {
  return {
    users: [
      {
        id: "usr-demo-1",
        name: "Murilo (Motorista Pro)",
        email: "motorista@goorno.app",
        phone: "(11) 99999-0000",
        city: "São Paulo - SP",
        plan: "vip",
        status: "active",
        trialEndsAt: new Date(Date.now() + 1000 * 60 * 60 * 24 * 365).toISOString(),
        createdAt: new Date().toISOString(),
        lastActiveAt: new Date().toISOString(),
        vehicleConfig: DEFAULT_VEHICLE_CONFIG,
      },
    ],
    rides: SEED_RIDES,
    riskZones: SEED_RISK_ZONES,
    lastUpdated: new Date().toISOString(),
  };
}

let memoryDb: GoOrNoDatabase | null = null;
let hydratedFromCloud = false;

function firestoreUrl(collection: string, docId?: string): string {
  const base = `https://firestore.googleapis.com/v1/projects/${FIREBASE_PROJECT_ID}/databases/(default)/documents/${collection}`;
  const pathUrl = docId ? `${base}/${encodeURIComponent(docId)}` : base;
  return FIREBASE_API_KEY ? `${pathUrl}?key=${encodeURIComponent(FIREBASE_API_KEY)}` : pathUrl;
}

async function fetchCloudDoc(collection: string, docId: string): Promise<any | null> {
  try {
    const res = await fetch(firestoreUrl(collection, docId));
    if (!res.ok) return null;
    const json: any = await res.json();
    const raw = json?.fields?.payload?.stringValue;
    if (!raw) return null;
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

async function saveCloudDoc(collection: string, docId: string, data: any): Promise<boolean> {
  try {
    const body = {
      fields: {
        payload: { stringValue: JSON.stringify(data) },
        updatedAt: { stringValue: new Date().toISOString() },
      },
    };
    const res = await fetch(firestoreUrl(collection, docId), {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return res.ok;
  } catch {
    return false;
  }
}

export async function listVaultSnapshots(): Promise<Array<{ slot: string; updatedAt: string; usersCount: number; ridesCount: number }>> {
  const results: Array<{ slot: string; updatedAt: string; usersCount: number; ridesCount: number }> = [];
  let pageToken = "";
  try {
    do {
      const base = firestoreUrl(COL_VAULT);
      const sep = base.includes("?") ? "&" : "?";
      const url = `${base}${sep}pageSize=50${pageToken ? `&pageToken=${encodeURIComponent(pageToken)}` : ""}`;
      const res = await fetch(url);
      if (!res.ok) break;
      const json: any = await res.json();
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
          } catch {}
        }
        results.push({ slot, updatedAt, usersCount, ridesCount });
      }
      pageToken = json.nextPageToken || "";
    } while (pageToken);
  } catch {}
  return results.sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
}

export async function loadDatabase(): Promise<GoOrNoDatabase> {
  if (memoryDb && hydratedFromCloud) {
    return memoryDb;
  }

  // 1. Try Cloud Firestore first ('goorno_cloud_store/main_state')
  const cloudData = await fetchCloudDoc(COL_MAIN, "main_state");
  if (cloudData && Array.isArray(cloudData.users) && Array.isArray(cloudData.rides)) {
    memoryDb = cloudData;
    hydratedFromCloud = true;
    saveLocalFile(memoryDb!);
    return memoryDb!;
  }

  // 2. Fallback to local file if available
  try {
    if (fs.existsSync(STORE_FILE)) {
      const raw = fs.readFileSync(STORE_FILE, "utf-8");
      const parsed = JSON.parse(raw);
      if (parsed && Array.isArray(parsed.users)) {
        memoryDb = parsed;
        hydratedFromCloud = true;
        await saveCloudDoc(COL_MAIN, "main_state", memoryDb);
        return memoryDb!;
      }
    }
  } catch {}

  // 3. Initialize default DB & persist to cloud
  memoryDb = createDefaultDb();
  hydratedFromCloud = true;
  saveLocalFile(memoryDb);
  await saveCloudDoc(COL_MAIN, "main_state", memoryDb);
  return memoryDb;
}

function saveLocalFile(db: GoOrNoDatabase) {
  try {
    if (!fs.existsSync(DATA_DIR)) {
      fs.mkdirSync(DATA_DIR, { recursive: true });
    }
    fs.writeFileSync(STORE_FILE, JSON.stringify(db, null, 2), "utf-8");
  } catch {}
}

export async function saveDatabase(db: GoOrNoDatabase): Promise<void> {
  db.lastUpdated = new Date().toISOString();
  memoryDb = db;
  saveLocalFile(db);

  // Mandatory await before serverless exit
  await saveCloudDoc(COL_MAIN, "main_state", db);

  // 7-Day Time-Machine Automatic Snapshot ('slot_day_0' .. 'slot_day_6')
  const dayOfWeek = new Date().getDay();
  const todayKey = `${new Date().toISOString().slice(0, 10)}_slot_${dayOfWeek}`;
  if (db.lastSnapshotSlot !== todayKey) {
    db.lastSnapshotSlot = todayKey;
    await saveCloudDoc(COL_VAULT, `slot_day_${dayOfWeek}`, db);
  }
}

export async function restoreFromVaultSlot(slotId: string): Promise<GoOrNoDatabase | null> {
  const snapshot = await fetchCloudDoc(COL_VAULT, slotId);
  if (!snapshot || !Array.isArray(snapshot.users)) return null;
  await saveDatabase(snapshot);
  return snapshot;
}

export function calculateRideMetrics(
  params: {
    offeredValue: number;
    pickupDistanceKm: number;
    tripDistanceKm: number;
    pickupTimeMin: number;
    tripTimeMin: number;
    originAddress: string;
    destinationAddress: string;
  },
  config: VehicleConfig,
  riskZones: RiskZoneRecord[] = []
) {
  const totalDistanceKm = Math.max(0.1, Number((params.pickupDistanceKm + params.tripDistanceKm).toFixed(2)));
  const totalTimeMin = Math.max(1, Math.round(params.pickupTimeMin + params.tripTimeMin));

  const valuePerKm = Number((params.offeredValue / totalDistanceKm).toFixed(2));
  const valuePerHour = Number(((params.offeredValue / totalTimeMin) * 60).toFixed(2));

  const fuelCostPerKm = config.consumptionPerUnit > 0 ? config.fuelPricePerUnit / config.consumptionPerUnit : 0.5;
  const estimatedFuelCost = Number((totalDistanceKm * fuelCostPerKm).toFixed(2));
  const estimatedTotalCost = Number((totalDistanceKm * (fuelCostPerKm + config.maintenanceCostPerKm)).toFixed(2));
  const netProfit = Number((params.offeredValue - estimatedTotalCost).toFixed(2));

  let verdict: "GO" | "WARN" | "NO" = "WARN";
  if (valuePerKm >= config.greenMinPerKm && valuePerHour >= config.greenMinPerHour) {
    verdict = "GO";
  } else if (valuePerKm < config.yellowMinPerKm || valuePerHour < config.yellowMinPerHour || netProfit <= 0) {
    verdict = "NO";
  }

  const combinedText = `${params.originAddress} ${params.destinationAddress}`.toLowerCase();
  const matchedRisk = riskZones.find((rz) =>
    rz.neighborhoodOrStreet
      .toLowerCase()
      .split("/")
      .some((part) => part.trim().length > 3 && combinedText.includes(part.trim()))
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
    riskReason: matchedRisk ? `${matchedRisk.neighborhoodOrStreet}: ${matchedRisk.reason}` : null,
  };
}

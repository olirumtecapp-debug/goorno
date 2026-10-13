import express from "express";
import path from "path";
import fs from "fs";
import {
  loadDatabase,
  saveDatabase,
  calculateRideMetrics,
  listVaultSnapshots,
  restoreFromVaultSlot,
  RideRecord,
  DriverUser,
} from "./store.js";
import {
  checkOrActivateLicense,
  getLicense,
  saveLicense,
  listAllLicenses,
  applyPayment,
  cleanPhone,
  formatPhoneBr,
  LicenseRecord,
} from "./licenseStore.js";
import { createPixCharge, verifyPaymentStatus } from "./asaas.js";
import { sendFraudAlertEmail } from "./fraudAlert.js";

export const app = express();
app.use(express.json({ limit: "5mb" }));

// Health check
app.get("/api/health", async (_req, res) => {
  const db = await loadDatabase();
  res.json({
    ok: true,
    app: "GoOrNo",
    ridesCount: db.rides.length,
    usersCount: db.users.length,
    lastUpdated: db.lastUpdated,
  });
});

// Full state for driver dashboard
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
      lastUpdated: db.lastUpdated,
    });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao carregar dados" });
  }
});

// Capture incoming ride offer (from Android Screen Reader or Simulator/Calculator)
app.post("/api/rides/capture", async (req, res) => {
  try {
    const db = await loadDatabase();
    const {
      driverEmail = "motorista@goorno.app",
      platform = "Uber",
      category = "UberX",
      offeredValue = 20,
      pickupDistanceKm = 1.5,
      tripDistanceKm = 8.0,
      pickupTimeMin = 5,
      tripTimeMin = 18,
      originAddress = "Av. Paulista, 1000 - Bela Vista, São Paulo - SP",
      originNeighborhood = "Bela Vista",
      destinationAddress = "Rua Funchal, 418 - Vila Olímpia, São Paulo - SP",
      destinationNeighborhood = "Vila Olímpia",
      passengerName = "Passageiro Verificado",
      passengerRating = 4.89,
      decision = "evaluating",
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
        destinationAddress: String(destinationAddress),
      },
      user.vehicleConfig,
      db.riskZones
    );

    const newRide: RideRecord = {
      id: `ride-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
      driverEmail: user.email,
      platform: platform as "Uber" | "99" | "inDrive",
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
      createdAt: new Date().toISOString(),
    };

    db.rides.unshift(newRide);
    if (db.rides.length > 500) {
      db.rides = db.rides.slice(0, 500);
    }

    await saveDatabase(db);
    res.json({ ok: true, ride: newRide, riskReason: metrics.riskReason });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao registrar corrida" });
  }
});

// Scan Screenshot Image (Vision AI) or OCR Text of Uber, 99 or inDrive Ride Card
app.post("/api/rides/scan-screenshot", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { imageBase64 = "", rawText = "", fileName = "", driverEmail = "motorista@goorno.app" } = req.body || {};
    let text = String(rawText || "");

    let aiExtracted: {
      platform?: "Uber" | "99" | "inDrive";
      category?: string;
      offeredValue?: number;
      pickupDistanceKm?: number;
      tripDistanceKm?: number;
      pickupTimeMin?: number;
      tripTimeMin?: number;
      originAddress?: string;
      destinationAddress?: string;
      passengerRating?: number;
    } | null = null;

    // 1. If an image screenshot (base64) was sent, read it with Vision AI (Llama 4 Scout Vision)
    const groqKey = process.env.GROQ_API_KEY || "";
    if (imageBase64 && groqKey) {
      try {
        const visionRes = await fetch("https://api.groq.com/openai/v1/chat/completions", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            Authorization: `Bearer ${groqKey}`,
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
                    text: `Você é o leitor óptico do app GoOrNo para motoristas Uber, 99 e inDrive. Extraia os dados deste print de chamada de corrida e retorne APENAS um JSON com as chaves:
{
  "platform": "Uber" | "99" | "inDrive",
  "category": "string (ex: UberX, Uber Comfort, 99Pop, inDrive)",
  "offeredValue": number (valor em reais da corrida, ex: 26.50),
  "pickupDistanceKm": number (distância até o passageiro em km, ex: 1.4),
  "tripDistanceKm": number (distância da viagem em km, ex: 9.8),
  "pickupTimeMin": number (tempo até o passageiro em minutos, ex: 4),
  "tripTimeMin": number (tempo da viagem em minutos, ex: 21),
  "originAddress": "endereço completo de embarque/origem",
  "destinationAddress": "endereço completo de desembarque/destino",
  "passengerRating": number (nota do passageiro, ex: 4.91)
}`,
                  },
                  {
                    type: "image_url",
                    image_url: {
                      url: imageBase64.startsWith("data:") ? imageBase64 : `data:image/jpeg;base64,${imageBase64}`,
                    },
                  },
                ],
              },
            ],
          }),
        });
        if (visionRes.ok) {
          const visionJson: any = await visionRes.json();
          const content = visionJson?.choices?.[0]?.message?.content;
          if (content) {
            aiExtracted = JSON.parse(content);
          }
        }
      } catch {}
    }

    // 2. Fallback / Merge with text regex parser
    const lower = (text + " " + String(fileName)).toLowerCase();
    let platform: "Uber" | "99" | "inDrive" =
      aiExtracted?.platform ||
      (lower.includes("99") || lower.includes("pop") ? "99" : lower.includes("indrive") ? "inDrive" : "Uber");
    let category =
      aiExtracted?.category ||
      (platform === "99" ? "99Pop" : platform === "inDrive" ? "inDrive Urbano" : "UberX");

    const priceMatches = [...text.matchAll(/R\$\s*([0-9]{1,3}(?:[.,][0-9]{2}))/gi)];
    let offeredValue = Number(aiExtracted?.offeredValue) || 0;
    if (!offeredValue && priceMatches.length > 0) {
      const parsedPrices = priceMatches
        .map((m) => parseFloat(m[1].replace(",", ".")))
        .filter((n) => !isNaN(n) && n >= 4.5 && n <= 600);
      if (parsedPrices.length > 0) offeredValue = Math.max(...parsedPrices);
    }
    if (!offeredValue || offeredValue <= 0) offeredValue = 26.80;

    const kmMatches = [...text.matchAll(/([0-9]{1,3}(?:[.,][0-9]{1,2})?)\s*km/gi)];
    const kms = kmMatches
      .map((m) => parseFloat(m[1].replace(",", ".")))
      .filter((n) => !isNaN(n) && n >= 0.1 && n <= 250);
    const pickupDistanceKm =
      Number(aiExtracted?.pickupDistanceKm) || (kms.length >= 2 ? kms[0] : kms.length === 1 ? 1.2 : 1.5);
    const tripDistanceKm =
      Number(aiExtracted?.tripDistanceKm) || (kms.length >= 2 ? kms[1] : kms.length === 1 ? kms[0] : 8.5);

    const minMatches = [...text.matchAll(/([0-9]{1,3})\s*(?:min|minutos)/gi)];
    const mins = minMatches.map((m) => parseInt(m[1], 10)).filter((n) => !isNaN(n) && n >= 1 && n <= 300);
    const pickupTimeMin = Number(aiExtracted?.pickupTimeMin) || (mins.length >= 2 ? mins[0] : 4);
    const tripTimeMin =
      Number(aiExtracted?.tripTimeMin) ||
      (mins.length >= 2 ? mins[1] : mins.length === 1 ? mins[0] : Math.max(8, Math.round(tripDistanceKm * 2.2)));

    const ratingMatch = text.match(/\b([45][.,][0-9]{2})\b/);
    const passengerRating =
      Number(aiExtracted?.passengerRating) || (ratingMatch ? parseFloat(ratingMatch[1].replace(",", ".")) : 4.89);

    const lines = text
      .split(/\r?\n|\|/)
      .map((l) => l.trim())
      .filter((l) => l.length > 6);
    const addressLines = lines.filter((l) =>
      /\b(rua|av\.|avenida|estrada|alameda|praça|rodovia|travessa|shopping|aeroporto|terminal|sp|rj|mg|pr|ba|rs|df|go|pe|ce)\b/i.test(
        l
      )
    );

    const originAddress =
      aiExtracted?.originAddress || addressLines[0] || "Av. Paulista, 1578 - Bela Vista, São Paulo - SP";
    const destinationAddress =
      aiExtracted?.destinationAddress ||
      addressLines[1] ||
      addressLines[0] ||
      "Rua Funchal, 418 - Vila Olímpia, São Paulo - SP";

    const user = db.users.find((u) => u.email.toLowerCase() === String(driverEmail).toLowerCase()) || db.users[0];
    const metrics = calculateRideMetrics(
      {
        offeredValue,
        pickupDistanceKm,
        tripDistanceKm,
        pickupTimeMin,
        tripTimeMin,
        originAddress,
        destinationAddress,
      },
      user.vehicleConfig,
      db.riskZones
    );

    const newRide: RideRecord = {
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
      createdAt: new Date().toISOString(),
    };

    db.rides.unshift(newRide);
    await saveDatabase(db);
    res.json({ ok: true, ride: newRide, riskReason: metrics.riskReason });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao ler print da corrida" });
  }
});

// Update decision on a ride (Aceitar / Recusar / Cancelar após ver Street View)
app.post("/api/rides/:id/decision", async (req, res) => {
  try {
    const db = await loadDatabase();
    const ride = db.rides.find((r) => r.id === req.params.id);
    if (!ride) return res.status(404).json({ error: "Corrida não encontrada" });

    const { decision } = req.body || {};
    if (decision) {
      ride.decision = decision;
      if (decision === "accepted" && ride.receivedValue === null) {
        ride.receivedValue = ride.offeredValue;
      }
    }
    await saveDatabase(db);
    res.json({ ok: true, ride });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao atualizar decisão" });
  }
});

// Audit payment: compare offeredValue vs actual receivedValue
app.post("/api/rides/:id/audit-payment", async (req, res) => {
  try {
    const db = await loadDatabase();
    const ride = db.rides.find((r) => r.id === req.params.id);
    if (!ride) return res.status(404).json({ error: "Corrida não encontrada" });

    const { receivedValue, paymentAuditNote } = req.body || {};
    ride.receivedValue = receivedValue !== undefined && receivedValue !== null ? Number(receivedValue) : null;
    if (paymentAuditNote !== undefined) {
      ride.paymentAuditNote = String(paymentAuditNote);
    } else if (ride.receivedValue !== null && ride.receivedValue < ride.offeredValue) {
      const diff = (ride.offeredValue - ride.receivedValue).toFixed(2);
      ride.paymentAuditNote = `Diferença de -R$ ${diff} em relação ao valor ofertado na tela`;
    } else {
      ride.paymentAuditNote = "Valor conferido e pago corretamente";
    }

    await saveDatabase(db);
    res.json({ ok: true, ride });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao auditar pagamento" });
  }
});

// Delete ride from history
app.delete("/api/rides/:id", async (req, res) => {
  try {
    const db = await loadDatabase();
    db.rides = db.rides.filter((r) => r.id !== req.params.id);
    await saveDatabase(db);
    res.json({ ok: true });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao remover corrida" });
  }
});

// Update Vehicle & Traffic Light (Semáforo) settings
app.post("/api/config/vehicle", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { email = "motorista@goorno.app", vehicleConfig } = req.body || {};
    const user = db.users.find((u) => u.email.toLowerCase() === String(email).toLowerCase()) || db.users[0];
    if (vehicleConfig) {
      user.vehicleConfig = {
        ...user.vehicleConfig,
        ...vehicleConfig,
      };
    }
    await saveDatabase(db);
    res.json({ ok: true, user });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao salvar configurações" });
  }
});

// Manage Risk Zones
app.post("/api/risk-zones", async (req, res) => {
  try {
    const db = await loadDatabase();
    const { neighborhoodOrStreet, city = "São Paulo - SP", riskLevel = "high", reason = "Área sinalizada pelo motorista" } = req.body || {};
    if (!neighborhoodOrStreet) return res.status(400).json({ error: "Informe o bairro ou rua" });

    const item = {
      id: `risk-${Date.now()}`,
      neighborhoodOrStreet: String(neighborhoodOrStreet),
      city: String(city),
      riskLevel: (riskLevel === "medium" ? "medium" : "high") as "high" | "medium",
      reason: String(reason),
      addedBy: "Motorista",
      createdAt: new Date().toISOString(),
    };
    db.riskZones.unshift(item);
    await saveDatabase(db);
    res.json({ ok: true, riskZone: item });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao cadastrar área de risco" });
  }
});

app.delete("/api/risk-zones/:id", async (req, res) => {
  try {
    const db = await loadDatabase();
    db.riskZones = db.riskZones.filter((rz) => rz.id !== req.params.id);
    await saveDatabase(db);
    res.json({ ok: true });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao excluir área de risco" });
  }
});

// Admin Overview & Vault Snapshots
app.get("/api/admin/overview", async (_req, res) => {
  try {
    const db = await loadDatabase();
    const snapshots = await listVaultSnapshots();
    res.json({
      users: db.users,
      totalRides: db.rides.length,
      riskZonesCount: db.riskZones.length,
      snapshots,
      lastUpdated: db.lastUpdated,
    });
  } catch (err: any) {
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
        if (name !== undefined) existing.name = name;
        if (email !== undefined) existing.email = email;
        if (phone !== undefined) existing.phone = phone;
        if (city !== undefined) existing.city = city;
        if (plan !== undefined) existing.plan = plan;
        if (status !== undefined) existing.status = status;
      }
    } else if (email) {
      const newUser: DriverUser = {
        id: `usr-${Date.now()}`,
        name: String(name || "Novo Motorista"),
        email: String(email),
        phone: String(phone || ""),
        city: String(city || "Brasil"),
        plan: plan || "pro_monthly",
        status: status || "active",
        trialEndsAt: new Date(Date.now() + 1000 * 60 * 60 * 24 * 7).toISOString(),
        createdAt: new Date().toISOString(),
        lastActiveAt: new Date().toISOString(),
        vehicleConfig: db.users[0].vehicleConfig,
      };
      db.users.unshift(newUser);
    }
    await saveDatabase(db);
    res.json({ ok: true, users: db.users });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao gerenciar motorista" });
  }
});

app.post("/api/admin/restore-vault", async (req, res) => {
  try {
    const { slot } = req.body || {};
    if (!slot) return res.status(400).json({ error: "Slot obrigatório" });
    const restored = await restoreFromVaultSlot(String(slot));
    if (!restored) return res.status(404).json({ error: "Snapshot não encontrado" });
    res.json({ ok: true, restored });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao restaurar snapshot" });
  }
});

// =========================================================================
// GOORNO MONETIZAÇÃO, LICENÇAS E ANTI-FRAUDE (ASAAS + PIX + PAINEL ADMIN)
// =========================================================================

function checkAdminAuth(req: express.Request): boolean {
  const masterPass = process.env.ADMIN_PASSWORD || "goorno2026admin";
  const passed =
    req.headers["x-admin-password"] ||
    req.query.adminPassword ||
    req.body?.adminPassword;
  return passed === masterPass;
}

// 1. Check or Activate Driver License (App Startup & Background Sync)
app.post("/api/license/check-or-activate", async (req, res) => {
  try {
    const { whatsapp, deviceId, deviceModel, action = "check" } = req.body || {};
    const clientIp = String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "");

    if (!whatsapp || !deviceId) {
      return res.status(400).json({
        allowed: false,
        message: "WhatsApp e ID do Aparelho são obrigatórios.",
      });
    }

    const result = await checkOrActivateLicense({
      whatsapp: String(whatsapp),
      deviceId: String(deviceId),
      deviceModel: deviceModel ? String(deviceModel) : undefined,
      action: action as "check" | "transfer",
      clientIp,
    });

    // Se houve alerta de fraude por trocas excessivas, dispara email imediatamente
    if (result.fraudAlert && result.license) {
      sendFraudAlertEmail({
        whatsapp: result.license.whatsapp,
        fromDevice: result.license.transfers?.slice(-2)?.[0]?.fromDevice || result.license.deviceId,
        toDevice: String(deviceId),
        transfersCount: result.license.deviceTransfersCount || 0,
        reason: result.license.fraudReason || "Trocas excessivas de aparelho",
        clientIp,
        timestamp: new Date().toISOString(),
      }).catch((e) => console.warn("[fraudAlert] Erro em background:", e));
    }

    res.json(result);
  } catch (err: any) {
    console.error("[license] Erro em check-or-activate:", err);
    res.status(500).json({ allowed: false, message: err?.message || "Erro interno ao validar licença." });
  }
});

// 2. Create Asaas PIX Charge for Subscription
app.post("/api/license/create-pix", async (req, res) => {
  try {
    const { whatsapp, plan = "mensal" } = req.body || {};
    if (!whatsapp) {
      return res.status(400).json({ error: "Informe o WhatsApp do motorista." });
    }

    const pixData = await createPixCharge({
      whatsapp: String(whatsapp),
      plan: plan === "vitalicio" ? "vitalicio" : "mensal",
    });

    res.json({ ok: true, pix: pixData });
  } catch (err: any) {
    console.error("[license] Erro ao gerar PIX Asaas:", err);
    res.status(500).json({ error: err?.message || "Erro ao conectar com gateway PIX Asaas." });
  }
});

// 3. Manual / Instant Payment Status Check
app.get("/api/license/check-payment/:paymentId", async (req, res) => {
  try {
    const { paymentId } = req.params;
    const { phone } = req.query;
    const status = await verifyPaymentStatus(paymentId);

    if (status.confirmed) {
      const targetPhone = phone
        ? String(phone)
        : status.externalReference?.replace("goorno_tel_", "") || "";

      if (targetPhone) {
        const updated = await applyPayment({
          phone: targetPhone,
          amount: status.value,
          paymentId: paymentId,
        });
        return res.json({ confirmed: true, updatedLicense: updated });
      }
    }

    res.json({ confirmed: status.confirmed, status: status.status });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao verificar pagamento." });
  }
});

// 4. Asaas Webhook (Automated Payment Notifications)
app.post("/api/goorno-webhook", async (req, res) => {
  try {
    const body = req.body || {};
    const event = String(body.event || "");
    const payment = body.payment || {};

    console.log(`[Asaas Webhook] Evento recebido: ${event} para cobrança ${payment.id}`);

    const APPROVED_EVENTS = ["PAYMENT_RECEIVED", "PAYMENT_CONFIRMED", "PAYMENT_RECEIVED_IN_CASH"];
    if (APPROVED_EVENTS.includes(event)) {
      let phone = "";
      if (payment.externalReference && payment.externalReference.startsWith("goorno_tel_")) {
        phone = payment.externalReference.replace("goorno_tel_", "");
      } else if (payment.customer) {
        // Fallback: try customer mobilePhone
        try {
          const custRes = await fetch(`https://api.asaas.com/v3/customers/${payment.customer}`, {
            headers: { access_token: process.env.ASAAS_API_KEY || "" },
          });
          if (custRes.ok) {
            const custJson: any = await custRes.json();
            phone = custJson.mobilePhone || custJson.phone || "";
          }
        } catch {}
      }

      if (phone) {
        const updated = await applyPayment({
          phone: phone,
          amount: Number(payment.value || 0),
          paymentId: String(payment.id || "webhook"),
        });
        console.log(`[Asaas Webhook] Licença ativada/renovada com sucesso para ${updated.whatsapp}`);
      } else {
        console.warn(`[Asaas Webhook] Pagamento aprovado mas nenhum telefone vinculado encontrado: ${payment.id}`);
      }
    }

    res.status(200).json({ received: true });
  } catch (err: any) {
    console.error("[Asaas Webhook] Erro:", err);
    res.status(200).json({ received: true, error: err?.message });
  }
});

// 5. Admin Dashboard: Real-time Stats & Users / Licenses
app.get("/api/admin/stats-and-licenses", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso não autorizado ao painel admin." });
  }

  try {
    const licenses = await listAllLicenses();
    const nowMs = Date.now();

    let totalUsers = licenses.length;
    let activePaid = 0;
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
        activePaid++;
      } else if (lic.plano === "mensal") {
        const validMs = new Date(lic.validoAte || 0).getTime();
        if (validMs >= nowMs) {
          activePaid++;
          estimatedMonthlyRevenue += 9.90;
        } else {
          expired++;
        }
      } else if (lic.plano === "trial") {
        const trialMs = new Date(lic.trialEndsAt || 0).getTime();
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
        inTrial,
        expired,
        blocked,
        fraudAlerts,
        estimatedMonthlyRevenue: Number(estimatedMonthlyRevenue.toFixed(2)),
      },
      licenses,
    });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao carregar estatísticas do admin." });
  }
});

// 6. Admin Actions: 1-Click Manage Driver License
app.post("/api/admin/manage-license", async (req, res) => {
  if (!checkAdminAuth(req)) {
    return res.status(401).json({ error: "Acesso não autorizado." });
  }

  try {
    const { whatsapp, action, days, note } = req.body || {};
    const phone = cleanPhone(String(whatsapp || ""));
    let lic = await getLicense(phone);

    if (!lic) {
      return res.status(404).json({ error: "Licença não encontrada para este WhatsApp." });
    }

    const now = new Date();

    if (action === "add_days") {
      const addedDays = Number(days || 30);
      const baseMs = Math.max(now.getTime(), new Date(lic.validoAte || 0).getTime());
      const newValid = new Date(baseMs + addedDays * 24 * 60 * 60 * 1000);
      lic.validoAte = newValid.toISOString();
      lic.status = "active";
      if (lic.plano === "trial") lic.plano = "mensal";
      lic.notes = `${lic.notes || ""}\n+${addedDays} dias adicionados manualmente via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "set_lifetime" || action === "set_courtesy") {
      lic.status = "active";
      lic.plano = action === "set_lifetime" ? "vitalicio" : "cortesia";
      lic.validoAte = new Date("2099-12-31T23:59:59.000Z").toISOString();
      lic.notes = `${lic.notes || ""}\nPlano ${lic.plano.toUpperCase()} concedido via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "block") {
      lic.status = "blocked";
      lic.notes = `${lic.notes || ""}\nBloqueado via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "unblock") {
      lic.status = "active";
      lic.fraudSuspicion = false;
      lic.fraudReason = undefined;
      lic.notes = `${lic.notes || ""}\nDesbloqueado via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    } else if (action === "reset_device") {
      lic.deviceId = "";
      lic.deviceTransfersCount = 0;
      lic.transfers = [];
      lic.fraudSuspicion = false;
      lic.fraudReason = undefined;
      lic.notes = `${lic.notes || ""}\nAparelho desvinculado e contador zerado via Admin em ${now.toLocaleString("pt-BR")}`.trim();
    }

    if (note) {
      lic.notes = `${lic.notes || ""}\nNota: ${note}`.trim();
    }

    await saveLicense(lic);
    res.json({ ok: true, license: lic });
  } catch (err: any) {
    res.status(500).json({ error: err?.message || "Erro ao atualizar licença." });
  }
});

// Serve static build in production/local start
const distPublic = path.resolve(process.cwd(), "dist/public");
if (fs.existsSync(distPublic)) {
  app.use(express.static(distPublic));
  app.get("*", (req, res, next) => {
    if (req.path.startsWith("/api")) return next();
    res.sendFile(path.join(distPublic, "index.html"));
  });
}

const PORT = Number(process.env.PORT || 3008);
if (!process.env.VERCEL) {
  app.listen(PORT, () => {
    console.log(`[GoOrNo] Servidor ativo em http://localhost:${PORT}`);
  });
}

export default app;

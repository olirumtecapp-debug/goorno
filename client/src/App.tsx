import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  AlertTriangle,
  ArrowRight,
  Check,
  CheckCircle2,
  Car,
  ClipboardCheck,
  Clock,
  Copy,
  DollarSign,
  Eye,
  Filter,
  Fuel,
  Gauge,
  History,
  Lock,
  MapPin,
  Navigation,
  Play,
  Plus,
  RefreshCw,
  ShieldAlert,
  ShieldCheck,
  Smartphone,
  Sparkles,
  Trash2,
  TrendingUp,
  Volume2,
  VolumeX,
  Wrench,
  X,
  XCircle,
} from "lucide-react";

interface VehicleConfig {
  vehicleType: "car_flex" | "car_gnv" | "car_electric" | "moto";
  vehicleModel: string;
  fuelPricePerUnit: number;
  consumptionPerUnit: number;
  maintenanceCostPerKm: number;
  rentalOrInstallmentDaily: number;
  greenMinPerKm: number;
  yellowMinPerKm: number;
  greenMinPerHour: number;
  yellowMinPerHour: number;
  dailyNetGoal: number;
  voiceAlertEnabled: boolean;
  overlayEnabled: boolean;
  autoOpenStreetViewOnAccept: boolean;
}

interface RideRecord {
  id: string;
  driverEmail: string;
  platform: "Uber" | "99" | "inDrive";
  category: string;
  offeredValue: number;
  receivedValue: number | null;
  pickupDistanceKm: number;
  tripDistanceKm: number;
  totalDistanceKm: number;
  pickupTimeMin: number;
  tripTimeMin: number;
  totalTimeMin: number;
  valuePerKm: number;
  valuePerHour: number;
  estimatedFuelCost: number;
  estimatedTotalCost: number;
  netProfit: number;
  verdict: "GO" | "WARN" | "NO";
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

interface RiskZoneRecord {
  id: string;
  neighborhoodOrStreet: string;
  city: string;
  riskLevel: "high" | "medium";
  reason: string;
  addedBy: string;
  createdAt: string;
}

interface DriverUser {
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

type ActiveTab = "copilot" | "evaluate" | "history" | "settings" | "apk" | "admin";

const PRESET_SIMULATIONS = [
  {
    label: "🟢 Uber Comfort (Corrida Top)",
    platform: "Uber" as const,
    category: "Uber Comfort",
    offeredValue: 38.90,
    pickupDistanceKm: 0.9,
    tripDistanceKm: 11.6,
    pickupTimeMin: 3,
    tripTimeMin: 21,
    originAddress: "Shopping Ibirapuera - Av. Ibirapuera, 3103 - Moema, São Paulo - SP",
    originNeighborhood: "Moema",
    destinationAddress: "Av. Brig. Faria Lima, 2232 - Jardim Paulistano, São Paulo - SP",
    destinationNeighborhood: "Jardim Paulistano",
    passengerName: "Fernanda L.",
    passengerRating: 4.97,
  },
  {
    label: "🟡 99Pop (Corrida Média — Avaliar)",
    platform: "99" as const,
    category: "99Pop",
    offeredValue: 19.80,
    pickupDistanceKm: 1.6,
    tripDistanceKm: 9.2,
    pickupTimeMin: 5,
    tripTimeMin: 23,
    originAddress: "Rua Vergueiro, 1200 - Liberdade, São Paulo - SP",
    originNeighborhood: "Liberdade",
    destinationAddress: "Av. Celso Garcia, 1900 - Belenzinho, São Paulo - SP",
    destinationNeighborhood: "Belenzinho",
    passengerName: "Carlos E.",
    passengerRating: 4.82,
  },
  {
    label: "🔴 UberX (Prejuízo + Área de Risco)",
    platform: "Uber" as const,
    category: "UberX",
    offeredValue: 12.50,
    pickupDistanceKm: 3.4,
    tripDistanceKm: 7.5,
    pickupTimeMin: 10,
    tripTimeMin: 22,
    originAddress: "Av. Santo Amaro, 4500 - Brooklin, São Paulo - SP",
    originNeighborhood: "Brooklin",
    destinationAddress: "Estrada do M'Boi Mirim, 4150 - Jardim Ângela, São Paulo - SP",
    destinationNeighborhood: "Jardim Ângela",
    passengerName: "Passageiro Novo",
    passengerRating: 4.58,
  },
  {
    label: "⚡ inDrive (Oferta Negociada)",
    platform: "inDrive" as const,
    category: "inDrive Urbano",
    offeredValue: 29.00,
    pickupDistanceKm: 1.1,
    tripDistanceKm: 10.4,
    pickupTimeMin: 4,
    tripTimeMin: 19,
    originAddress: "Rua Turiassu, 2100 - Perdizes, São Paulo - SP",
    originNeighborhood: "Perdizes",
    destinationAddress: "Av. Paulista, 900 - Bela Vista, São Paulo - SP",
    destinationNeighborhood: "Bela Vista",
    passengerName: "Juliana R.",
    passengerRating: 4.93,
  },
];

function formatBRL(val: number): string {
  return val.toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
}

function buildStreetViewExternalUrl(address: string): string {
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
}

function buildEmbedMapUrl(address: string): string {
  return `https://maps.google.com/maps?q=${encodeURIComponent(address)}&t=k&z=18&ie=UTF8&iwloc=&output=embed`;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<ActiveTab>("copilot");
  const [user, setUser] = useState<DriverUser | null>(null);
  const [rides, setRides] = useState<RideRecord[]>([]);
  const [riskZones, setRiskZones] = useState<RiskZoneRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  // Floating Ride Popup (Overlay Semáforo)
  const [incomingRide, setIncomingRide] = useState<RideRecord | null>(null);
  const [overlayCountdown, setOverlayCountdown] = useState<number>(12);

  // Selected ride for "Avaliar Corrida Aceita & Street View"
  const [selectedRideId, setSelectedRideId] = useState<string | null>(null);
  const [streetViewTarget, setStreetViewTarget] = useState<{ label: string; address: string } | null>(null);

  // Manual Calculator form on Copilot tab
  const [calcPlatform, setCalcPlatform] = useState<"Uber" | "99" | "inDrive">("Uber");
  const [calcCategory, setCalcCategory] = useState("UberX");
  const [calcOffered, setCalcOffered] = useState("24.50");
  const [calcPickupKm, setCalcPickupKm] = useState("1.3");
  const [calcTripKm, setCalcTripKm] = useState("8.7");
  const [calcPickupMin, setCalcPickupMin] = useState("4");
  const [calcTripMin, setCalcTripMin] = useState("18");
  const [calcOrigin, setCalcOrigin] = useState("Av. Paulista, 1000 - Bela Vista, São Paulo - SP");
  const [calcDest, setCalcDest] = useState("Rua Funchal, 418 - Vila Olímpia, São Paulo - SP");

  // History filters & payment audit
  const [historyPlatformFilter, setHistoryPlatformFilter] = useState<string>("ALL");
  const [historyDecisionFilter, setHistoryDecisionFilter] = useState<string>("ALL");
  const [auditingRideId, setAuditingRideId] = useState<string | null>(null);
  const [auditReceivedInput, setAuditReceivedInput] = useState<string>("");
  const [auditNoteInput, setAuditNoteInput] = useState<string>("");

  // New Risk Zone input
  const [newRiskStreet, setNewRiskStreet] = useState("");
  const [newRiskReason, setNewRiskReason] = useState("");

  // Secret Admin Unlock (Alt + Shift + M or 5x tap on GoOrNo logo)
  const [adminUnlocked, setAdminUnlocked] = useState(false);
  const [adminData, setAdminData] = useState<any>(null);
  const [scanningScreenshot, setScanningScreenshot] = useState(false);
  const screenshotInputRef = useRef<HTMLInputElement | null>(null);
  const logoTapCount = useRef(0);
  const logoTapTimer = useRef<any>(null);

  const showToast = (msg: string) => {
    setToastMsg(msg);
    setTimeout(() => setToastMsg(null), 3500);
  };

  const handleScreenshotFile = async (file: File) => {
    setScanningScreenshot(true);
    showToast("📸 Lendo dados do print da corrida com IA...");
    try {
      const reader = new FileReader();
      const base64: string = await new Promise((resolve, reject) => {
        reader.onload = () => resolve(String(reader.result || ""));
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });

      const res = await fetch("/api/rides/scan-screenshot", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          imageBase64: base64,
          fileName: file.name,
        }),
      });
      const data = await res.json();
      if (data.ride) {
        setRides((prev) => [data.ride, ...prev]);
        setSelectedRideId(data.ride.id);
        setIncomingRide(data.ride);
        speakRideVerdict(data.ride);
        showToast("✅ Print lido! Semáforo calculado.");
      } else {
        showToast("Não foi possível ler o print");
      }
    } catch {
      showToast("Erro ao processar print da corrida");
    } finally {
      setScanningScreenshot(false);
    }
  };

  const speakRideVerdict = (ride: RideRecord, enabled?: boolean) => {
    const shouldSpeak = enabled ?? user?.vehicleConfig?.voiceAlertEnabled ?? true;
    if (!shouldSpeak || typeof window === "undefined" || !("speechSynthesis" in window)) return;
    try {
      window.speechSynthesis.cancel();
      const verdictWord =
        ride.verdict === "GO"
          ? "Gôu! Corrida Boa, Aceitar!"
          : ride.verdict === "WARN"
          ? "Atenção! Corrida Média, Avaliar rota!"
          : "Nôu! Corrida Ruim, Recusar!";
      const riskText = ride.isRiskArea ? " Cuidado: Endereço em área de risco sinalizada!" : "";
      const text = `${verdictWord} ${ride.platform}, ${ride.offeredValue.toFixed(2).replace(".", " e ")} reais. ${ride.valuePerKm
        .toFixed(2)
        .replace(".", " e ")} por quilômetro. Lucro livre de ${ride.netProfit.toFixed(0)} reais.${riskText}`;
      const utter = new SpeechSynthesisUtterance(text);
      utter.lang = "pt-BR";
      utter.rate = 1.12;
      window.speechSynthesis.speak(utter);
    } catch {}
  };

  const fetchState = async () => {
    try {
      const res = await fetch("/api/state");
      const data = await res.json();
      if (data.user) setUser(data.user);
      if (Array.isArray(data.rides)) {
        setRides(data.rides);
        if (!selectedRideId && data.rides.length > 0) {
          setSelectedRideId(data.rides[0].id);
        }
      }
      if (Array.isArray(data.riskZones)) setRiskZones(data.riskZones);
    } catch {
      showToast("Modo offline local ativo");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchState();
  }, []);

  // Keyboard shortcut Alt + Shift + M for Admin
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.altKey && e.shiftKey && (e.key === "M" || e.key === "m")) {
        e.preventDefault();
        setAdminUnlocked(true);
        setActiveTab("admin");
        loadAdminOverview();
        showToast("👑 Painel Admin Master Desbloqueado!");
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

  // Countdown timer for floating incoming ride overlay
  useEffect(() => {
    if (!incomingRide) return;
    setOverlayCountdown(12);
    const interval = setInterval(() => {
      setOverlayCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);
    return () => clearInterval(interval);
  }, [incomingRide]);

  // Listen to native Android Accessibility Service bridge events if running inside APK
  useEffect(() => {
    (window as any).onGoOrNoNativeRide = async (payload: any) => {
      await triggerRideCapture(payload);
    };
    return () => {
      delete (window as any).onGoOrNoNativeRide;
    };
  });

  const handleLogoTap = () => {
    logoTapCount.current += 1;
    if (logoTapTimer.current) clearTimeout(logoTapTimer.current);
    if (logoTapCount.current >= 5) {
      logoTapCount.current = 0;
      setAdminUnlocked(true);
      setActiveTab("admin");
      loadAdminOverview();
      showToast("👑 Painel Admin Master Desbloqueado!");
      return;
    }
    logoTapTimer.current = setTimeout(() => {
      logoTapCount.current = 0;
    }, 1800);
  };

  const loadAdminOverview = async () => {
    try {
      const res = await fetch("/api/admin/overview");
      const data = await res.json();
      setAdminData(data);
    } catch {}
  };

  const triggerRideCapture = async (payload: {
    platform: "Uber" | "99" | "inDrive";
    category: string;
    offeredValue: number;
    pickupDistanceKm: number;
    tripDistanceKm: number;
    pickupTimeMin: number;
    tripTimeMin: number;
    originAddress: string;
    originNeighborhood?: string;
    destinationAddress: string;
    destinationNeighborhood?: string;
    passengerName?: string;
    passengerRating?: number;
  }) => {
    try {
      const res = await fetch("/api/rides/capture", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = await res.json();
      if (data.ride) {
        setRides((prev) => [data.ride, ...prev]);
        setSelectedRideId(data.ride.id);
        setIncomingRide(data.ride);
        speakRideVerdict(data.ride);
      }
    } catch {
      showToast("Erro ao processar chamada");
    }
  };

  const handleDecision = async (ride: RideRecord, decision: RideRecord["decision"], openEvaluateAfter = false) => {
    try {
      const res = await fetch(`/api/rides/${ride.id}/decision`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ decision }),
      });
      const data = await res.json();
      if (data.ride) {
        setRides((prev) => prev.map((r) => (r.id === ride.id ? data.ride : r)));
      }
      setIncomingRide(null);
      setSelectedRideId(ride.id);
      if (openEvaluateAfter) {
        setActiveTab("evaluate");
        setStreetViewTarget({
          label: "Destino da Corrida",
          address: ride.destinationAddress,
        });
        showToast("Corrida aceita! Confira a rua no Street View antes de ir.");
      } else if (decision === "declined") {
        showToast("Corrida recusada (NO) e salva no histórico.");
      } else if (decision === "cancelled_after_review") {
        showToast("Corrida marcada como Cancelada após avaliar Street View!");
      } else {
        showToast("Status da corrida atualizado!");
      }
    } catch {}
  };

  const handleSavePaymentAudit = async (rideId: string) => {
    try {
      const val = parseFloat(auditReceivedInput.replace(",", "."));
      if (isNaN(val)) {
        showToast("Digite um valor numérico válido");
        return;
      }
      const res = await fetch(`/api/rides/${rideId}/audit-payment`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          receivedValue: val,
          paymentAuditNote: auditNoteInput || undefined,
        }),
      });
      const data = await res.json();
      if (data.ride) {
        setRides((prev) => prev.map((r) => (r.id === rideId ? data.ride : r)));
        setAuditingRideId(null);
        showToast("✅ Auditoria de valor salva no histórico!");
      }
    } catch {
      showToast("Erro ao salvar conferência");
    }
  };

  const handleSaveVehicleConfig = async (updatedPartial: Partial<VehicleConfig>) => {
    if (!user) return;
    const nextConfig = { ...user.vehicleConfig, ...updatedPartial };
    setUser({ ...user, vehicleConfig: nextConfig });
    try {
      await fetch("/api/config/vehicle", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ email: user.email, vehicleConfig: nextConfig }),
      });
      showToast("✅ Configurações e metas do Semáforo salvas!");
    } catch {}
  };

  const handleAddRiskZone = async (addressOrNeighborhood: string, reasonText?: string) => {
    if (!addressOrNeighborhood.trim()) return;
    try {
      const res = await fetch("/api/risk-zones", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          neighborhoodOrStreet: addressOrNeighborhood.trim(),
          reason: reasonText || "Marcado pelo motorista durante análise de corrida",
        }),
      });
      const data = await res.json();
      if (data.riskZone) {
        setRiskZones((prev) => [data.riskZone, ...prev]);
        setNewRiskStreet("");
        setNewRiskReason("");
        showToast("⚠️ Endereço adicionado à lista de Áreas de Risco!");
      }
    } catch {}
  };

  const handleDeleteRiskZone = async (id: string) => {
    try {
      await fetch(`/api/risk-zones/${id}`, { method: "DELETE" });
      setRiskZones((prev) => prev.filter((rz) => rz.id !== id));
      showToast("Área de risco removida");
    } catch {}
  };

  // Selected ride for Evaluate Tab
  const activeRideToEvaluate = useMemo(() => {
    return rides.find((r) => r.id === selectedRideId) || rides[0] || null;
  }, [rides, selectedRideId]);

  // Shift / Day Summary Metrics
  const shiftMetrics = useMemo(() => {
    const acceptedRides = rides.filter((r) => r.decision === "accepted");
    const totalGross = acceptedRides.reduce((acc, r) => acc + (r.receivedValue ?? r.offeredValue), 0);
    const totalOffered = acceptedRides.reduce((acc, r) => acc + r.offeredValue, 0);
    const totalNet = acceptedRides.reduce((acc, r) => acc + r.netProfit, 0);
    const totalKm = acceptedRides.reduce((acc, r) => acc + r.totalDistanceKm, 0);
    const totalDiscrepancy = acceptedRides.reduce((acc, r) => {
      if (r.receivedValue !== null && r.receivedValue < r.offeredValue) {
        return acc + (r.offeredValue - r.receivedValue);
      }
      return acc;
    }, 0);
    const avgPerKm = totalKm > 0 ? totalGross / totalKm : 0;

    return {
      acceptedCount: acceptedRides.length,
      declinedCount: rides.filter((r) => r.decision === "declined" || r.decision === "cancelled_after_review").length,
      totalCallsCount: rides.length,
      totalGross,
      totalOffered,
      totalNet,
      totalKm,
      avgPerKm,
      totalDiscrepancy,
    };
  }, [rides]);

  // Filtered history list
  const filteredRides = useMemo(() => {
    return rides.filter((r) => {
      if (historyPlatformFilter !== "ALL" && r.platform !== historyPlatformFilter) return false;
      if (historyDecisionFilter === "ACCEPTED" && r.decision !== "accepted") return false;
      if (historyDecisionFilter === "DECLINED" && r.decision !== "declined" && r.decision !== "cancelled_after_review")
        return false;
      if (
        historyDecisionFilter === "DISCREPANCY" &&
        !(r.receivedValue !== null && r.receivedValue < r.offeredValue)
      )
        return false;
      return true;
    });
  }, [rides, historyPlatformFilter, historyDecisionFilter]);

  const vConfig = user?.vehicleConfig || {
    vehicleType: "car_flex",
    vehicleModel: "Veículo Padrão",
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

  const realVehicleCostPerKm =
    (vConfig.consumptionPerUnit > 0 ? vConfig.fuelPricePerUnit / vConfig.consumptionPerUnit : 0.5) +
    vConfig.maintenanceCostPerKm;

  if (loading) {
    return (
      <div className="min-h-screen bg-[#04241E] flex flex-col items-center justify-center p-6 text-white">
        <img src="/logo-goorno-clean.png" alt="GoOrNo" className="h-24 object-contain mb-4 animate-pulse" />
        <p className="text-sm font-semibold tracking-wider text-[#97B304] uppercase">
          Carregando Copiloto GoOrNo...
        </p>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-[#04241E] text-[#F6F8F5] flex flex-col pb-16">
      {/* Toast Notification */}
      {toastMsg && (
        <div className="fixed top-4 right-4 z-[100] bg-[#073B32] border-2 border-[#97B304] text-white px-4 py-3 rounded-2xl shadow-2xl flex items-center gap-2.5 animate-bounce">
          <CheckCircle2 className="w-5 h-5 text-[#97B304] shrink-0" />
          <span className="text-sm font-bold">{toastMsg}</span>
        </div>
      )}

      {/* =====================================================================
          FLOATING OVERLAY POP-UP (SEMÁFORO EM TEMPO REAL SOBRE UBER / 99)
         ===================================================================== */}
      {incomingRide && (
        <div className="fixed inset-0 z-50 bg-black/75 backdrop-blur-sm flex items-center justify-center p-3 sm:p-4">
          <div
            className={`w-full max-w-md rounded-3xl border-4 shadow-2xl overflow-hidden transition-all ${
              incomingRide.verdict === "GO"
                ? "bg-[#06352C] border-[#97B304]"
                : incomingRide.verdict === "WARN"
                ? "bg-[#2D2606] border-amber-400"
                : "bg-[#330C0C] border-red-500"
            }`}
          >
            {/* Top Bar of Floating Card */}
            <div className="px-4 py-3 bg-black/35 flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-1 rounded-full text-xs font-black uppercase bg-white/15 text-white">
                  {incomingRide.platform} • {incomingRide.category}
                </span>
                <span className="text-xs font-bold text-amber-300">★ {incomingRide.passengerRating.toFixed(2)}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="px-2.5 py-0.5 rounded-full text-xs font-extrabold bg-white/10 text-white">
                  ⏱️ {overlayCountdown}s
                </span>
                <button
                  onClick={() => setIncomingRide(null)}
                  className="p-1 rounded-full hover:bg-white/10 text-white/70 hover:text-white"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Big Semáforo Banner: GO vs WARN vs NO */}
            <div
              className={`px-5 py-3.5 flex items-center justify-between ${
                incomingRide.verdict === "GO"
                  ? "bg-gradient-to-r from-[#7C9303] to-[#97B304] text-[#04241E]"
                  : incomingRide.verdict === "WARN"
                  ? "bg-amber-400 text-slate-950"
                  : "bg-red-600 text-white"
              }`}
            >
              <div className="flex items-center gap-2.5">
                {incomingRide.verdict === "GO" ? (
                  <div className="w-9 h-9 rounded-full bg-[#073B32] text-[#97B304] flex items-center justify-center font-black text-xl">
                    ✓
                  </div>
                ) : incomingRide.verdict === "WARN" ? (
                  <AlertTriangle className="w-8 h-8 text-slate-950" />
                ) : (
                  <div className="w-9 h-9 rounded-full bg-white text-red-600 flex items-center justify-center font-black text-xl">
                    −
                  </div>
                )}
                <div>
                  <div className="text-xs font-black uppercase tracking-wider opacity-85">
                    SEMÁFORO GO OR NO
                  </div>
                  <div className="text-xl font-black tracking-tight">
                    {incomingRide.verdict === "GO"
                      ? "GO! COMPENSA ACEITAR"
                      : incomingRide.verdict === "WARN"
                      ? "ATENÇÃO! CORRIDA MÉDIA"
                      : "NO! NÃO COMPENSA (PREJUÍZO)"}
                  </div>
                </div>
              </div>
              <div className="text-right">
                <div className="text-xs font-bold uppercase opacity-80">Oferta Tela</div>
                <div className="text-2xl font-black">{formatBRL(incomingRide.offeredValue)}</div>
              </div>
            </div>

            {/* Risk Area Alert inside Popup if detected */}
            {incomingRide.isRiskArea && (
              <div className="bg-red-600/90 text-white px-4 py-2 text-xs font-extrabold flex items-center gap-2">
                <ShieldAlert className="w-4 h-4 shrink-0" />
                <span>ALERTA DE SEGURANÇA: Endereço em Área de Risco! Verifique o Street View!</span>
              </div>
            )}

            {/* 3 Core Metrics Grid: R$/KM, R$/Hora, Lucro Líquido */}
            <div className="p-4 grid grid-cols-3 gap-2.5 bg-black/20">
              <div className="rounded-2xl bg-white/10 p-3 text-center border border-white/10">
                <div className="text-[11px] font-bold uppercase text-white/70">Ganho / KM</div>
                <div
                  className={`text-lg sm:text-xl font-black mt-0.5 ${
                    incomingRide.valuePerKm >= vConfig.greenMinPerKm
                      ? "text-[#97B304]"
                      : incomingRide.valuePerKm >= vConfig.yellowMinPerKm
                      ? "text-amber-300"
                      : "text-red-400"
                  }`}
                >
                  R$ {incomingRide.valuePerKm.toFixed(2)}
                </div>
                <div className="text-[10px] text-white/60">Total: {incomingRide.totalDistanceKm} km</div>
              </div>

              <div className="rounded-2xl bg-white/10 p-3 text-center border border-white/10">
                <div className="text-[11px] font-bold uppercase text-white/70">Ganho / Hora</div>
                <div
                  className={`text-lg sm:text-xl font-black mt-0.5 ${
                    incomingRide.valuePerHour >= vConfig.greenMinPerHour
                      ? "text-[#97B304]"
                      : incomingRide.valuePerHour >= vConfig.yellowMinPerHour
                      ? "text-amber-300"
                      : "text-red-400"
                  }`}
                >
                  R$ {incomingRide.valuePerHour.toFixed(0)}/h
                </div>
                <div className="text-[10px] text-white/60">Tempo: {incomingRide.totalTimeMin} min</div>
              </div>

              <div className="rounded-2xl bg-white/10 p-3 text-center border border-white/10">
                <div className="text-[11px] font-bold uppercase text-white/70">Lucro Limpo</div>
                <div className="text-lg sm:text-xl font-black mt-0.5 text-emerald-300">
                  {formatBRL(incomingRide.netProfit)}
                </div>
                <div className="text-[10px] text-white/60">Custo: -{formatBRL(incomingRide.estimatedTotalCost)}</div>
              </div>
            </div>

            {/* Origin & Destination with quick Street View triggers */}
            <div className="px-4 py-3 space-y-2.5 text-xs">
              <div className="flex items-start justify-between gap-2 bg-white/5 p-2.5 rounded-xl border border-white/10">
                <div className="flex items-start gap-2">
                  <MapPin className="w-4 h-4 text-[#97B304] shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-[#97B304] uppercase text-[10px]">
                      Embarque ({incomingRide.pickupDistanceKm} km • {incomingRide.pickupTimeMin} min)
                    </span>
                    <p className="text-white font-semibold leading-snug mt-0.5">{incomingRide.originAddress}</p>
                  </div>
                </div>
                <a
                  href={buildStreetViewExternalUrl(incomingRide.originAddress)}
                  target="_blank"
                  rel="noreferrer"
                  className="px-2.5 py-1.5 rounded-lg bg-[#7C9303] text-white font-bold text-[11px] shrink-0 hover:bg-[#97B304] hover:text-[#04241E] transition"
                >
                  📸 Rua
                </a>
              </div>

              <div className="flex items-start justify-between gap-2 bg-white/5 p-2.5 rounded-xl border border-white/10">
                <div className="flex items-start gap-2">
                  <Navigation className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                  <div>
                    <span className="font-bold text-amber-300 uppercase text-[10px]">
                      Destino ({incomingRide.tripDistanceKm} km • {incomingRide.tripTimeMin} min)
                    </span>
                    <p className="text-white font-semibold leading-snug mt-0.5">{incomingRide.destinationAddress}</p>
                  </div>
                </div>
                <a
                  href={buildStreetViewExternalUrl(incomingRide.destinationAddress)}
                  target="_blank"
                  rel="noreferrer"
                  className="px-2.5 py-1.5 rounded-lg bg-[#7C9303] text-white font-bold text-[11px] shrink-0 hover:bg-[#97B304] hover:text-[#04241E] transition"
                >
                  📸 Rua
                </a>
              </div>
            </div>

            {/* Bottom Decision Buttons: GO (Accept & Inspect Street View) vs NO (Decline) */}
            <div className="p-4 pt-2 grid grid-cols-2 gap-3">
              <button
                onClick={() => handleDecision(incomingRide, "declined", false)}
                className="py-3.5 px-4 rounded-2xl bg-red-600/25 hover:bg-red-600/40 border-2 border-red-500 text-red-200 font-black text-sm flex items-center justify-center gap-2 transition cursor-pointer"
              >
                <XCircle className="w-5 h-5" />
                <span>NO • RECUSAR</span>
              </button>
              <button
                onClick={() => handleDecision(incomingRide, "accepted", true)}
                className="py-3.5 px-4 rounded-2xl bg-[#97B304] hover:bg-[#a9c906] text-[#04241E] font-black text-sm flex items-center justify-center gap-2 shadow-lg transition cursor-pointer"
              >
                <CheckCircle2 className="w-5 h-5" />
                <span>GO • ACEITAR & VER RUA</span>
              </button>
            </div>
          </div>
        </div>
      )}

      {/* =====================================================================
          TOP HEADER BAR (BRAND IDENTITY + VOICE TOGGLE + QUICK SIMULATE)
         ===================================================================== */}
      <header className="bg-[#073B32] border-b border-[#7C9303]/40 sticky top-0 z-30 shadow-lg">
        <div className="max-w-6xl mx-auto px-4 py-3 flex items-center justify-between gap-3">
          {/* Official Brand Logo (5x tap unlocks Admin) */}
          <div
            onClick={handleLogoTap}
            className="flex items-center gap-3 cursor-pointer select-none"
            title="GoOrNo — Aceitar ou Recusar?"
          >
            <div className="bg-white rounded-2xl px-2.5 py-1.5 shadow-md border-2 border-[#7C9303] flex items-center justify-center">
              <img src="/logo-goorno-clean.png" alt="GoOrNo Logo" className="h-9 sm:h-10 w-auto object-contain" />
            </div>
            <div>
              <div className="flex items-center gap-1.5">
                <span className="text-lg sm:text-xl font-black tracking-tight text-white">
                  Go<span className="text-[#97B304]">Or</span>No
                </span>
                <span className="hidden sm:inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-extrabold bg-[#7C9303]/30 text-[#97B304] border border-[#97B304]/40">
                  <span className="w-1.5 h-1.5 rounded-full bg-[#97B304] animate-ping" />
                  COPILOTO ATIVO
                </span>
              </div>
              <p className="text-[10px] sm:text-xs font-bold tracking-widest uppercase text-[#97B304]">
                ACEITAR OU RECUSAR?
              </p>
            </div>
          </div>

          {/* Right Header Controls */}
          <div className="flex items-center gap-2">
            <button
              onClick={() => handleSaveVehicleConfig({ voiceAlertEnabled: !vConfig.voiceAlertEnabled })}
              className={`px-3 py-2 rounded-xl text-xs font-extrabold flex items-center gap-1.5 border transition cursor-pointer ${
                vConfig.voiceAlertEnabled
                  ? "bg-[#7C9303]/25 border-[#97B304] text-[#97B304]"
                  : "bg-white/5 border-white/15 text-white/60"
              }`}
              title="Ligar/Desligar aviso por voz quando tocar corrida"
            >
              {vConfig.voiceAlertEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
              <span className="hidden md:inline">{vConfig.voiceAlertEnabled ? "Voz ON" : "Voz OFF"}</span>
            </button>

            <button
              onClick={() => {
                const randomPreset = PRESET_SIMULATIONS[Math.floor(Math.random() * PRESET_SIMULATIONS.length)];
                triggerRideCapture(randomPreset);
              }}
              className="px-3.5 py-2 rounded-xl bg-[#97B304] hover:bg-[#a8c605] text-[#04241E] font-black text-xs sm:text-sm flex items-center gap-1.5 shadow-md transition cursor-pointer pulse-lime"
            >
              <Play className="w-4 h-4 fill-current" />
              <span>Simular Chamada</span>
            </button>
          </div>
        </div>

        {/* Navigation Bar (Strict Single-Line no-wrap-bar) */}
        <div className="bg-[#04241E]/80 border-t border-white/10">
          <div className="max-w-6xl mx-auto px-4 py-2 no-wrap-bar gap-2">
            <button
              onClick={() => setActiveTab("copilot")}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-extrabold flex items-center gap-2 transition cursor-pointer ${
                activeTab === "copilot"
                  ? "bg-[#7C9303] text-white shadow-md"
                  : "bg-white/5 text-white/75 hover:bg-white/10"
              }`}
            >
              <Gauge className="w-4 h-4" />
              <span>1. Copiloto & Semáforo</span>
            </button>

            <button
              onClick={() => setActiveTab("evaluate")}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-extrabold flex items-center gap-2 transition cursor-pointer ${
                activeTab === "evaluate"
                  ? "bg-[#7C9303] text-white shadow-md"
                  : "bg-white/5 text-white/75 hover:bg-white/10"
              }`}
            >
              <Eye className="w-4 h-4" />
              <span>2. Avaliar Corrida Aceita & Street View</span>
              {activeRideToEvaluate && (
                <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-[#04241E] text-[#97B304] font-black">
                  360°
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab("history")}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-extrabold flex items-center gap-2 transition cursor-pointer ${
                activeTab === "history"
                  ? "bg-[#7C9303] text-white shadow-md"
                  : "bg-white/5 text-white/75 hover:bg-white/10"
              }`}
            >
              <History className="w-4 h-4" />
              <span>3. Histórico & Conferir Pagamentos ({rides.length})</span>
              {shiftMetrics.totalDiscrepancy > 0 && (
                <span className="px-1.5 py-0.5 rounded-full text-[10px] bg-red-600 text-white font-black">
                  Alerta R$
                </span>
              )}
            </button>

            <button
              onClick={() => setActiveTab("settings")}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-extrabold flex items-center gap-2 transition cursor-pointer ${
                activeTab === "settings"
                  ? "bg-[#7C9303] text-white shadow-md"
                  : "bg-white/5 text-white/75 hover:bg-white/10"
              }`}
            >
              <Wrench className="w-4 h-4" />
              <span>4. Veículo, Metas & Áreas de Risco</span>
            </button>

            <button
              onClick={() => setActiveTab("apk")}
              className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-extrabold flex items-center gap-2 transition cursor-pointer ${
                activeTab === "apk"
                  ? "bg-[#7C9303] text-white shadow-md"
                  : "bg-white/5 text-white/75 hover:bg-white/10"
              }`}
            >
              <Smartphone className="w-4 h-4" />
              <span>5. Leitor de Tela Android (.APK)</span>
            </button>

            {adminUnlocked && (
              <button
                onClick={() => {
                  setActiveTab("admin");
                  loadAdminOverview();
                }}
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-extrabold flex items-center gap-2 transition cursor-pointer ${
                  activeTab === "admin"
                    ? "bg-amber-500 text-slate-950 shadow-md"
                    : "bg-amber-500/20 text-amber-300 border border-amber-400/40"
                }`}
              >
                <Lock className="w-4 h-4" />
                <span>👑 Admin & Cofre 7 Dias</span>
              </button>
            )}
          </div>
        </div>
      </header>

      {/* =====================================================================
          MAIN CONTENT CONTAINER
         ===================================================================== */}
      <main className="max-w-6xl mx-auto w-full px-4 py-6 space-y-6 flex-1">
        {/* TOP SHIFT KPI CARDS */}
        <section className="grid grid-cols-2 lg:grid-cols-4 gap-3 sm:gap-4">
          <div className="bg-[#073B32] border border-[#7C9303]/40 rounded-2xl p-4 shadow-md">
            <div className="flex items-center justify-between text-xs text-white/70 font-bold uppercase">
              <span>Lucro Líquido (Bolso)</span>
              <DollarSign className="w-4 h-4 text-[#97B304]" />
            </div>
            <div className="text-2xl sm:text-3xl font-black text-[#97B304] mt-1">
              {formatBRL(shiftMetrics.totalNet)}
            </div>
            <div className="text-[11px] text-white/65 mt-1">
              Bruto: {formatBRL(shiftMetrics.totalGross)} • Meta: {formatBRL(vConfig.dailyNetGoal)}
            </div>
          </div>

          <div className="bg-[#073B32] border border-[#7C9303]/40 rounded-2xl p-4 shadow-md">
            <div className="flex items-center justify-between text-xs text-white/70 font-bold uppercase">
              <span>Média R$ / KM Hoje</span>
              <TrendingUp className="w-4 h-4 text-[#97B304]" />
            </div>
            <div className="text-2xl sm:text-3xl font-black text-white mt-1">
              R$ {shiftMetrics.avgPerKm.toFixed(2)}/km
            </div>
            <div className="text-[11px] text-white/65 mt-1">
              Seu custo real: R$ {realVehicleCostPerKm.toFixed(2)}/km ({shiftMetrics.totalKm.toFixed(1)} km)
            </div>
          </div>

          <div className="bg-[#073B32] border border-[#7C9303]/40 rounded-2xl p-4 shadow-md">
            <div className="flex items-center justify-between text-xs text-white/70 font-bold uppercase">
              <span>Filtro GoOrNo (Chamadas)</span>
              <CheckCircle2 className="w-4 h-4 text-[#97B304]" />
            </div>
            <div className="text-2xl sm:text-3xl font-black text-white mt-1">
              {shiftMetrics.acceptedCount} GO <span className="text-white/40 text-lg">/ {shiftMetrics.declinedCount} NO</span>
            </div>
            <div className="text-[11px] text-white/65 mt-1">
              {shiftMetrics.totalCallsCount} corridas gravadas no histórico
            </div>
          </div>

          <div
            onClick={() => {
              setHistoryDecisionFilter("DISCREPANCY");
              setActiveTab("history");
            }}
            className={`rounded-2xl p-4 shadow-md border cursor-pointer transition ${
              shiftMetrics.totalDiscrepancy > 0
                ? "bg-red-950/60 border-red-500/70 hover:bg-red-950/80"
                : "bg-[#073B32] border-[#7C9303]/40"
            }`}
          >
            <div className="flex items-center justify-between text-xs text-white/80 font-bold uppercase">
              <span>Auditoria de Pagamento</span>
              <ClipboardCheck className="w-4 h-4 text-amber-400" />
            </div>
            <div
              className={`text-2xl sm:text-3xl font-black mt-1 ${
                shiftMetrics.totalDiscrepancy > 0 ? "text-red-400" : "text-emerald-300"
              }`}
            >
              {shiftMetrics.totalDiscrepancy > 0 ? `-${formatBRL(shiftMetrics.totalDiscrepancy)}` : "100% Certo"}
            </div>
            <div className="text-[11px] text-white/70 mt-1">
              {shiftMetrics.totalDiscrepancy > 0
                ? "⚠️ Diferença detectada! Clique p/ conferir"
                : "Nenhum desconto indevido nas corridas"}
            </div>
          </div>
        </section>

        {/* ===================================================================
            TAB 1: COPILOTO & SEMÁFORO (SIMULADOR EM TEMPO REAL + CALCULADORA)
           =================================================================== */}
        {activeTab === "copilot" && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Left Column: Live Screen Reader Simulator & Presets */}
            <div className="lg:col-span-7 space-y-5">
              <div className="bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-5 sm:p-6 shadow-xl">
                <div className="flex items-center justify-between gap-2 mb-4">
                  <div>
                    <span className="px-2.5 py-1 rounded-full text-[11px] font-black uppercase bg-[#7C9303]/30 text-[#97B304]">
                      LEITOR DE TELA EM TEMPO REAL
                    </span>
                    <h2 className="text-xl sm:text-2xl font-black text-white mt-1.5">
                      Toque de Chamada (Uber • 99 • inDrive)
                    </h2>
                    <p className="text-xs sm:text-sm text-white/75 mt-1">
                      Quando uma viagem toca na tela do celular, o <strong>GoOrNo</strong> lê instantaneamente o valor,
                      os KMs, o tempo e os endereços, mostrando o Semáforo Flutuante <strong>GO (✓)</strong> ou{" "}
                      <strong>NO (−)</strong>.
                    </p>
                  </div>
                </div>

                {/* 4 Realistic One-Click Call Simulators */}
                <div className="space-y-2.5">
                  <div className="text-xs font-extrabold uppercase tracking-wider text-[#97B304]">
                    ⚡ Clique para testar como o Semáforo aparece quando toca uma corrida:
                  </div>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {PRESET_SIMULATIONS.map((preset, idx) => (
                      <button
                        key={idx}
                        onClick={() => triggerRideCapture(preset)}
                        className="text-left p-3.5 rounded-2xl bg-[#04241E] hover:bg-[#04241E]/70 border border-[#7C9303]/50 hover:border-[#97B304] transition group cursor-pointer"
                      >
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-black text-white group-hover:text-[#97B304] transition">
                            {preset.label}
                          </span>
                          <span className="text-sm font-black text-[#97B304]">{formatBRL(preset.offeredValue)}</span>
                        </div>
                        <p className="text-[11px] text-white/65 mt-1 truncate">
                          📍 {preset.originNeighborhood} ➔ {preset.destinationNeighborhood} (
                          {(preset.pickupDistanceKm + preset.tripDistanceKm).toFixed(1)} km)
                        </p>
                      </button>
                    ))}
                  </div>

                  {/* Screenshot Reader Box */}
                  <div className="mt-4 p-4 rounded-2xl bg-[#04241E] border-2 border-dashed border-[#97B304]/60 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-3">
                    <div>
                      <div className="text-xs font-black uppercase text-[#97B304]">
                        📸 Tem um Print de Corrida da Uber / 99 no celular ou PC?
                      </div>
                      <p className="text-[11px] text-white/75 mt-0.5">
                        Envie o print da chamada aqui: a IA lê o valor, os KMs e os endereços na hora e abre o Semáforo + Street View!
                      </p>
                    </div>
                    <input
                      ref={screenshotInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) handleScreenshotFile(f);
                        e.target.value = "";
                      }}
                    />
                    <button
                      onClick={() => screenshotInputRef.current?.click()}
                      disabled={scanningScreenshot}
                      className="px-4 py-2.5 rounded-xl bg-[#97B304] hover:bg-[#a9c805] text-[#04241E] font-black text-xs shrink-0 cursor-pointer transition"
                    >
                      {scanningScreenshot ? "⏳ Lendo Print..." : "📸 Ler Print de Corrida"}
                    </button>
                  </div>
                </div>

                {/* Last Captured Ride Quick Action Box */}
                {activeRideToEvaluate && (
                  <div className="mt-6 pt-5 border-t border-white/10">
                    <div className="flex items-center justify-between mb-2">
                      <span className="text-xs font-extrabold uppercase text-white/70">
                        Última Corrida Capturada na Tela:
                      </span>
                      <button
                        onClick={() => setActiveTab("evaluate")}
                        className="text-xs font-black text-[#97B304] hover:underline flex items-center gap-1 cursor-pointer"
                      >
                        <span>Abrir Raio-X & Street View</span>
                        <ArrowRight className="w-3.5 h-3.5" />
                      </button>
                    </div>
                    <div className="p-4 rounded-2xl bg-[#04241E] border border-white/10 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
                      <div>
                        <div className="flex items-center gap-2">
                          <span
                            className={`px-2.5 py-0.5 rounded-full text-xs font-black ${
                              activeRideToEvaluate.verdict === "GO"
                                ? "bg-[#97B304] text-[#04241E]"
                                : activeRideToEvaluate.verdict === "WARN"
                                ? "bg-amber-400 text-slate-950"
                                : "bg-red-600 text-white"
                            }`}
                          >
                            {activeRideToEvaluate.verdict === "GO"
                              ? "✓ GO (COMPENSA)"
                              : activeRideToEvaluate.verdict === "WARN"
                              ? "! AVALIAR"
                              : "− NO (RECUSAR)"}
                          </span>
                          <span className="font-black text-white">
                            {activeRideToEvaluate.platform} • {formatBRL(activeRideToEvaluate.offeredValue)}
                          </span>
                          <span className="text-xs text-[#97B304] font-bold">
                            (R$ {activeRideToEvaluate.valuePerKm.toFixed(2)}/km • Lucro{" "}
                            {formatBRL(activeRideToEvaluate.netProfit)})
                          </span>
                        </div>
                        <p className="text-xs text-white/75 mt-1.5">
                          <strong>Origem:</strong> {activeRideToEvaluate.originAddress}
                        </p>
                        <p className="text-xs text-white/75 mt-0.5">
                          <strong>Destino:</strong> {activeRideToEvaluate.destinationAddress}
                        </p>
                      </div>
                      <div className="flex sm:flex-col gap-2 shrink-0">
                        <button
                          onClick={() => {
                            setActiveTab("evaluate");
                            setStreetViewTarget({
                              label: "Destino",
                              address: activeRideToEvaluate.destinationAddress,
                            });
                          }}
                          className="px-3.5 py-2 rounded-xl bg-[#7C9303] hover:bg-[#97B304] text-white hover:text-[#04241E] font-extrabold text-xs flex items-center justify-center gap-1.5 transition cursor-pointer"
                        >
                          <Eye className="w-4 h-4" />
                          <span>Ver Rua (Street View)</span>
                        </button>
                        <button
                          onClick={() => setIncomingRide(activeRideToEvaluate)}
                          className="px-3.5 py-2 rounded-xl bg-white/10 hover:bg-white/15 text-white font-bold text-xs cursor-pointer"
                        >
                          Reabrir Pop-up
                        </button>
                      </div>
                    </div>
                  </div>
                )}
              </div>
            </div>

            {/* Right Column: Instant Manual Ride Analyzer */}
            <div className="lg:col-span-5">
              <div className="bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-5 sm:p-6 shadow-xl">
                <h3 className="text-lg font-black text-white flex items-center gap-2">
                  <Sparkles className="w-5 h-5 text-[#97B304]" />
                  <span>Calculadora Manual de Corrida</span>
                </h3>
                <p className="text-xs text-white/70 mt-1 mb-4">
                  Quer testar ou lançar uma corrida específica (ou corrida particular)? Digite abaixo para acionar o
                  Semáforo e gravar no histórico:
                </p>

                <div className="space-y-3">
                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="text-[11px] font-bold uppercase text-white/75">Aplicativo</label>
                      <select
                        value={calcPlatform}
                        onChange={(e) => setCalcPlatform(e.target.value as any)}
                        className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm font-bold"
                      >
                        <option value="Uber">Uber</option>
                        <option value="99">99 Motorista</option>
                        <option value="inDrive">inDrive</option>
                      </select>
                    </div>
                    <div>
                      <label className="text-[11px] font-bold uppercase text-white/75">Valor da Oferta (R$)</label>
                      <input
                        type="number"
                        step="0.50"
                        value={calcOffered}
                        onChange={(e) => setCalcOffered(e.target.value)}
                        className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-[#97B304]/60 text-[#97B304] text-sm font-black"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="text-[11px] font-bold uppercase text-white/75">KM até Passageiro</label>
                      <input
                        type="number"
                        step="0.1"
                        value={calcPickupKm}
                        onChange={(e) => setCalcPickupKm(e.target.value)}
                        className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm"
                      />
                    </div>
                    <div>
                      <label className="text-[11px] font-bold uppercase text-white/75">KM da Viagem</label>
                      <input
                        type="number"
                        step="0.1"
                        value={calcTripKm}
                        onChange={(e) => setCalcTripKm(e.target.value)}
                        className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-2.5">
                    <div>
                      <label className="text-[11px] font-bold uppercase text-white/75">Tempo Busca (min)</label>
                      <input
                        type="number"
                        value={calcPickupMin}
                        onChange={(e) => setCalcPickupMin(e.target.value)}
                        className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm"
                      />
                    </div>
                    <div>
                      <label className="text-[11px] font-bold uppercase text-white/75">Tempo Viagem (min)</label>
                      <input
                        type="number"
                        value={calcTripMin}
                        onChange={(e) => setCalcTripMin(e.target.value)}
                        className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="text-[11px] font-bold uppercase text-white/75">
                      Endereço de Origem (Embarque)
                    </label>
                    <input
                      type="text"
                      value={calcOrigin}
                      onChange={(e) => setCalcOrigin(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-xs"
                    />
                  </div>

                  <div>
                    <label className="text-[11px] font-bold uppercase text-white/75">
                      Endereço de Destino (Desembarque)
                    </label>
                    <input
                      type="text"
                      value={calcDest}
                      onChange={(e) => setCalcDest(e.target.value)}
                      className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-xs"
                    />
                  </div>

                  <button
                    onClick={() =>
                      triggerRideCapture({
                        platform: calcPlatform,
                        category: calcCategory,
                        offeredValue: parseFloat(calcOffered) || 20,
                        pickupDistanceKm: parseFloat(calcPickupKm) || 1,
                        tripDistanceKm: parseFloat(calcTripKm) || 8,
                        pickupTimeMin: parseInt(calcPickupMin, 10) || 4,
                        tripTimeMin: parseInt(calcTripMin, 10) || 18,
                        originAddress: calcOrigin,
                        destinationAddress: calcDest,
                      })
                    }
                    className="w-full py-3.5 rounded-2xl bg-[#97B304] hover:bg-[#a9c805] text-[#04241E] font-black text-sm flex items-center justify-center gap-2 shadow-lg transition cursor-pointer mt-2"
                  >
                    <Gauge className="w-5 h-5" />
                    <span>Calcular GO OR NO & Abrir Semáforo</span>
                  </button>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* ===================================================================
            TAB 2: AVALIAR CORRIDA ACEITA & STREET VIEW (SEGURANÇA ANTES DE IR)
           =================================================================== */}
        {activeTab === "evaluate" && (
          <div className="space-y-6">
            <div className="bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-5 sm:p-6 shadow-xl">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-5 border-b border-white/10">
                <div>
                  <span className="px-2.5 py-1 rounded-full text-[11px] font-black uppercase bg-[#97B304] text-[#04241E]">
                    ANÁLISE PÓS-ACEITE & STREET VIEW 360°
                  </span>
                  <h2 className="text-xl sm:text-2xl font-black text-white mt-2">
                    Aceitou no reflexo? Avalie a Corrida e Veja a Rua Antes de Ir!
                  </h2>
                  <p className="text-xs sm:text-sm text-white/75 mt-1">
                    Clique em <strong>Ver Rua no Street View</strong> na Origem ou no Destino para inspecionar a
                    vizinhança e decidir com segurança se vai buscar o passageiro ou se cancela a viagem.
                  </p>
                </div>

                {/* Selector to switch between recent rides */}
                <div className="shrink-0">
                  <label className="block text-[11px] font-bold uppercase text-[#97B304] mb-1">
                    Selecionar Corrida Recente:
                  </label>
                  <select
                    value={activeRideToEvaluate?.id || ""}
                    onChange={(e) => setSelectedRideId(e.target.value)}
                    className="px-3.5 py-2.5 rounded-xl bg-[#04241E] border border-[#7C9303] text-white text-xs sm:text-sm font-bold"
                  >
                    {rides.map((r) => (
                      <option key={r.id} value={r.id}>
                        [{r.verdict}] {r.platform} {formatBRL(r.offeredValue)} — {r.originNeighborhood} ➔{" "}
                        {r.destinationNeighborhood}
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {activeRideToEvaluate && (
                <div className="mt-5 space-y-6">
                  {/* Verdict + Financial Breakdown Banner */}
                  <div className="grid grid-cols-2 sm:grid-cols-5 gap-3">
                    <div
                      className={`col-span-2 sm:col-span-1 rounded-2xl p-3.5 flex flex-col justify-center items-center text-center font-black ${
                        activeRideToEvaluate.verdict === "GO"
                          ? "bg-[#97B304] text-[#04241E]"
                          : activeRideToEvaluate.verdict === "WARN"
                          ? "bg-amber-400 text-slate-950"
                          : "bg-red-600 text-white"
                      }`}
                    >
                      <span className="text-[10px] uppercase tracking-wider opacity-80">Veredito</span>
                      <span className="text-xl mt-0.5">
                        {activeRideToEvaluate.verdict === "GO"
                          ? "✓ GO (BOA)"
                          : activeRideToEvaluate.verdict === "WARN"
                          ? "! MÉDIA"
                          : "− NO (RUIM)"}
                      </span>
                    </div>

                    <div className="bg-[#04241E] rounded-2xl p-3.5 border border-white/10">
                      <span className="text-[11px] text-white/65 font-bold uppercase">Valor na Tela</span>
                      <div className="text-xl font-black text-white mt-0.5">
                        {formatBRL(activeRideToEvaluate.offeredValue)}
                      </div>
                      <span className="text-[10px] text-[#97B304] font-bold">
                        {activeRideToEvaluate.platform} • ★ {activeRideToEvaluate.passengerRating}
                      </span>
                    </div>

                    <div className="bg-[#04241E] rounded-2xl p-3.5 border border-white/10">
                      <span className="text-[11px] text-white/65 font-bold uppercase">Ganho / KM</span>
                      <div className="text-xl font-black text-[#97B304] mt-0.5">
                        R$ {activeRideToEvaluate.valuePerKm.toFixed(2)}/km
                      </div>
                      <span className="text-[10px] text-white/60">
                        Busca {activeRideToEvaluate.pickupDistanceKm}km + Viagem {activeRideToEvaluate.tripDistanceKm}km
                      </span>
                    </div>

                    <div className="bg-[#04241E] rounded-2xl p-3.5 border border-white/10">
                      <span className="text-[11px] text-white/65 font-bold uppercase">Ganho / Hora</span>
                      <div className="text-xl font-black text-white mt-0.5">
                        R$ {activeRideToEvaluate.valuePerHour.toFixed(0)}/h
                      </div>
                      <span className="text-[10px] text-white/60">
                        Tempo total: {activeRideToEvaluate.totalTimeMin} min
                      </span>
                    </div>

                    <div className="bg-[#04241E] rounded-2xl p-3.5 border border-emerald-500/30">
                      <span className="text-[11px] text-emerald-300 font-bold uppercase">Lucro Limpo</span>
                      <div className="text-xl font-black text-emerald-300 mt-0.5">
                        {formatBRL(activeRideToEvaluate.netProfit)}
                      </div>
                      <span className="text-[10px] text-white/60">
                        Descontado R$ {activeRideToEvaluate.estimatedTotalCost.toFixed(2)} de custo
                      </span>
                    </div>
                  </div>

                  {/* ORIGIN & DESTINATION INTERACTIVE STREET VIEW CARDS */}
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* ORIGIN CARD */}
                    <div className="rounded-2xl bg-[#04241E] border-2 border-[#7C9303]/60 p-4 flex flex-col justify-between gap-4">
                      <div>
                        <div className="flex items-center justify-between">
                          <span className="px-2.5 py-1 rounded-lg bg-[#7C9303]/25 text-[#97B304] text-xs font-black uppercase flex items-center gap-1.5">
                            <MapPin className="w-4 h-4" />
                            1. Endereço de Origem (Embarque)
                          </span>
                          <span className="text-xs font-bold text-white/70">
                            {activeRideToEvaluate.pickupDistanceKm} km ({activeRideToEvaluate.pickupTimeMin} min)
                          </span>
                        </div>

                        <button
                          onClick={() =>
                            setStreetViewTarget({
                              label: "Origem (Embarque)",
                              address: activeRideToEvaluate.originAddress,
                            })
                          }
                          className="mt-3 text-left group cursor-pointer block w-full"
                        >
                          <p className="text-base sm:text-lg font-black text-white group-hover:text-[#97B304] underline decoration-[#97B304]/60 underline-offset-4 transition">
                            {activeRideToEvaluate.originAddress}
                          </p>
                          <span className="text-[11px] text-[#97B304] font-bold mt-1 inline-block">
                            👆 Clique no endereço acima ou nos botões abaixo para abrir a rua no Street View
                          </span>
                        </button>
                      </div>

                      <div className="flex flex-wrap gap-2">
                        <button
                          onClick={() =>
                            setStreetViewTarget({
                              label: "Origem (Embarque)",
                              address: activeRideToEvaluate.originAddress,
                            })
                          }
                          className="flex-1 py-2.5 px-3 rounded-xl bg-[#97B304] hover:bg-[#a8c605] text-[#04241E] font-black text-xs flex items-center justify-center gap-1.5 cursor-pointer"
                        >
                          <Eye className="w-4 h-4" />
                          <span>📸 Ver Origem Aqui na Tela</span>
                        </button>
                        <a
                          href={buildStreetViewExternalUrl(activeRideToEvaluate.originAddress)}
                          target="_blank"
                          rel="noreferrer"
                          className="py-2.5 px-3 rounded-xl bg-white/10 hover:bg-white/20 text-white font-bold text-xs flex items-center justify-center gap-1.5"
                        >
                          <Navigation className="w-4 h-4 text-[#97B304]" />
                          <span>App Google Maps / Street</span>
                        </a>
                      </div>
                    </div>

                    {/* DESTINATION CARD */}
                    <div
                      className={`rounded-2xl bg-[#04241E] border-2 p-4 flex flex-col justify-between gap-4 ${
                        activeRideToEvaluate.isRiskArea ? "border-red-500" : "border-amber-400/60"
                      }`}
                    >
                      <div>
                        <div className="flex items-center justify-between">
                          <span className="px-2.5 py-1 rounded-lg bg-amber-400/20 text-amber-300 text-xs font-black uppercase flex items-center gap-1.5">
                            <Navigation className="w-4 h-4" />
                            2. Endereço de Destino (Desembarque)
                          </span>
                          <span className="text-xs font-bold text-white/70">
                            +{activeRideToEvaluate.tripDistanceKm} km ({activeRideToEvaluate.tripTimeMin} min)
                          </span>
                        </div>

                        {activeRideToEvaluate.isRiskArea && (
                          <div className="mt-2 px-3 py-1.5 rounded-xl bg-red-600/30 border border-red-500 text-red-200 text-xs font-extrabold flex items-center gap-1.5">
                            <ShieldAlert className="w-4 h-4 shrink-0 text-red-400" />
                            <span>Atenção: Destino coincide com Área de Risco cadastrada!</span>
                          </div>
                        )}

                        <button
                          onClick={() =>
                            setStreetViewTarget({
                              label: "Destino (Desembarque)",
                              address: activeRideToEvaluate.destinationAddress,
                            })
                          }
                          className="mt-3 text-left group cursor-pointer block w-full"
                        >
                          <p className="text-base sm:text-lg font-black text-white group-hover:text-amber-300 underline decoration-amber-400/60 underline-offset-4 transition">
                            {activeRideToEvaluate.destinationAddress}
                          </p>
                          <span className="text-[11px] text-amber-300 font-bold mt-1 inline-block">
                            👆 Clique no endereço acima ou nos botões abaixo para ver o Destino no Street View
                          </span>
                        </button>
                      </div>

                      <div className="flex flex-wrap gap-2">
                        <button
                          onClick={() =>
                            setStreetViewTarget({
                              label: "Destino (Desembarque)",
                              address: activeRideToEvaluate.destinationAddress,
                            })
                          }
                          className="flex-1 py-2.5 px-3 rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 font-black text-xs flex items-center justify-center gap-1.5 cursor-pointer"
                        >
                          <Eye className="w-4 h-4" />
                          <span>📸 Ver Destino Aqui na Tela</span>
                        </button>
                        <a
                          href={buildStreetViewExternalUrl(activeRideToEvaluate.destinationAddress)}
                          target="_blank"
                          rel="noreferrer"
                          className="py-2.5 px-3 rounded-xl bg-white/10 hover:bg-white/20 text-white font-bold text-xs flex items-center justify-center gap-1.5"
                        >
                          <Navigation className="w-4 h-4 text-amber-300" />
                          <span>App Google Maps / Street</span>
                        </a>
                        <button
                          onClick={() =>
                            handleAddRiskZone(
                              activeRideToEvaluate.destinationAddress,
                              "Sinalizado ao inspecionar corrida aceita"
                            )
                          }
                          className="py-2.5 px-3 rounded-xl bg-red-600/25 hover:bg-red-600/40 border border-red-500/50 text-red-200 font-bold text-xs cursor-pointer"
                          title="Adicionar este endereço como Área de Risco"
                        >
                          ⚠️ Marcar Risco
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* Embedded Interactive Satellite / Street View Inspector */}
                  <div className="rounded-3xl bg-[#04241E] border-2 border-[#7C9303] overflow-hidden shadow-2xl">
                    <div className="px-4 py-3 bg-[#073B32] flex flex-wrap items-center justify-between gap-2">
                      <div className="flex items-center gap-2">
                        <Eye className="w-5 h-5 text-[#97B304]" />
                        <span className="text-xs sm:text-sm font-black text-white">
                          Visor de Segurança da Rua:{" "}
                          <span className="text-[#97B304]">
                            {streetViewTarget?.label || "Destino"} —{" "}
                            {streetViewTarget?.address || activeRideToEvaluate.destinationAddress}
                          </span>
                        </span>
                      </div>
                      <div className="flex items-center gap-2">
                        <button
                          onClick={() =>
                            setStreetViewTarget({
                              label: "Origem (Embarque)",
                              address: activeRideToEvaluate.originAddress,
                            })
                          }
                          className="px-2.5 py-1 rounded-lg bg-white/10 hover:bg-white/20 text-xs font-bold text-white cursor-pointer"
                        >
                          Focar Origem
                        </button>
                        <button
                          onClick={() =>
                            setStreetViewTarget({
                              label: "Destino (Desembarque)",
                              address: activeRideToEvaluate.destinationAddress,
                            })
                          }
                          className="px-2.5 py-1 rounded-lg bg-[#7C9303] text-xs font-bold text-white cursor-pointer"
                        >
                          Focar Destino
                        </button>
                        <a
                          href={buildStreetViewExternalUrl(
                            streetViewTarget?.address || activeRideToEvaluate.destinationAddress
                          )}
                          target="_blank"
                          rel="noreferrer"
                          className="px-3 py-1 rounded-lg bg-[#97B304] text-[#04241E] text-xs font-black"
                        >
                          Abrir Street View 360° Tela Cheia ↗
                        </a>
                      </div>
                    </div>
                    <iframe
                      title="Visor de Rua e Satélite GoOrNo"
                      src={buildEmbedMapUrl(streetViewTarget?.address || activeRideToEvaluate.destinationAddress)}
                      className="w-full h-[340px] sm:h-[400px] border-0"
                      loading="lazy"
                      referrerPolicy="no-referrer-when-downgrade"
                    />
                  </div>

                  {/* Final Driver Decision Buttons after inspecting the street */}
                  <div className="pt-2 grid grid-cols-1 sm:grid-cols-2 gap-3">
                    <button
                      onClick={() => handleDecision(activeRideToEvaluate, "cancelled_after_review", false)}
                      className="py-4 px-5 rounded-2xl bg-red-600/25 hover:bg-red-600/40 border-2 border-red-500 text-red-200 font-black text-sm flex items-center justify-center gap-2 transition cursor-pointer"
                    >
                      <XCircle className="w-5 h-5" />
                      <span>🚫 Rua Perigosa / Não Compensa (Cancelar Viagem)</span>
                    </button>
                    <button
                      onClick={() => handleDecision(activeRideToEvaluate, "accepted", false)}
                      className="py-4 px-5 rounded-2xl bg-[#97B304] hover:bg-[#a9c805] text-[#04241E] font-black text-sm flex items-center justify-center gap-2 shadow-lg transition cursor-pointer"
                    >
                      <CheckCircle2 className="w-5 h-5" />
                      <span>✅ Rua Segura & Lucro Confirmado (Realizar Corrida)</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        )}

        {/* ===================================================================
            TAB 3: HISTÓRICO DE CORRIDAS & AUDITORIA DE PAGAMENTOS
           =================================================================== */}
        {activeTab === "history" && (
          <div className="space-y-5">
            <div className="bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-5 sm:p-6 shadow-xl">
              <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-4 border-b border-white/10">
                <div>
                  <span className="px-2.5 py-1 rounded-full text-[11px] font-black uppercase bg-[#7C9303]/30 text-[#97B304]">
                    MEMÓRIA AUTOMÁTICA DE CHAMADAS & AUDITORIA
                  </span>
                  <h2 className="text-xl sm:text-2xl font-black text-white mt-1.5">
                    Histórico Completo de Viagens & Conferência de Valor Recebido
                  </h2>
                  <p className="text-xs sm:text-sm text-white/75 mt-1">
                    Toda viagem que toca no seu celular fica registrada aqui. Confira se o valor depositado pela Uber/99
                    bate com o valor que apareceu na chamada!
                  </p>
                </div>

                {/* Filters */}
                <div className="flex flex-wrap items-center gap-2">
                  <select
                    value={historyPlatformFilter}
                    onChange={(e) => setHistoryPlatformFilter(e.target.value)}
                    className="px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-xs font-bold text-white"
                  >
                    <option value="ALL">Todas Plataformas</option>
                    <option value="Uber">Apenas Uber</option>
                    <option value="99">Apenas 99</option>
                    <option value="inDrive">Apenas inDrive</option>
                  </select>

                  <select
                    value={historyDecisionFilter}
                    onChange={(e) => setHistoryDecisionFilter(e.target.value)}
                    className="px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-xs font-bold text-white"
                  >
                    <option value="ALL">Todas ({rides.length})</option>
                    <option value="ACCEPTED">Aceitas / Realizadas</option>
                    <option value="DECLINED">Recusadas / Canceladas</option>
                    <option value="DISCREPANCY">⚠️ Pagou a Menos (Divergência)</option>
                  </select>
                </div>
              </div>

              {/* Rides List */}
              <div className="mt-4 space-y-3">
                {filteredRides.map((ride) => {
                  const hasShortfall = ride.receivedValue !== null && ride.receivedValue < ride.offeredValue;
                  const shortfallAmount = hasShortfall ? ride.offeredValue - (ride.receivedValue ?? 0) : 0;

                  return (
                    <div
                      key={ride.id}
                      className={`p-4 rounded-2xl bg-[#04241E] border transition ${
                        hasShortfall
                          ? "border-red-500/80"
                          : ride.verdict === "GO"
                          ? "border-[#7C9303]/50"
                          : "border-white/10"
                      }`}
                    >
                      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                        <div className="space-y-1.5">
                          <div className="flex flex-wrap items-center gap-2">
                            <span
                              className={`px-2.5 py-0.5 rounded-full text-[11px] font-black ${
                                ride.verdict === "GO"
                                  ? "bg-[#97B304] text-[#04241E]"
                                  : ride.verdict === "WARN"
                                  ? "bg-amber-400 text-slate-950"
                                  : "bg-red-600 text-white"
                              }`}
                            >
                              {ride.verdict === "GO" ? "✓ GO" : ride.verdict === "WARN" ? "! MÉDIA" : "− NO"}
                            </span>
                            <span className="px-2.5 py-0.5 rounded-lg bg-white/10 text-white text-xs font-extrabold">
                              {ride.platform} • {ride.category}
                            </span>
                            <span className="text-xs text-white/60">
                              🕒 {new Date(ride.createdAt).toLocaleString("pt-BR")}
                            </span>
                            <span
                              className={`px-2 py-0.5 rounded-md text-[11px] font-bold ${
                                ride.decision === "accepted"
                                  ? "bg-emerald-500/20 text-emerald-300"
                                  : "bg-red-500/20 text-red-300"
                              }`}
                            >
                              {ride.decision === "accepted"
                                ? "Aceita"
                                : ride.decision === "cancelled_after_review"
                                ? "Cancelada após Street View"
                                : "Recusada"}
                            </span>
                          </div>

                          <div className="text-xs text-white/85 pt-1 space-y-1">
                            <div className="flex items-center gap-2">
                              <span className="text-[#97B304] font-bold">Embarque:</span>
                              <span>{ride.originAddress}</span>
                              <a
                                href={buildStreetViewExternalUrl(ride.originAddress)}
                                target="_blank"
                                rel="noreferrer"
                                className="text-[11px] text-[#97B304] underline font-bold"
                              >
                                [Street View]
                              </a>
                            </div>
                            <div className="flex items-center gap-2">
                              <span className="text-amber-300 font-bold">Destino:</span>
                              <span>{ride.destinationAddress}</span>
                              <a
                                href={buildStreetViewExternalUrl(ride.destinationAddress)}
                                target="_blank"
                                rel="noreferrer"
                                className="text-[11px] text-amber-300 underline font-bold"
                              >
                                [Street View]
                              </a>
                            </div>
                          </div>

                          <div className="flex flex-wrap items-center gap-3 pt-1 text-xs text-white/70">
                            <span>📏 {ride.totalDistanceKm} km total</span>
                            <span>⏱️ {ride.totalTimeMin} min</span>
                            <span className="text-[#97B304] font-bold">R$ {ride.valuePerKm.toFixed(2)}/km</span>
                            <span>R$ {ride.valuePerHour.toFixed(0)}/h</span>
                            <span className="text-emerald-300 font-bold">Lucro: {formatBRL(ride.netProfit)}</span>
                          </div>
                        </div>

                        {/* Payment Comparison & Audit Box */}
                        <div className="flex flex-col sm:flex-row lg:flex-col items-start sm:items-center lg:items-end justify-between gap-2 shrink-0 bg-[#073B32]/70 p-3.5 rounded-2xl border border-white/10">
                          <div className="flex items-center gap-4">
                            <div>
                              <div className="text-[10px] uppercase text-white/60 font-bold">Tocou na Tela</div>
                              <div className="text-lg font-black text-white">{formatBRL(ride.offeredValue)}</div>
                            </div>
                            <div className="border-l border-white/15 pl-4">
                              <div className="text-[10px] uppercase text-white/60 font-bold">Valor Recebido</div>
                              <div
                                className={`text-lg font-black ${
                                  hasShortfall
                                    ? "text-red-400"
                                    : ride.receivedValue !== null
                                    ? "text-[#97B304]"
                                    : "text-white/40"
                                }`}
                              >
                                {ride.receivedValue !== null ? formatBRL(ride.receivedValue) : "Não conferido"}
                              </div>
                            </div>
                          </div>

                          {hasShortfall && (
                            <div className="text-xs font-extrabold text-red-300 bg-red-950/80 px-2.5 py-1 rounded-lg border border-red-500/50">
                              ⚠️ Pagou {formatBRL(shortfallAmount)} a menos!
                            </div>
                          )}

                          <div className="flex flex-wrap items-center gap-2 mt-1">
                            <button
                              onClick={() => {
                                setAuditingRideId(ride.id);
                                setAuditReceivedInput(String(ride.receivedValue ?? ride.offeredValue));
                                setAuditNoteInput(ride.paymentAuditNote || "");
                              }}
                              className="px-3 py-1.5 rounded-xl bg-[#7C9303] hover:bg-[#97B304] text-white hover:text-[#04241E] text-xs font-extrabold transition cursor-pointer"
                            >
                              🔍 Conferir Valor Pago
                            </button>

                            <button
                              onClick={() => {
                                setSelectedRideId(ride.id);
                                setActiveTab("evaluate");
                              }}
                              className="px-3 py-1.5 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-bold cursor-pointer"
                            >
                              📸 Avaliar / Street View
                            </button>

                            {hasShortfall && (
                              <button
                                onClick={() => {
                                  const contestText = `Olá Suporte ${ride.platform}, na corrida realizada em ${new Date(
                                    ride.createdAt
                                  ).toLocaleString("pt-BR")} (Origem: ${ride.originAddress} -> Destino: ${
                                    ride.destinationAddress
                                  }, distância total ${ride.totalDistanceKm} km), o valor ofertado na tela ao aceitar foi de ${formatBRL(
                                    ride.offeredValue
                                  )}, porém o valor repassado foi de apenas ${formatBRL(
                                    ride.receivedValue || 0
                                  )} (diferença de ${formatBRL(
                                    shortfallAmount
                                  )}). Solicito a revisão e o ajuste da tarifa.`;
                                  navigator.clipboard.writeText(contestText);
                                  showToast("📋 Texto de contestação copiado para mandar no suporte!");
                                }}
                                className="px-3 py-1.5 rounded-xl bg-red-600 text-white text-xs font-black flex items-center gap-1 cursor-pointer"
                              >
                                <Copy className="w-3.5 h-3.5" />
                                <span>Copiar Reclamação</span>
                              </button>
                            )}
                          </div>
                        </div>
                      </div>

                      {/* Inline Audit Editor if open */}
                      {auditingRideId === ride.id && (
                        <div className="mt-3 pt-3 border-t border-white/15 flex flex-wrap items-end gap-3 bg-black/25 p-3 rounded-xl">
                          <div>
                            <label className="block text-[11px] font-bold text-[#97B304] uppercase">
                              Quanto o App realmente te pagou (R$)?
                            </label>
                            <input
                              type="number"
                              step="0.10"
                              value={auditReceivedInput}
                              onChange={(e) => setAuditReceivedInput(e.target.value)}
                              className="mt-1 px-3 py-1.5 rounded-lg bg-[#04241E] border border-[#97B304] text-white font-black text-sm w-36"
                            />
                          </div>
                          <div className="flex-1 min-w-[200px]">
                            <label className="block text-[11px] font-bold text-white/70 uppercase">
                              Observação (Opcional)
                            </label>
                            <input
                              type="text"
                              value={auditNoteInput}
                              onChange={(e) => setAuditNoteInput(e.target.value)}
                              placeholder="Ex: Pedágio não incluso ou taxa descontada a mais"
                              className="mt-1 w-full px-3 py-1.5 rounded-lg bg-[#04241E] border border-white/15 text-white text-xs"
                            />
                          </div>
                          <div className="flex gap-2">
                            <button
                              onClick={() => handleSavePaymentAudit(ride.id)}
                              className="px-4 py-2 rounded-lg bg-[#97B304] text-[#04241E] font-black text-xs cursor-pointer"
                            >
                              Salvar Conferência
                            </button>
                            <button
                              onClick={() => setAuditingRideId(null)}
                              className="px-3 py-2 rounded-lg bg-white/10 text-white text-xs font-bold cursor-pointer"
                            >
                              Cancelar
                            </button>
                          </div>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        )}

        {/* ===================================================================
            TAB 4: CONFIGURAÇÕES DO VEÍCULO, METAS DO SEMÁFORO & ÁREAS DE RISCO
           =================================================================== */}
        {activeTab === "settings" && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            {/* Vehicle Cost & Semáforo Goals */}
            <div className="lg:col-span-7 bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-5 sm:p-6 shadow-xl space-y-5">
              <div>
                <span className="px-2.5 py-1 rounded-full text-[11px] font-black uppercase bg-[#7C9303]/30 text-[#97B304]">
                  CALIBRAGEM DO SEMÁFORO GO OR NO
                </span>
                <h2 className="text-xl font-black text-white mt-1.5">
                  Custos do Seu Veículo & Metas para Aceitar (GO) ou Recusar (NO)
                </h2>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className="text-xs font-bold text-white/80">Modelo do Veículo</label>
                  <input
                    type="text"
                    value={vConfig.vehicleModel}
                    onChange={(e) => handleSaveVehicleConfig({ vehicleModel: e.target.value })}
                    className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm font-bold"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-white/80">Preço do Combustível (R$/L ou m³)</label>
                  <input
                    type="number"
                    step="0.05"
                    value={vConfig.fuelPricePerUnit}
                    onChange={(e) => handleSaveVehicleConfig({ fuelPricePerUnit: parseFloat(e.target.value) || 5.89 })}
                    className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm font-bold"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-white/80">Consumo Urbano (KM por Litro)</label>
                  <input
                    type="number"
                    step="0.5"
                    value={vConfig.consumptionPerUnit}
                    onChange={(e) => handleSaveVehicleConfig({ consumptionPerUnit: parseFloat(e.target.value) || 10 })}
                    className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm font-bold"
                  />
                </div>
                <div>
                  <label className="text-xs font-bold text-white/80">Manutenção + Pneus + Óleo (R$/km)</label>
                  <input
                    type="number"
                    step="0.02"
                    value={vConfig.maintenanceCostPerKm}
                    onChange={(e) =>
                      handleSaveVehicleConfig({ maintenanceCostPerKm: parseFloat(e.target.value) || 0.2 })
                    }
                    className="w-full mt-1 px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-sm font-bold"
                  />
                </div>
              </div>

              <div className="p-4 rounded-2xl bg-[#04241E] border border-[#97B304]/50">
                <div className="text-xs font-black uppercase text-[#97B304] mb-3">
                  🚦 Regras do Seu Semáforo (Quando mostrar GO Verde vs NO Vermelho)
                </div>
                <div className="grid grid-cols-2 gap-4">
                  <div>
                    <label className="text-xs font-bold text-emerald-300">🟢 GO Verde — Mínimo R$ / KM</label>
                    <input
                      type="number"
                      step="0.05"
                      value={vConfig.greenMinPerKm}
                      onChange={(e) => handleSaveVehicleConfig({ greenMinPerKm: parseFloat(e.target.value) || 2.0 })}
                      className="w-full mt-1 px-3 py-2 rounded-xl bg-[#073B32] border border-emerald-400/50 text-white font-black text-sm"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-emerald-300">🟢 GO Verde — Mínimo R$ / Hora</label>
                    <input
                      type="number"
                      step="1"
                      value={vConfig.greenMinPerHour}
                      onChange={(e) => handleSaveVehicleConfig({ greenMinPerHour: parseFloat(e.target.value) || 40 })}
                      className="w-full mt-1 px-3 py-2 rounded-xl bg-[#073B32] border border-emerald-400/50 text-white font-black text-sm"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-amber-300">🟡 Amarelo — Mínimo R$ / KM</label>
                    <input
                      type="number"
                      step="0.05"
                      value={vConfig.yellowMinPerKm}
                      onChange={(e) => handleSaveVehicleConfig({ yellowMinPerKm: parseFloat(e.target.value) || 1.5 })}
                      className="w-full mt-1 px-3 py-2 rounded-xl bg-[#073B32] border border-amber-400/50 text-white font-black text-sm"
                    />
                  </div>
                  <div>
                    <label className="text-xs font-bold text-amber-300">🟡 Amarelo — Mínimo R$ / Hora</label>
                    <input
                      type="number"
                      step="1"
                      value={vConfig.yellowMinPerHour}
                      onChange={(e) => handleSaveVehicleConfig({ yellowMinPerHour: parseFloat(e.target.value) || 30 })}
                      className="w-full mt-1 px-3 py-2 rounded-xl bg-[#073B32] border border-amber-400/50 text-white font-black text-sm"
                    />
                  </div>
                </div>
              </div>
            </div>

            {/* Risk Zones Manager */}
            <div className="lg:col-span-5 bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-5 sm:p-6 shadow-xl space-y-4">
              <h3 className="text-lg font-black text-white flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-red-400" />
                <span>Radar de Ruas & Bairros de Risco</span>
              </h3>
              <p className="text-xs text-white/75">
                Cadastre bairros ou avenidas perigosas. Sempre que tocar uma corrida para esses locais, o{" "}
                <strong>GoOrNo</strong> dispara um alerta vermelho na hora!
              </p>

              <div className="space-y-2.5">
                <input
                  type="text"
                  placeholder="Ex: Jardim Ângela / Estrada do M'Boi Mirim"
                  value={newRiskStreet}
                  onChange={(e) => setNewRiskStreet(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-xs"
                />
                <input
                  type="text"
                  placeholder="Motivo (Ex: Risco alto à noite / viela sem saída)"
                  value={newRiskReason}
                  onChange={(e) => setNewRiskReason(e.target.value)}
                  className="w-full px-3 py-2 rounded-xl bg-[#04241E] border border-white/15 text-white text-xs"
                />
                <button
                  onClick={() => handleAddRiskZone(newRiskStreet, newRiskReason)}
                  className="w-full py-2.5 rounded-xl bg-red-600 hover:bg-red-500 text-white font-black text-xs flex items-center justify-center gap-1.5 cursor-pointer"
                >
                  <Plus className="w-4 h-4" />
                  <span>Adicionar Área de Risco</span>
                </button>
              </div>

              <div className="space-y-2 pt-2">
                {riskZones.map((rz) => (
                  <div
                    key={rz.id}
                    className="p-3 rounded-xl bg-[#04241E] border border-red-500/40 flex items-center justify-between gap-2"
                  >
                    <div>
                      <div className="text-xs font-black text-red-300">⚠️ {rz.neighborhoodOrStreet}</div>
                      <div className="text-[11px] text-white/65">{rz.reason}</div>
                    </div>
                    <button
                      onClick={() => handleDeleteRiskZone(rz.id)}
                      className="p-1.5 rounded-lg hover:bg-red-500/20 text-red-300 cursor-pointer"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* ===================================================================
            TAB 5: GERAR / INSTALAR APK ANDROID & PERMISSÕES NATIVAS
           =================================================================== */}
        {activeTab === "apk" && (
          <div className="bg-[#073B32] border border-[#7C9303]/50 rounded-3xl p-6 shadow-xl space-y-5">
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-white/10 pb-4">
              <div>
                <span className="px-2.5 py-1 rounded-full text-[11px] font-black uppercase bg-[#97B304] text-[#04241E]">
                  ARQUITETURA ANDROID NATIVA PRONTA (.APK)
                </span>
                <h2 className="text-xl sm:text-2xl font-black text-white mt-2">
                  Módulo Leitor de Tela Android & Janela Flutuante (GoOrNo APK)
                </h2>
                <p className="text-xs sm:text-sm text-white/75 mt-1">
                  O pacote Android do <strong>GoOrNo</strong> já inclui o serviço nativo{" "}
                  <code>GoOrNoAccessibilityService.java</code> configurado para ler automaticamente os cards de chamada
                  da <strong>Uber Driver</strong>, <strong>99 Motorista</strong> e <strong>inDrive</strong>.
                </p>
              </div>
              <a
                href="/GoOrNo-v1.0.0.apk"
                download="GoOrNo-v1.0.0.apk"
                className="px-5 py-3 rounded-2xl bg-[#97B304] hover:bg-[#a9c805] text-[#04241E] font-black text-xs sm:text-sm flex items-center justify-center gap-2 shadow-lg shrink-0"
              >
                <Smartphone className="w-4 h-4" />
                <span>📥 Baixar GoOrNo-v1.0.0.apk</span>
              </a>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <div className="p-4 rounded-2xl bg-[#04241E] border border-[#7C9303]/50 flex flex-col justify-between gap-3">
                <div>
                  <div className="text-xs font-black text-[#97B304] uppercase">Passo 1 no Celular</div>
                  <h3 className="text-base font-black text-white mt-1">Permissão de Acessibilidade</h3>
                  <p className="text-xs text-white/70 mt-1">
                    Ao instalar o APK no Android, o motorista ativa a chave <strong>"GoOrNo Copiloto"</strong> em{" "}
                    <em>Configurações ➔ Acessibilidade</em> para permitir que o app leia o preço, KM e endereço quando a
                    corrida tocar.
                  </p>
                </div>
                <button
                  onClick={() => {
                    if ((window as any).AndroidBridge?.openAccessibilitySettings) {
                      (window as any).AndroidBridge.openAccessibilitySettings();
                    } else {
                      showToast("Abra este botão dentro do APK instalado no Android para ir direto às Configurações!");
                    }
                  }}
                  className="w-full py-2.5 px-3 rounded-xl bg-[#7C9303] hover:bg-[#97B304] text-white hover:text-[#04241E] font-black text-xs transition cursor-pointer"
                >
                  ⚙️ Abrir Acessibilidade no Android
                </button>
              </div>

              <div className="p-4 rounded-2xl bg-[#04241E] border border-[#7C9303]/50 flex flex-col justify-between gap-3">
                <div>
                  <div className="text-xs font-black text-[#97B304] uppercase">Passo 2 no Celular</div>
                  <h3 className="text-base font-black text-white mt-1">Sobrepor a Outros Apps</h3>
                  <p className="text-xs text-white/70 mt-1">
                    Ativa a permissão <strong>"Aparecer sobre outros apps"</strong> para exibir o Semáforo Flutuante{" "}
                    <strong>GO (✓) / NO (−)</strong> por cima da tela da Uber e da 99 sem fechar o mapa.
                  </p>
                </div>
                <button
                  onClick={() => {
                    if ((window as any).AndroidBridge?.openOverlaySettings) {
                      (window as any).AndroidBridge.openOverlaySettings();
                    } else {
                      showToast("Abra este botão dentro do APK instalado no Android para ativar a Sobreposição!");
                    }
                  }}
                  className="w-full py-2.5 px-3 rounded-xl bg-[#7C9303] hover:bg-[#97B304] text-white hover:text-[#04241E] font-black text-xs transition cursor-pointer"
                >
                  🪟 Ativar Janela Flutuante no Android
                </button>
              </div>

              <div className="p-4 rounded-2xl bg-[#04241E] border border-[#7C9303]/50 flex flex-col justify-between gap-3">
                <div>
                  <div className="text-xs font-black text-[#97B304] uppercase">Passo 3 (100% Automático)</div>
                  <h3 className="text-base font-black text-white mt-1">Gravação no Histórico & Street View</h3>
                  <p className="text-xs text-white/70 mt-1">
                    Toda chamada lida na tela vai automaticamente para a nuvem (<code>goorno_cloud_store</code>) e libera o
                    botão de 1 clique para abrir a rua no <strong>Google Street View</strong>!
                  </p>
                </div>
                <button
                  onClick={() => setActiveTab("copilot")}
                  className="w-full py-2.5 px-3 rounded-xl bg-white/10 hover:bg-white/20 text-white font-bold text-xs cursor-pointer"
                >
                  Ir para o Painel do Copiloto ➔
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ===================================================================
            TAB 6: PAINEL ADMIN MASTER & COFRE TIME-MACHINE 7 DIAS
           =================================================================== */}
        {activeTab === "admin" && adminUnlocked && (
          <div className="bg-[#073B32] border-2 border-amber-400 rounded-3xl p-6 shadow-2xl space-y-6">
            <div className="flex items-center justify-between border-b border-white/15 pb-4">
              <div>
                <span className="px-2.5 py-1 rounded-full text-xs font-black uppercase bg-amber-400 text-slate-950">
                  👑 PAINEL ADMIN MASTER • GO OR NO
                </span>
                <h2 className="text-2xl font-black text-white mt-2">
                  Gestão de Motoristas, Assinaturas & Cofre Time-Machine (7 Dias)
                </h2>
              </div>
              <button
                onClick={loadAdminOverview}
                className="px-3.5 py-2 rounded-xl bg-white/10 hover:bg-white/20 text-white text-xs font-bold flex items-center gap-1.5 cursor-pointer"
              >
                <RefreshCw className="w-4 h-4" />
                <span>Atualizar</span>
              </button>
            </div>

            {adminData && (
              <div className="space-y-6">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div className="p-4 rounded-2xl bg-[#04241E] border border-white/10">
                    <h3 className="text-sm font-black text-[#97B304] uppercase mb-3">
                      Motoristas Cadastrados ({adminData.users?.length || 0})
                    </h3>
                    <div className="space-y-2">
                      {(adminData.users || []).map((u: DriverUser) => (
                        <div
                          key={u.id}
                          className="p-3 rounded-xl bg-white/5 flex items-center justify-between text-xs"
                        >
                          <div>
                            <div className="font-black text-white">{u.name}</div>
                            <div className="text-white/65">{u.email} • {u.city}</div>
                          </div>
                          <span className="px-2.5 py-1 rounded-full bg-[#97B304] text-[#04241E] font-black uppercase">
                            {u.plan}
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="p-4 rounded-2xl bg-[#04241E] border border-emerald-500/40">
                    <h3 className="text-sm font-black text-emerald-300 uppercase mb-3 flex items-center gap-2">
                      <ShieldCheck className="w-4 h-4" />
                      <span>Cofre Time-Machine 7 Dias (goorno_vault_snapshots)</span>
                    </h3>
                    <div className="space-y-2">
                      {(adminData.snapshots || []).length === 0 ? (
                        <p className="text-xs text-white/60">
                          O primeiro snapshot automático é gravado na primeira operação do dia.
                        </p>
                      ) : (
                        (adminData.snapshots || []).map((snap: any) => (
                          <div
                            key={snap.slot}
                            className="p-3 rounded-xl bg-white/5 flex items-center justify-between text-xs"
                          >
                            <div>
                              <div className="font-black text-white">{snap.slot}</div>
                              <div className="text-white/60">
                                {snap.updatedAt ? new Date(snap.updatedAt).toLocaleString("pt-BR") : ""} •{" "}
                                {snap.ridesCount} corridas
                              </div>
                            </div>
                            <button
                              onClick={async () => {
                                await fetch("/api/admin/restore-vault", {
                                  method: "POST",
                                  headers: { "Content-Type": "application/json" },
                                  body: JSON.stringify({ slot: snap.slot }),
                                });
                                await fetchState();
                                showToast(`Snapshot ${snap.slot} restaurado com sucesso!`);
                              }}
                              className="px-3 py-1.5 rounded-lg bg-emerald-500 text-slate-950 font-black cursor-pointer"
                            >
                              Restaurar
                            </button>
                          </div>
                        ))
                      )}
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </main>
    </div>
  );
}

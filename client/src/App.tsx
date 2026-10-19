import React, { useState, useEffect, useRef } from 'react';
import { AdminDashboard } from './AdminDashboard';
import { DownloadLandingPage } from './DownloadLandingPage';
import { ScreenshotsShowcase } from './ScreenshotsShowcase';
import {
  Power,
  Sliders,
  History,
  MapPin,
  Navigation,
  Volume2,
  VolumeX,
  Eye,
  CheckCircle2,
  AlertTriangle,
  XCircle,
  Camera,
  ShieldCheck,
  ExternalLink,
  Smartphone,
  Clock,
  DollarSign,
  Download,
  ChevronRight,
  Layers,
  Sparkles,
  Check,
  Star,
  Car,
  Plus,
  Minus,
  Gauge,
  Zap,
  RotateCcw,
  Info,
  ArrowLeft
} from 'lucide-react';

export interface DriverConfig {
  greenPerKm: number;
  yellowPerKm: number;
  greenPerHour: number;
  yellowPerHour: number;
  fuelPricePerLiter: number;
  carKmPerLiter: number;
  maintenancePerKm: number;
  voiceEnabled: boolean;
  overlayOpacity: number;
  overlayDurationSec: number;
  autoStreetViewOnAccept: boolean;
  targetPlatforms: string[];
  minPassengerRating?: number;
  minTripPrice?: number;
  minNetProfit?: number;
  maxPickupKm?: number;
}

export interface RideRecord {
  id: string;
  timestamp: string;
  platform: 'UBER' | '99' | 'INDRIVE' | 'OUTRO';
  category?: string;
  offeredPrice: number;
  receivedPrice: number | null;
  pickupKm: number;
  tripKm: number;
  totalKm: number;
  durationMin: number;
  originAddress: string;
  destinationAddress: string;
  pricePerKm: number;
  pricePerHour: number;
  estimatedCost: number;
  netProfit: number;
  verdict: 'GREEN' | 'YELLOW' | 'RED';
  accepted: boolean;
  paymentStatus: 'PENDING' | 'MATCH' | 'UNDERPAID' | 'OVERPAID';
  notes?: string;
  passengerRating?: number;
  profitPercent?: number;
}

declare global {
  interface Window {
    AndroidBridge?: {
      isNativeAndroid: () => boolean;
      hasOverlayPermission: () => boolean;
      hasAccessibilityPermission: () => boolean;
      openOverlaySettings: () => void;
      openAccessibilitySettings: () => void;
      startCopilotOverlay: () => void;
      stopCopilotOverlay: () => void;
      syncCopilotConfig: (
        greenKm: number,
        yellowKm: number,
        greenHour: number,
        yellowHour: number,
        costPerKm: number,
        opacityPercent: number,
        durationSec: number,
        voiceOn: boolean
      ) => void;
      previewOverlayCard: (
        platform: string,
        price: number,
        totalKm: number,
        durationMin: number,
        origin: string,
        destination: string
      ) => void;
    };
  }
}

const DEFAULT_CONFIG: DriverConfig = {
  greenPerKm: 2.2,
  yellowPerKm: 1.6,
  greenPerHour: 45,
  yellowPerHour: 32,
  fuelPricePerLiter: 5.89,
  carKmPerLiter: 10.5,
  maintenancePerKm: 0.25,
  voiceEnabled: true,
  overlayOpacity: 90,
  overlayDurationSec: 10,
  autoStreetViewOnAccept: true,
  targetPlatforms: ['UBER', '99', 'INDRIVE'],
  minPassengerRating: 4.75,
  minTripPrice: 8.0,
  minNetProfit: 5.0,
  maxPickupKm: 3.5,
};

export function App() {
  const [isAdminView, setIsAdminView] = useState<boolean>(() => {
    return (
      window.location.pathname.startsWith('/admin') ||
      window.location.hash.includes('admin') ||
      window.location.search.includes('admin')
    );
  });

  const [showSimulator, setShowSimulator] = useState<boolean>(() => {
    return window.location.hash.includes('simulator') && !window.location.hash.includes('app');
  });

  const [showDriverApp, setShowDriverApp] = useState<boolean>(() => {
    return (
      window.location.hash.includes('app') ||
      window.location.hash.includes('copiloto') ||
      window.location.search.includes('app')
    );
  });

  useEffect(() => {
    const handleNav = () => {
      setIsAdminView(
        window.location.pathname.startsWith('/admin') ||
        window.location.hash.includes('admin') ||
        window.location.search.includes('admin')
      );
      setShowSimulator(window.location.hash.includes('simulator') && !window.location.hash.includes('app'));
      setShowDriverApp(
        window.location.hash.includes('app') ||
        window.location.hash.includes('copiloto') ||
        window.location.search.includes('app')
      );
    };
    window.addEventListener('hashchange', handleNav);
    window.addEventListener('popstate', handleNav);
    return () => {
      window.removeEventListener('hashchange', handleNav);
      window.removeEventListener('popstate', handleNav);
    };
  }, []);

  useEffect(() => {
    if (showDriverApp || isAdminView) {
      window.scrollTo({ top: 0, behavior: 'instant' });
    }
  }, [showDriverApp, isAdminView]);

  const [activeTab, setActiveTab] = useState<'copilot' | 'overlay' | 'targets' | 'history'>('copilot');
  const [config, setConfig] = useState<DriverConfig>(DEFAULT_CONFIG);
  const [rides, setRides] = useState<RideRecord[]>([]);
  const [copilotActive, setCopilotActive] = useState<boolean>(false);

  // Native Android permissions state
  const [isNative, setIsNative] = useState<boolean>(() => typeof window !== 'undefined' && !!(window as any).AndroidBridge);
  const [hasOverlayPerm, setHasOverlayPerm] = useState<boolean>(false);
  const [hasAccessPerm, setHasAccessPerm] = useState<boolean>(false);

  // Floating overlay preview on screen (works both on Web and Native)
  const [showFloatingPreview, setShowFloatingPreview] = useState<boolean>(true);
  const [floatingCollapsed, setFloatingCollapsed] = useState<boolean>(false);

  // Street View Modal state
  const [streetViewModal, setStreetViewModal] = useState<{
    open: boolean;
    label: string;
    address: string;
  }>({ open: false, label: '', state: '', address: '' } as any);

  // Audit Payment Modal/Inline state
  const [editingPaymentId, setEditingPaymentId] = useState<string | null>(null);
  const [receivedInput, setReceivedInput] = useState<string>('');

  // Screenshot OCR scanning state
  const [isScanningPrint, setIsScanningPrint] = useState<boolean>(false);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  const costPerKm = Number(
    (config.fuelPricePerLiter / Math.max(config.carKmPerLiter, 1) + config.maintenancePerKm).toFixed(2)
  );

  // Check native Android bridge and permissions
  const refreshAndroidPermissions = () => {
    if (window.AndroidBridge && typeof window.AndroidBridge.isNativeAndroid === 'function') {
      setIsNative(true);
      try {
        const ov = window.AndroidBridge.hasOverlayPermission();
        const ac = window.AndroidBridge.hasAccessibilityPermission();
        setHasOverlayPerm(ov);
        setHasAccessPerm(ac);
      } catch (err) {
        console.error(err);
      }
    }
  };

  useEffect(() => {
    refreshAndroidPermissions();
    const timer = setInterval(refreshAndroidPermissions, 2500);
    return () => clearInterval(timer);
  }, []);

  // Load state from backend
  useEffect(() => {
    fetch('/api/state')
      .then((r) => r.json())
      .then((data) => {
        if (data?.config) setConfig({ ...DEFAULT_CONFIG, ...data.config });
        if (Array.isArray(data?.rides)) setRides(data.rides);
      })
      .catch(() => {});
  }, []);

  // Sync config with Android Native Overlay whenever config changes
  useEffect(() => {
    if (window.AndroidBridge && typeof window.AndroidBridge.syncCopilotConfig === 'function') {
      try {
        window.AndroidBridge.syncCopilotConfig(
          config.greenPerKm,
          config.yellowPerKm,
          config.greenPerHour,
          config.yellowPerHour,
          costPerKm,
          config.overlayOpacity,
          config.overlayDurationSec,
          config.voiceEnabled
        );
      } catch (e) {
        console.error(e);
      }
    }
  }, [config, costPerKm]);

  const saveNewConfig = async (updated: DriverConfig) => {
    setConfig(updated);
    try {
      await fetch('/api/config', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updated),
      });
    } catch (e) {
      console.error(e);
    }
  };

  const toggleCopilot = () => {
    if (isNative && window.AndroidBridge) {
      if (!hasOverlayPerm) {
        window.AndroidBridge.openOverlaySettings();
        return;
      }
      if (!hasAccessPerm) {
        window.AndroidBridge.openAccessibilitySettings();
        return;
      }
      if (!copilotActive) {
        window.AndroidBridge.startCopilotOverlay();
        setCopilotActive(true);
      } else {
        window.AndroidBridge.stopCopilotOverlay();
        setCopilotActive(false);
      }
    } else {
      const next = !copilotActive;
      setCopilotActive(next);
      setShowFloatingPreview(true);
      setFloatingCollapsed(false);
    }
  };

  const triggerFloatingDemo = (
    platform: 'UBER' | '99' | 'INDRIVE',
    price: number,
    pickupKm: number,
    tripKm: number,
    durationMin: number,
    originAddress: string,
    destinationAddress: string,
    forcedVerdict?: 'GREEN' | 'YELLOW' | 'RED',
    paxRating: number = 4.96,
    categoryName: string = 'UberX'
  ) => {
    const totalKm = Number((pickupKm + tripKm).toFixed(1));
    const cost = Number((totalKm * costPerKm).toFixed(2));
    const netProfit = Number((price - cost).toFixed(2));
    const pricePerKm = Number((price / Math.max(totalKm, 0.1)).toFixed(2));
    const pricePerHour = Number(((price / Math.max(durationMin, 1)) * 60).toFixed(1));

    let verdict: 'GREEN' | 'YELLOW' | 'RED' = forcedVerdict || 'GREEN';
    if (!forcedVerdict) {
      if (pricePerKm >= config.greenPerKm && pricePerHour >= config.greenPerHour) {
        verdict = 'GREEN';
      } else if (pricePerKm >= config.yellowPerKm && pricePerHour >= config.yellowPerHour) {
        verdict = 'YELLOW';
      } else {
        verdict = 'RED';
      }
    }

    const localRide: RideRecord = {
      id: 'ride-' + Date.now(),
      timestamp: new Date().toISOString(),
      platform,
      category: categoryName,
      offeredPrice: price,
      receivedPrice: null,
      pickupKm,
      tripKm,
      totalKm,
      durationMin,
      originAddress,
      destinationAddress,
      pricePerKm,
      pricePerHour,
      estimatedCost: cost,
      netProfit,
      verdict,
      accepted: verdict !== 'RED',
      paymentStatus: 'PENDING',
      passengerRating: paxRating,
    };

    setRides((prev) => [localRide, ...prev]);
    setShowFloatingPreview(true);
    setFloatingCollapsed(false);

    if (config.voiceEnabled && 'speechSynthesis' in window && !isNative) {
      window.speechSynthesis.cancel();
      const vText =
        verdict === 'GREEN'
          ? `Corrida ótima! Aceitar. ${pricePerKm.toFixed(2).replace('.', ',')} reais por quilômetro.`
          : verdict === 'YELLOW'
          ? `Atenção. Corrida média. ${pricePerKm.toFixed(2).replace('.', ',')} reais por quilômetro.`
          : `Recusar! Prejuízo. Apenas ${pricePerKm.toFixed(2).replace('.', ',')} por quilômetro.`;
      const utter = new SpeechSynthesisUtterance(vText);
      utter.lang = 'pt-BR';
      window.speechSynthesis.speak(utter);
    }

    if (isNative && window.AndroidBridge) {
      window.AndroidBridge.previewOverlayCard(
        platform,
        price,
        totalKm,
        durationMin,
        originAddress,
        destinationAddress
      );
    }

    // Persist to backend if online
    fetch('/api/rides/evaluate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        platform,
        offeredPrice: price,
        pickupKm,
        tripKm,
        durationMin,
        originAddress,
        destinationAddress,
        accepted: verdict !== 'RED',
      }),
    }).catch(() => {});
  };

  const handlePrintUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setIsScanningPrint(true);
    try {
      const reader = new FileReader();
      reader.onload = async () => {
        const base64 = String(reader.result || '');
        const res = await fetch('/api/rides/scan-screenshot', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ imageBase64: base64 }),
        });
        const data = await res.json();
        if (data?.ride) {
          setRides((prev) => [data.ride, ...prev]);
          setShowFloatingPreview(true);
          setFloatingCollapsed(false);
          if (isNative && window.AndroidBridge) {
            window.AndroidBridge.previewOverlayCard(
              data.ride.platform,
              data.ride.offeredPrice,
              data.ride.totalKm,
              data.ride.durationMin,
              data.ride.originAddress,
              data.ride.destinationAddress
            );
          }
        }
        setIsScanningPrint(false);
      };
      reader.readAsDataURL(file);
    } catch (err) {
      setIsScanningPrint(false);
    }
  };

  const handleVerifyPayment = async (rideId: string) => {
    const val = parseFloat(receivedInput.replace(',', '.'));
    if (isNaN(val) || val < 0) return;
    try {
      const res = await fetch(`/api/rides/${rideId}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ receivedPrice: val, accepted: true }),
      });
      const data = await res.json();
      if (data?.ride) {
        setRides((prev) => prev.map((r) => (r.id === rideId ? data.ride : r)));
      }
      setEditingPaymentId(null);
      setReceivedInput('');
    } catch (e) {
      console.error(e);
    }
  };

  const openStreetView = (label: string, address: string) => {
    setStreetViewModal({ open: true, label, address });
  };

  const currentRide: RideRecord = rides[0] || {
    id: 'demo-1',
    timestamp: new Date().toISOString(),
    platform: 'UBER',
    category: 'UberX',
    offeredPrice: 28.5,
    receivedPrice: null,
    pickupKm: 1.4,
    tripKm: 8.6,
    totalKm: 10.0,
    durationMin: 19,
    originAddress: 'Av. Paulista, 1578 - Bela Vista, São Paulo',
    destinationAddress: 'Rua Funchal, 418 - Vila Olímpia, São Paulo',
    pricePerKm: 2.85,
    pricePerHour: 90.0,
    estimatedCost: 8.1,
    netProfit: 20.4,
    verdict: 'GREEN',
    accepted: true,
    paymentStatus: 'PENDING',
    passengerRating: 4.96,
  };

  const getVerdictColors = (verdict: 'GREEN' | 'YELLOW' | 'RED') => {
    if (verdict === 'GREEN') {
      return {
        bg: '#16a34a',
        border: '#4ade80',
        badge: '✔ ACEITAR',
        badgeFull: '✔ ACEITAR · ÓTIMA',
        text: '#ffffff',
        glow: 'rgba(22, 163, 74, 0.45)',
      };
    }
    if (verdict === 'YELLOW') {
      return {
        bg: '#d97706',
        border: '#fbbf24',
        badge: '⚠ ATENÇÃO',
        badgeFull: '⚠ ATENÇÃO · AVALIAR',
        text: '#ffffff',
        glow: 'rgba(217, 119, 6, 0.45)',
      };
    }
    return {
      bg: '#dc2626',
      border: '#f87171',
      badge: '✖ RECUSAR',
      badgeFull: '✖ RECUSAR · PREJUÍZO',
      text: '#ffffff',
      glow: 'rgba(220, 38, 38, 0.45)',
    };
  };

  const vStyle = getVerdictColors(currentRide.verdict);

  // Pillar 4 Health Indicators:
  const kmIndicator: 'green' | 'yellow' | 'red' =
    currentRide.pricePerKm >= config.greenPerKm
      ? 'green'
      : currentRide.pricePerKm >= config.yellowPerKm
      ? 'yellow'
      : 'red';

  const hourIndicator: 'green' | 'yellow' | 'red' =
    currentRide.pricePerHour >= config.greenPerHour
      ? 'green'
      : currentRide.pricePerHour >= config.yellowPerHour
      ? 'yellow'
      : 'red';

  const profitPercent = Math.max(
    0,
    Math.min(100, Math.round(((currentRide.netProfit || 0) / Math.max(currentRide.offeredPrice, 1)) * 100))
  );

  const profitIndicator: 'green' | 'yellow' | 'red' =
    profitPercent >= 65 ? 'green' : profitPercent >= 45 ? 'yellow' : 'red';

  const paxRating = currentRide.passengerRating || 4.96;
  const ratingIndicator: 'green' | 'yellow' | 'red' =
    paxRating >= 4.85
      ? 'green'
      : paxRating >= (config.minPassengerRating || 4.75)
      ? 'yellow'
      : 'red';

  if (isAdminView) {
    return (
      <AdminDashboard
        onBackToApp={() => {
          window.location.hash = '';
          setIsAdminView(false);
        }}
      />
    );
  }

  if (showSimulator) {
    return (
      <ScreenshotsShowcase
        onBackToLanding={() => {
          window.location.hash = '';
          setShowSimulator(false);
        }}
      />
    );
  }

  if (!isNative && !showDriverApp) {
    return (
      <DownloadLandingPage
        onOpenSimulator={() => {
          window.location.hash = 'app';
          setShowDriverApp(true);
        }}
        onOpenAdmin={() => {
          window.location.hash = 'admin';
          setIsAdminView(true);
        }}
      />
    );
  }

  return (
    <div className="min-h-screen bg-[#031B17] text-white flex justify-center select-none font-sans">
      {/* Mobile-First Container (Identical proportions to GigU / JetMax / JustoApp) */}
      <div className="w-full max-w-md min-h-screen bg-[#04241E] flex flex-col justify-between relative shadow-2xl border-x border-[#0D4A3E]/60 pb-20">
        
        {/* TOP COMPACT APP HEADER */}
        <header className="px-4 pt-3 pb-2.5 bg-[#073B32] border-b border-[#115E50]/60 flex items-center justify-between sticky top-0 z-30">
          <div className="flex items-center gap-2.5">
            <img
              src="/logo-goorno.png"
              alt="GoOrNo"
              className="w-9 h-9 rounded-xl object-contain bg-[#04241E] p-0.5 border border-[#97B304]/50"
            />
            <div>
              <div className="flex items-center gap-1.5">
                <span className="font-black text-base tracking-tight text-white">GoOrNo</span>
                <span className="text-[10px] font-extrabold uppercase px-1.5 py-0.5 rounded bg-[#97B304] text-[#04241E]">
                  Copiloto
                </span>
              </div>
              <p className="text-[11px] text-emerald-200/80 leading-none mt-0.5">
                Leitor de Tela Uber · 99 · inDrive
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            {!isNative && (
              <button
                onClick={() => {
                  window.location.hash = '';
                  setShowSimulator(false);
                }}
                className="px-2 py-1.5 rounded-xl border border-[#97B304]/40 bg-[#04241E] hover:bg-[#0D4A3E] text-[11px] font-bold text-[#d9f56a] transition flex items-center gap-1 shadow-sm"
                title="Voltar para a Página de Download do APK"
              >
                <Download className="w-3.5 h-3.5" />
                <span className="hidden xs:inline">Baixar APK</span>
              </button>
            )}

            {/* Quick Admin Access */}
            <button
              onClick={() => {
                window.location.hash = 'admin';
                setIsAdminView(true);
              }}
              className="p-2 rounded-xl border border-white/10 bg-[#04241E] hover:bg-[#0D4A3E] text-slate-300 hover:text-white transition"
              title="Painel Admin Master"
            >
              <ShieldCheck className="w-4 h-4 text-emerald-400" />
            </button>

            {/* Quick Voice Toggle */}
            <button
              onClick={() => saveNewConfig({ ...config, voiceEnabled: !config.voiceEnabled })}
              className={`p-2 rounded-xl border transition ${
                config.voiceEnabled
                  ? 'bg-[#0D4A3E] border-[#97B304]/60 text-[#B8D906]'
                  : 'bg-[#04241E] border-white/10 text-white/40'
              }`}
              title="Voz do Copiloto"
            >
              {config.voiceEnabled ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
            </button>

            {/* Quick Opacity Pill */}
            <button
              onClick={() => setActiveTab('overlay')}
              className="px-2.5 py-1.5 rounded-xl bg-[#04241E] border border-white/15 text-xs font-bold text-emerald-200 flex items-center gap-1"
            >
              <Eye className="w-3.5 h-3.5 text-[#97B304]" />
              {config.overlayOpacity}%
            </button>
          </div>
        </header>

        {/* FLOATING OVERLAY WINDOW (JANELA FLUTUANTE REAL COM ERGONOMIA MODERNA DE 4 PILARES) */}
        {showFloatingPreview && (
          <div
            className="fixed top-14 left-1/2 -translate-x-1/2 z-40 w-[94%] max-w-[400px] transition-all duration-200"
            style={{
              opacity: Math.max(0.25, Math.min(1, config.overlayOpacity / 100)),
            }}
          >
            {floatingCollapsed ? (
              /* Pílula Flutuante Minimizada */
              <div
                className="flex items-center justify-between bg-[#071f1a]/95 border-2 rounded-full px-3.5 py-2 shadow-2xl backdrop-blur-md"
                style={{ borderColor: vStyle.border }}
              >
                <button
                  onClick={() => setFloatingCollapsed(false)}
                  className="flex items-center gap-2 text-left flex-1 min-w-0"
                >
                  <span
                    className="px-2.5 py-0.5 rounded-full text-[10px] font-black text-white shrink-0 shadow-sm"
                    style={{ backgroundColor: vStyle.bg }}
                  >
                    {vStyle.badge}
                  </span>
                  <span className="text-xs font-black tracking-wide text-white truncate">
                    {currentRide.category || currentRide.platform} · R$ {currentRide.pricePerKm.toFixed(2).replace('.', ',')}/km · R$ {Math.round(currentRide.pricePerHour)}/h
                  </span>
                </button>
                <button
                  onClick={() => setFloatingCollapsed(false)}
                  className="text-[11px] font-bold text-[#071d19] bg-[#d9ff4f] hover:bg-white px-2.5 py-0.5 rounded-full ml-2 shrink-0 transition"
                >
                  Abrir
                </button>
              </div>
            ) : (
              /* Card Flutuante Completo com Balão de Veredito + 4 Pilares */
              <div className="relative pt-2">
                {/* Balão de Veredito (Speech Bubble Badge) no topo-esquerdo */}
                <div className="relative mb-[-6px] ml-3 z-10 inline-flex items-center">
                  <div
                    className="px-3 py-1 rounded-full text-[11px] font-black tracking-wider text-white shadow-lg flex items-center gap-1.5"
                    style={{
                      backgroundColor: vStyle.bg,
                      boxShadow: `0 4px 14px ${vStyle.glow}`,
                    }}
                  >
                    {vStyle.badge}
                  </div>
                  {/* Ponta do balão apontando para o card */}
                  <div
                    className="absolute left-4 -bottom-1.5 w-0 h-0 border-x-4 border-x-transparent border-t-[6px]"
                    style={{ borderTopColor: vStyle.bg }}
                  />
                </div>

                {/* Card Principal */}
                <div
                  className="rounded-2xl p-3 shadow-2xl border-2 text-white backdrop-blur-md"
                  style={{
                    backgroundColor: '#071f1a',
                    borderColor: vStyle.border,
                  }}
                >
                  {/* Top Strip: Categoria · Tempo · Distância + Controles Rápidos */}
                  <div className="flex items-center justify-between pb-2 border-b border-white/10 text-xs">
                    <div className="flex items-center gap-1.5 font-bold">
                      <span className="px-2 py-0.5 rounded bg-black/60 text-[10px] font-black tracking-wider text-emerald-300 uppercase">
                        {currentRide.category || currentRide.platform}
                      </span>
                      <span className="text-white/60">·</span>
                      <span className="text-white font-semibold">{currentRide.durationMin} min</span>
                      <span className="text-white/60">·</span>
                      <span className="text-white font-semibold">{currentRide.totalKm} km</span>
                    </div>

                    <div className="flex items-center gap-1">
                      {/* Botão 360° */}
                      <button
                        onClick={() => openStreetView('Local de Embarque', currentRide.originAddress)}
                        className="p-1 rounded-lg bg-black/40 hover:bg-black/70 text-[#d9ff4f] transition"
                        title="Ver Street View 360° da Rua"
                      >
                        <Eye className="w-3.5 h-3.5" />
                      </button>
                      {/* Minimizar */}
                      <button
                        onClick={() => setFloatingCollapsed(true)}
                        className="px-2 py-0.5 rounded-lg bg-black/40 hover:bg-black/70 text-white/70 hover:text-white text-xs font-bold transition"
                        title="Minimizar para Pílula"
                      >
                        –
                      </button>
                      {/* Fechar */}
                      <button
                        onClick={() => setShowFloatingPreview(false)}
                        className="px-2 py-0.5 rounded-lg bg-black/40 hover:bg-black/70 text-white/70 hover:text-white text-xs font-bold transition"
                        title="Fechar Janela"
                      >
                        ✕
                      </button>
                    </div>
                  </div>

                  {/* 4 PILARES DE DECISÃO INSTANTÂNEA */}
                  <div className="grid grid-cols-4 gap-1.5 my-2.5">
                    {/* Pilar 1: R$/km */}
                    <div className="bg-black/45 rounded-xl p-2 flex flex-col items-center justify-center border border-white/10">
                      <div className="flex items-center gap-1 mb-1">
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            kmIndicator === 'green'
                              ? 'bg-emerald-400'
                              : kmIndicator === 'yellow'
                              ? 'bg-amber-400'
                              : 'bg-red-400'
                          }`}
                        />
                        <span className="text-[10px] font-bold text-white/70 uppercase tracking-tight">R$/km</span>
                      </div>
                      <div className="text-base font-black text-white leading-none">
                        R$ {currentRide.pricePerKm.toFixed(2).replace('.', ',')}
                      </div>
                    </div>

                    {/* Pilar 2: R$/Hora */}
                    <div className="bg-black/45 rounded-xl p-2 flex flex-col items-center justify-center border border-white/10">
                      <div className="flex items-center gap-1 mb-1">
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            hourIndicator === 'green'
                              ? 'bg-emerald-400'
                              : hourIndicator === 'yellow'
                              ? 'bg-amber-400'
                              : 'bg-red-400'
                          }`}
                        />
                        <span className="text-[10px] font-bold text-white/70 uppercase tracking-tight">R$/Hora</span>
                      </div>
                      <div className="text-base font-black text-white leading-none">
                        R$ {Math.round(currentRide.pricePerHour)}
                      </div>
                    </div>

                    {/* Pilar 3: Lucro (%) */}
                    <div className="bg-black/45 rounded-xl p-2 flex flex-col items-center justify-center border border-white/10">
                      <div className="flex items-center gap-1 mb-1">
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            profitIndicator === 'green'
                              ? 'bg-emerald-400'
                              : profitIndicator === 'yellow'
                              ? 'bg-amber-400'
                              : 'bg-red-400'
                          }`}
                        />
                        <span className="text-[10px] font-bold text-white/70 uppercase tracking-tight">Lucro (%)</span>
                      </div>
                      <div className="text-base font-black text-[#d9ff4f] leading-none">
                        {profitPercent}%
                      </div>
                    </div>

                    {/* Pilar 4: Nota Pax */}
                    <div className="bg-black/45 rounded-xl p-2 flex flex-col items-center justify-center border border-white/10">
                      <div className="flex items-center gap-1 mb-1">
                        <span
                          className={`w-1.5 h-1.5 rounded-full ${
                            ratingIndicator === 'green'
                              ? 'bg-emerald-400'
                              : ratingIndicator === 'yellow'
                              ? 'bg-amber-400'
                              : 'bg-red-400'
                          }`}
                        />
                        <span className="text-[10px] font-bold text-white/70 uppercase tracking-tight">Nota Pax</span>
                      </div>
                      <div className="text-base font-black text-white leading-none flex items-center gap-0.5">
                        <span>{paxRating.toFixed(2).replace('.', ',')}</span>
                        <Star className="w-2.5 h-2.5 fill-amber-400 text-amber-400" />
                      </div>
                    </div>
                  </div>

                  {/* Resumo da Corrida & Lucro Líquido */}
                  <div className="flex items-center justify-between text-[11px] text-white/85 px-2 py-1.5 bg-black/30 rounded-lg mb-2 border border-white/5">
                    <span>
                      Oferta: <strong className="text-white">R$ {currentRide.offeredPrice.toFixed(2).replace('.', ',')}</strong>
                    </span>
                    <span className="text-white/40">|</span>
                    <span>
                      Lucro Líq: <strong className="text-[#d9ff4f]">R$ {currentRide.netProfit.toFixed(2).replace('.', ',')}</strong>
                    </span>
                    <span className="text-white/40">|</span>
                    <span>
                      Custo: <strong className="text-red-300">R$ {currentRide.estimatedCost.toFixed(2).replace('.', ',')}</strong>
                    </span>
                  </div>

                  {/* Botões 1-Toque Street View 360° */}
                  <div className="grid grid-cols-2 gap-1.5">
                    <button
                      onClick={() => openStreetView('Local de Embarque (Origem)', currentRide.originAddress)}
                      className="flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-lg bg-[#0e3b33] hover:bg-[#134d43] border border-[#d9ff4f]/30 text-[11px] font-bold text-white transition truncate"
                    >
                      <MapPin className="w-3.5 h-3.5 text-[#d9ff4f] shrink-0" />
                      <span className="truncate">Ver Origem (360°)</span>
                    </button>
                    <button
                      onClick={() => openStreetView('Local de Desembarque (Destino)', currentRide.destinationAddress)}
                      className="flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-lg bg-[#0e3b33] hover:bg-[#134d43] border border-[#d9ff4f]/30 text-[11px] font-bold text-white transition truncate"
                    >
                      <Navigation className="w-3.5 h-3.5 text-emerald-300 shrink-0" />
                      <span className="truncate">Ver Destino (360°)</span>
                    </button>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}

        {/* MAIN TAB CONTENT */}
        <main className="flex-1 px-4 pt-4 pb-6 space-y-4">
          
          {/* ==========================================
              TAB 1: INÍCIO / COPILOTO (GIGU / JETMAX STYLE)
             ========================================== */}
          {activeTab === 'copilot' && (
            <>
              {/* 1. BIG POWER SWITCH CARD */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 shadow-lg">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[11px] font-bold uppercase tracking-wider text-[#B8D906]">
                      Status do Leitor de Tela
                    </span>
                    <h2 className="text-lg font-black text-white mt-0.5">
                      {copilotActive ? 'COPILOTO ATIVO NA TELA' : 'COPILOTO DESLIGADO'}
                    </h2>
                    <p className="text-xs text-emerald-100/75 mt-0.5">
                      {copilotActive
                        ? 'Lendo chamadas da Uber, 99 e inDrive automaticamente'
                        : 'Toque no botão ao lado para abrir por cima da Uber/99'}
                    </p>
                  </div>

                  <button
                    onClick={toggleCopilot}
                    className={`w-16 h-16 rounded-2xl flex flex-col items-center justify-center transition-all shadow-xl border-2 shrink-0 ${
                      copilotActive
                        ? 'bg-[#97B304] border-white text-[#04241E] scale-105'
                        : 'bg-[#04241E] border-[#97B304]/60 text-[#B8D906] hover:bg-[#093129]'
                    }`}
                  >
                    <Power className="w-7 h-7 stroke-[2.5]" />
                    <span className="text-[10px] font-black mt-0.5">
                      {copilotActive ? 'ON' : 'LIGAR'}
                    </span>
                  </button>
                </div>

                {/* Quick Controls for Floating Window */}
                <div className="mt-3 pt-3 border-t border-white/10 flex items-center justify-between gap-2">
                  <button
                    onClick={() => {
                      setShowFloatingPreview(true);
                      setFloatingCollapsed(false);
                      if (isNative && window.AndroidBridge) {
                        window.AndroidBridge.previewOverlayCard(
                          currentRide.platform,
                          currentRide.offeredPrice,
                          currentRide.totalKm,
                          currentRide.durationMin,
                          currentRide.originAddress,
                          currentRide.destinationAddress
                        );
                      }
                    }}
                    className="flex-1 py-2 px-3 rounded-xl bg-[#04241E] border border-[#97B304]/50 text-xs font-bold text-[#B8D906] flex items-center justify-center gap-1.5"
                  >
                    <Layers className="w-4 h-4" />
                    Mostrar Janela Flutuante
                  </button>

                  <button
                    onClick={() => fileInputRef.current?.click()}
                    disabled={isScanningPrint}
                    className="py-2 px-3 rounded-xl bg-[#0D4A3E] border border-white/15 text-xs font-bold text-white flex items-center justify-center gap-1.5"
                  >
                    <Camera className="w-4 h-4 text-[#B8D906]" />
                    {isScanningPrint ? 'Lendo...' : 'Ler Print'}
                  </button>
                  <input
                    ref={fileInputRef}
                    type="file"
                    accept="image/*"
                    className="hidden"
                    onChange={handlePrintUpload}
                  />
                </div>
              </div>

              {/* 2. ANDROID PERMISSIONS CHECKLIST (2 TAPS LIKE GIGU/JETMAX) */}
              <div className="rounded-2xl bg-[#073B32]/90 border border-white/10 p-3.5 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-extrabold uppercase tracking-wider text-emerald-200">
                    Permissões para Abrir Sobre Uber e 99
                  </span>
                  <span className="text-[11px] font-bold text-[#B8D906]">
                    {isNative ? 'App Android Nativo' : 'Modo Web / APK'}
                  </span>
                </div>

                {/* Permissão 1: Sobrepor a outros apps */}
                <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#04241E] border border-white/10">
                  <div className="flex items-center gap-2.5">
                    <div
                      className={`w-7 h-7 rounded-lg flex items-center justify-center font-black text-xs ${
                        hasOverlayPerm || copilotActive
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                          : 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                      }`}
                    >
                      1
                    </div>
                    <div>
                      <div className="text-xs font-bold text-white">Janela Flutuante (Sobrepor Apps)</div>
                      <div className="text-[11px] text-white/60">
                        Abre o card transparente por cima da Uber e 99
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      if (isNative && window.AndroidBridge) {
                        window.AndroidBridge.openOverlaySettings();
                      } else {
                        setShowFloatingPreview(true);
                        setFloatingCollapsed(false);
                      }
                    }}
                    className="px-3 py-1.5 rounded-lg bg-[#97B304] text-[#04241E] text-xs font-black shrink-0"
                  >
                    {hasOverlayPerm ? 'Ativo ✓' : 'Ativar'}
                  </button>
                </div>

                {/* Permissão 2: Acessibilidade Leitor de Tela */}
                <div className="flex items-center justify-between p-2.5 rounded-xl bg-[#04241E] border border-white/10">
                  <div className="flex items-center gap-2.5">
                    <div
                      className={`w-7 h-7 rounded-lg flex items-center justify-center font-black text-xs ${
                        hasAccessPerm || copilotActive
                          ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/40'
                          : 'bg-amber-500/20 text-amber-300 border border-amber-500/40'
                      }`}
                    >
                      2
                    </div>
                    <div>
                      <div className="text-xs font-bold text-white">Leitor de Tela (Acessibilidade)</div>
                      <div className="text-[11px] text-white/60">
                        Lê R$, KM, minutos e endereços automaticamente
                      </div>
                    </div>
                  </div>
                  <button
                    onClick={() => {
                      if (isNative && window.AndroidBridge) {
                        window.AndroidBridge.openAccessibilitySettings();
                      } else {
                        setCopilotActive(true);
                        setShowFloatingPreview(true);
                      }
                    }}
                    className="px-3 py-1.5 rounded-lg bg-[#97B304] text-[#04241E] text-xs font-black shrink-0"
                  >
                    {hasAccessPerm ? 'Ativo ✓' : 'Ativar'}
                  </button>
                </div>
              </div>

              {/* 3. CORRIDA ACEITA / ÚLTIMA CAPTURADA -> STREET VIEW EM 1 TOQUE */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[10px] font-black uppercase tracking-wider text-[#B8D906]">
                      Avaliação de Segurança Pós-Aceite
                    </span>
                    <h3 className="text-sm font-black text-white">
                      Última Corrida Lida · Toque no Endereço p/ Street View
                    </h3>
                  </div>
                  <span
                    className="px-2 py-0.5 rounded text-[11px] font-black"
                    style={{ backgroundColor: vStyle.bg }}
                  >
                    R$ {currentRide.offeredPrice.toFixed(2)}
                  </span>
                </div>

                {/* Endereço de Origem (Clicável -> Street View) */}
                <button
                  onClick={() => openStreetView('Embarque (Origem)', currentRide.originAddress)}
                  className="w-full text-left p-3 rounded-xl bg-[#04241E] hover:bg-[#063028] border border-[#97B304]/40 transition flex items-center justify-between gap-2 group"
                >
                  <div className="flex items-start gap-2.5 min-w-0">
                    <MapPin className="w-4 h-4 text-[#B8D906] shrink-0 mt-0.5" />
                    <div className="min-w-0">
                      <div className="text-[10px] font-bold uppercase text-[#B8D906]">
                        Embarque · {currentRide.pickupKm} km até o passageiro
                      </div>
                      <div className="text-xs font-bold text-white truncate">
                        {currentRide.originAddress}
                      </div>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 rounded-lg bg-[#073B32] border border-[#97B304]/50 text-[11px] font-black text-[#B8D906] shrink-0 flex items-center gap-1">
                    Street View
                    <ExternalLink className="w-3 h-3" />
                  </span>
                </button>

                {/* Endereço de Destino (Clicável -> Street View) */}
                <button
                  onClick={() => openStreetView('Desembarque (Destino)', currentRide.destinationAddress)}
                  className="w-full text-left p-3 rounded-xl bg-[#04241E] hover:bg-[#063028] border border-emerald-400/30 transition flex items-center justify-between gap-2 group"
                >
                  <div className="flex items-start gap-2.5 min-w-0">
                    <Navigation className="w-4 h-4 text-emerald-400 shrink-0 mt-0.5" />
                    <div className="min-w-0">
                      <div className="text-[10px] font-bold uppercase text-emerald-300">
                        Destino Final · Viagem de {currentRide.tripKm} km ({currentRide.durationMin} min)
                      </div>
                      <div className="text-xs font-bold text-white truncate">
                        {currentRide.destinationAddress}
                      </div>
                    </div>
                  </div>
                  <span className="px-2.5 py-1 rounded-lg bg-[#073B32] border border-emerald-400/40 text-[11px] font-black text-emerald-300 shrink-0 flex items-center gap-1">
                    Street View
                    <ExternalLink className="w-3 h-3" />
                  </span>
                </button>

                {/* Testar exemplos reais de corrida (1 toque) */}
                <div className="pt-1">
                  <div className="text-[11px] font-bold text-white/60 mb-1.5">
                    Testar leitura da janela flutuante com chamadas reais:
                  </div>
                  <div className="grid grid-cols-3 gap-1.5">
                    <button
                      onClick={() =>
                        triggerFloatingDemo(
                          'UBER',
                          32.9,
                          1.2,
                          8.8,
                          18,
                          'Av. Paulista, 1578 - Bela Vista, São Paulo',
                          'Rua Funchal, 418 - Vila Olímpia, São Paulo'
                        )
                      }
                      className="py-2 px-2 rounded-xl bg-emerald-600/25 border border-emerald-400/50 text-[11px] font-black text-emerald-200 hover:bg-emerald-600/40"
                    >
                      🟢 Uber Boa
                    </button>
                    <button
                      onClick={() =>
                        triggerFloatingDemo(
                          '99',
                          17.5,
                          2.1,
                          7.9,
                          22,
                          'Rua da Consolação, 2200 - São Paulo',
                          'Terminal Barra Funda - São Paulo'
                        )
                      }
                      className="py-2 px-2 rounded-xl bg-amber-500/25 border border-amber-400/50 text-[11px] font-black text-amber-200 hover:bg-amber-500/40"
                    >
                      🟡 99 Média
                    </button>
                    <button
                      onClick={() =>
                        triggerFloatingDemo(
                          'UBER',
                          11.2,
                          3.4,
                          6.6,
                          24,
                          'Av. Brasil, 4500 - Rio de Janeiro',
                          'Estrada do Galeão, 1200 - Rio de Janeiro'
                        )
                      }
                      className="py-2 px-2 rounded-xl bg-red-600/25 border border-red-400/50 text-[11px] font-black text-red-200 hover:bg-red-600/40"
                    >
                      🔴 Uber Ruim
                    </button>
                  </div>
                </div>
              </div>

              {/* 4. DOWNLOAD APK NATIVO + DICA PLAY PROTECT */}
              {!isNative && (
                <div className="rounded-2xl bg-[#04241E] border border-[#97B304]/40 p-3.5 space-y-2">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <Smartphone className="w-4 h-4 text-[#B8D906]" />
                      <span className="text-xs font-black text-white">
                        Instalar APK Android Nativo (v3.3.24)
                      </span>
                    </div>
                    <a
                      href="/download/apk"
                      download="GoOrNo-v3.3.24.apk"
                      className="px-3 py-1.5 rounded-xl bg-[#97B304] text-[#04241E] text-xs font-black flex items-center gap-1"
                    >
                      <Download className="w-3.5 h-3.5" />
                      Baixar APK
                    </a>
                  </div>
                  <p className="text-[11px] text-white/70 leading-relaxed">
                    <strong>Dica se o Google Play Protect avisar:</strong> Como o GoOrNo usa{' '}
                    <em>Leitor de Tela + Janela Flutuante sobre a Uber/99</em> (igual ao GigU e JetMax), toque em{' '}
                    <strong className="text-[#B8D906]">"Mais detalhes" → "Instalar assim mesmo"</strong> ou pause a verificação na Play Store → Perfil → Play Protect → ⚙️.
                  </p>
                </div>
              )}
            </>
          )}

          {/* ==========================================================
              TAB 2: JANELA FLUTUANTE & TRANSPARÊNCIA (OPACITY CONTROL)
             ========================================================== */}
          {activeTab === 'overlay' && (
            <div className="space-y-4">
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-4">
                <div>
                  <span className="text-[11px] font-black uppercase tracking-wider text-[#B8D906]">
                    Configuração Visual Sobre Uber e 99
                  </span>
                  <h2 className="text-base font-black text-white mt-0.5">
                    Transparência da Janela Flutuante
                  </h2>
                  <p className="text-xs text-white/70 mt-0.5">
                    Deixe o card mais transparente para enxergar o mapa da Uber e da 99 por baixo sem atrapalhar o toque.
                  </p>
                </div>

                {/* SLIDER DE TRANSPARÊNCIA / OPACIDADE (25% a 100%) */}
                <div className="p-3.5 rounded-xl bg-[#04241E] border border-[#97B304]/40 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-white flex items-center gap-1.5">
                      <Eye className="w-4 h-4 text-[#B8D906]" />
                      Opacidade / Transparência da Janela
                    </span>
                    <span className="text-sm font-black text-[#B8D906]">
                      {config.overlayOpacity}%
                    </span>
                  </div>

                  <input
                    type="range"
                    min={25}
                    max={100}
                    step={5}
                    value={config.overlayOpacity}
                    onChange={(e) => {
                      const val = Number(e.target.value);
                      setShowFloatingPreview(true);
                      setFloatingCollapsed(false);
                      saveNewConfig({ ...config, overlayOpacity: val });
                    }}
                    className="w-full accent-[#97B304] cursor-pointer h-2 rounded-lg"
                  />

                  <div className="flex justify-between text-[10px] font-bold text-white/50">
                    <span>25% (Bem Transparente)</span>
                    <span>60% (Médio)</span>
                    <span>100% (Sólido)</span>
                  </div>

                  {/* Quick Preset Buttons */}
                  <div className="grid grid-cols-4 gap-1.5 pt-2">
                    {[35, 55, 75, 95].map((pct) => (
                      <button
                        key={pct}
                        onClick={() => {
                          setShowFloatingPreview(true);
                          setFloatingCollapsed(false);
                          saveNewConfig({ ...config, overlayOpacity: pct });
                        }}
                        className={`py-1.5 rounded-lg text-xs font-black border transition ${
                          config.overlayOpacity === pct
                            ? 'bg-[#97B304] text-[#04241E] border-white'
                            : 'bg-[#073B32] text-white/80 border-white/10'
                        }`}
                      >
                        {pct}%
                      </button>
                    ))}
                  </div>
                </div>

                {/* TEMPO DO CARD NA TELA ANTES DE MINIMIZAR SOZINHO */}
                <div className="p-3.5 rounded-xl bg-[#04241E] border border-white/10 space-y-2">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-bold text-white flex items-center gap-1.5">
                      <Clock className="w-4 h-4 text-emerald-300" />
                      Tempo Aberto na Tela (Auto-Minimizar)
                    </span>
                    <span className="text-sm font-black text-emerald-300">
                      {config.overlayDurationSec} seg
                    </span>
                  </div>
                  <div className="grid grid-cols-4 gap-1.5">
                    {[5, 8, 10, 15].map((sec) => (
                      <button
                        key={sec}
                        onClick={() => saveNewConfig({ ...config, overlayDurationSec: sec })}
                        className={`py-1.5 rounded-lg text-xs font-black border transition ${
                          config.overlayDurationSec === sec
                            ? 'bg-emerald-500 text-[#04241E] border-white'
                            : 'bg-[#073B32] text-white/80 border-white/10'
                        }`}
                      >
                        {sec} seg
                      </button>
                    ))}
                  </div>
                </div>

                {/* VOZ DO COPILOTO (NARRAÇÃO R$/KM) */}
                <div className="p-3.5 rounded-xl bg-[#04241E] border border-white/10 flex items-center justify-between">
                  <div>
                    <div className="text-xs font-bold text-white">Falar Nota da Corrida em Voz Alta</div>
                    <div className="text-[11px] text-white/60">
                      Narra R$/km e semáforo sem você precisar tirar o olho do trânsito
                    </div>
                  </div>
                  <button
                    onClick={() => saveNewConfig({ ...config, voiceEnabled: !config.voiceEnabled })}
                    className={`px-3.5 py-2 rounded-xl text-xs font-black ${
                      config.voiceEnabled
                        ? 'bg-[#97B304] text-[#04241E]'
                        : 'bg-white/10 text-white/60'
                    }`}
                  >
                    {config.voiceEnabled ? 'Voz Ligada' : 'Mudo'}
                  </button>
                </div>

                <button
                  onClick={() => {
                    setShowFloatingPreview(true);
                    setFloatingCollapsed(false);
                    if (isNative && window.AndroidBridge) {
                      window.AndroidBridge.previewOverlayCard(
                        currentRide.platform,
                        currentRide.offeredPrice,
                        currentRide.totalKm,
                        currentRide.durationMin,
                        currentRide.originAddress,
                        currentRide.destinationAddress
                      );
                    }
                  }}
                  className="w-full py-3 rounded-xl bg-[#97B304] text-[#04241E] font-black text-xs uppercase tracking-wider shadow-lg"
                >
                  Testar Janela Flutuante com {config.overlayOpacity}% de Opacidade
                </button>
              </div>
            </div>
          )}

          {/* ==========================================================
              TAB 3: SEMÁFORO DE VALORES (ROTA-PRO STYLE CONFIG + MATRIZ)
             ========================================================== */}
          {activeTab === 'targets' && (
            <div className="space-y-4">
              {/* Header do Semáforo */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[10px] font-black uppercase tracking-wider text-[#d9ff4f]">
                      Classificação por Cor
                    </span>
                    <h2 className="text-base font-black text-white flex items-center gap-2 mt-0.5">
                      <Sliders className="w-4 h-4 text-[#d9ff4f]" />
                      Semáforo de Valores
                    </h2>
                  </div>
                  <span className="px-2.5 py-1 rounded-full bg-[#04241E] border border-[#d9ff4f]/40 text-[11px] font-black text-[#d9ff4f]">
                    v3.3.24
                  </span>
                </div>
                <p className="text-xs text-white/70 mt-1">
                  Defina os limites de ganho por KM, Hora e Nota para colorir e avaliar as chamadas da Uber e 99 instantaneamente.
                </p>
              </div>

              {/* SIMULAÇÃO RÁPIDA: 3 BOTÕES DE TESTE AO VIVO */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-3.5 space-y-2">
                <span className="text-[10px] font-black uppercase tracking-wider text-emerald-300 flex items-center gap-1">
                  <Zap className="w-3.5 h-3.5 text-[#d9ff4f]" />
                  Testar Janela Flutuante ao Vivo (1-Toque)
                </span>
                <div className="grid grid-cols-3 gap-2">
                  {/* Botão 1: Corrida Boa */}
                  <button
                    onClick={() => {
                      triggerFloatingDemo(
                        'UBER',
                        28.5,
                        1.4,
                        8.6,
                        19,
                        'Av. Paulista, 1578 - Bela Vista, São Paulo',
                        'Rua Funchal, 418 - Vila Olímpia, São Paulo',
                        'GREEN',
                        4.96,
                        'UberX'
                      );
                    }}
                    className="p-2 rounded-xl bg-emerald-600/30 hover:bg-emerald-600/50 border border-emerald-500/60 text-center transition flex flex-col items-center justify-center gap-1"
                  >
                    <span className="text-xs font-black text-emerald-300">🟢 Aceitar</span>
                    <span className="text-[10px] text-white/80 font-bold">R$ 2,85/km</span>
                  </button>

                  {/* Botão 2: Corrida Média */}
                  <button
                    onClick={() => {
                      triggerFloatingDemo(
                        '99',
                        16.0,
                        1.8,
                        7.3,
                        25,
                        'Rua Augusta, 1200 - Consolação, São Paulo',
                        'Av. Rebouças, 3000 - Pinheiros, São Paulo',
                        'YELLOW',
                        4.82,
                        '99Pop'
                      );
                    }}
                    className="p-2 rounded-xl bg-amber-600/30 hover:bg-amber-600/50 border border-amber-500/60 text-center transition flex flex-col items-center justify-center gap-1"
                  >
                    <span className="text-xs font-black text-amber-300">🟡 Atenção</span>
                    <span className="text-[10px] text-white/80 font-bold">R$ 1,76/km</span>
                  </button>

                  {/* Botão 3: Corrida Ruim */}
                  <button
                    onClick={() => {
                      triggerFloatingDemo(
                        'UBER',
                        9.5,
                        3.2,
                        10.2,
                        32,
                        'Estrada do M Boi Mirim, 500 - Zona Sul',
                        'Av. Interlagos, 2220 - São Paulo',
                        'RED',
                        4.65,
                        'UberX'
                      );
                    }}
                    className="p-2 rounded-xl bg-red-600/30 hover:bg-red-600/50 border border-red-500/60 text-center transition flex flex-col items-center justify-center gap-1"
                  >
                    <span className="text-xs font-black text-red-300">🔴 Recusar</span>
                    <span className="text-[10px] text-white/80 font-bold">R$ 0,71/km</span>
                  </button>
                </div>
              </div>

              {/* TABELA "SUAS FAIXAS" (MATRIZ SEMAFÓRICA ESTILO ROTAPRO) */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-3.5 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-[10px] font-black uppercase tracking-wider text-[#d9ff4f]">
                    Suas Faixas Configuradas
                  </span>
                  <span className="text-[10px] text-white/50">Matriz Semafórica</span>
                </div>

                <div className="overflow-hidden rounded-xl border border-white/10 bg-[#04241E]">
                  <table className="w-full text-xs">
                    <thead>
                      <tr className="border-b border-white/10 text-[10px] uppercase font-black">
                        <th className="py-2 px-2 text-left text-white/60">Métrica</th>
                        <th className="py-2 px-1 text-center bg-red-500/20 text-red-300">🔴 Ruim</th>
                        <th className="py-2 px-1 text-center bg-amber-500/20 text-amber-300">🟡 Atenção</th>
                        <th className="py-2 px-1 text-center bg-emerald-500/20 text-emerald-300">🟢 Aceitar</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-white/5 font-bold">
                      {/* Linha 1: R$/km */}
                      <tr>
                        <td className="py-2.5 px-2 text-white/80">R$/km</td>
                        <td className="py-2.5 px-1 text-center bg-red-500/10 text-red-200">
                          &lt; R$ {config.yellowPerKm.toFixed(2).replace('.', ',')}
                        </td>
                        <td className="py-2.5 px-1 text-center bg-amber-500/10 text-amber-200">
                          R$ {config.yellowPerKm.toFixed(2).replace('.', ',')} a {config.greenPerKm.toFixed(2).replace('.', ',')}
                        </td>
                        <td className="py-2.5 px-1 text-center bg-emerald-500/10 text-emerald-200">
                          ≥ R$ {config.greenPerKm.toFixed(2).replace('.', ',')}
                        </td>
                      </tr>

                      {/* Linha 2: R$/Hora */}
                      <tr>
                        <td className="py-2.5 px-2 text-white/80">R$/Hora</td>
                        <td className="py-2.5 px-1 text-center bg-red-500/10 text-red-200">
                          &lt; R$ {config.yellowPerHour}
                        </td>
                        <td className="py-2.5 px-1 text-center bg-amber-500/10 text-amber-200">
                          R$ {config.yellowPerHour} a {config.greenPerHour}
                        </td>
                        <td className="py-2.5 px-1 text-center bg-emerald-500/10 text-emerald-200">
                          ≥ R$ {config.greenPerHour}
                        </td>
                      </tr>

                      {/* Linha 3: Nota Pax */}
                      <tr>
                        <td className="py-2.5 px-2 text-white/80">Nota Pax</td>
                        <td className="py-2.5 px-1 text-center bg-red-500/10 text-red-200">
                          &lt; {(config.minPassengerRating || 4.75).toFixed(2).replace('.', ',')}
                        </td>
                        <td className="py-2.5 px-1 text-center bg-amber-500/10 text-amber-200">
                          {(config.minPassengerRating || 4.75).toFixed(2).replace('.', ',')} a 4,85
                        </td>
                        <td className="py-2.5 px-1 text-center bg-emerald-500/10 text-emerald-200">
                          ≥ 4,85 ★
                        </td>
                      </tr>
                    </tbody>
                  </table>
                </div>
              </div>

              {/* SLIDERS E STEPPERS TRI-ZONE (CLASSIFICAÇÃO POR COR) */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-4">
                <div>
                  <span className="text-[10px] font-black uppercase tracking-wider text-[#d9ff4f]">
                    Ajuste Fino das Faixas
                  </span>
                  <h3 className="text-sm font-black text-white">Controles de Faixa Semafórica</h3>
                </div>

                {/* 1. Ganho por Km */}
                <div className="p-3.5 rounded-xl bg-[#04241E] border border-white/10 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-black text-white">Ganho por Km (R$/km)</span>
                    <span className="text-xs font-bold text-white/80">
                      🟡 Atenção: <strong className="text-amber-300">R$ {config.yellowPerKm.toFixed(2)}</strong> · 🟢 Aceitar: <strong className="text-emerald-300">R$ {config.greenPerKm.toFixed(2)}</strong>
                    </span>
                  </div>

                  {/* Segmented Color Track */}
                  <div className="h-3 rounded-full flex overflow-hidden shadow-inner bg-black/40 border border-white/10">
                    <div className="bg-red-500 h-full transition-all" style={{ width: '30%' }} title="Ruim" />
                    <div className="bg-amber-400 h-full transition-all" style={{ width: '35%' }} title="Atenção" />
                    <div className="bg-emerald-500 h-full transition-all" style={{ width: '35%' }} title="Aceitar" />
                  </div>

                  {/* Steppers +/- para motorista ajustar facilmente dirigindo ou parado */}
                  <div className="grid grid-cols-2 gap-2 pt-1">
                    {/* Stepper Amarelo */}
                    <div className="p-2 rounded-lg bg-[#073B32] border border-amber-500/30 flex items-center justify-between">
                      <span className="text-[10px] font-bold text-amber-300">Atenção</span>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              yellowPerKm: Math.max(0.5, Number((config.yellowPerKm - 0.1).toFixed(2))),
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="text-xs font-black text-white px-1">
                          R$ {config.yellowPerKm.toFixed(2)}
                        </span>
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              yellowPerKm: Number((config.yellowPerKm + 0.1).toFixed(2)),
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                    </div>

                    {/* Stepper Verde */}
                    <div className="p-2 rounded-lg bg-[#073B32] border border-emerald-500/30 flex items-center justify-between">
                      <span className="text-[10px] font-bold text-emerald-300">Aceitar</span>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              greenPerKm: Math.max(config.yellowPerKm, Number((config.greenPerKm - 0.1).toFixed(2))),
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="text-xs font-black text-white px-1">
                          R$ {config.greenPerKm.toFixed(2)}
                        </span>
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              greenPerKm: Number((config.greenPerKm + 0.1).toFixed(2)),
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 2. Ganho por Hora */}
                <div className="p-3.5 rounded-xl bg-[#04241E] border border-white/10 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-black text-white">Ganho por Hora (R$/h)</span>
                    <span className="text-xs font-bold text-white/80">
                      🟡 Atenção: <strong className="text-amber-300">R$ {config.yellowPerHour}</strong> · 🟢 Aceitar: <strong className="text-emerald-300">R$ {config.greenPerHour}</strong>
                    </span>
                  </div>

                  {/* Segmented Color Track */}
                  <div className="h-3 rounded-full flex overflow-hidden shadow-inner bg-black/40 border border-white/10">
                    <div className="bg-red-500 h-full transition-all" style={{ width: '30%' }} />
                    <div className="bg-amber-400 h-full transition-all" style={{ width: '35%' }} />
                    <div className="bg-emerald-500 h-full transition-all" style={{ width: '35%' }} />
                  </div>

                  {/* Steppers +/- */}
                  <div className="grid grid-cols-2 gap-2 pt-1">
                    <div className="p-2 rounded-lg bg-[#073B32] border border-amber-500/30 flex items-center justify-between">
                      <span className="text-[10px] font-bold text-amber-300">Atenção</span>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              yellowPerHour: Math.max(10, config.yellowPerHour - 1),
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="text-xs font-black text-white px-1">
                          R$ {config.yellowPerHour}
                        </span>
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              yellowPerHour: config.yellowPerHour + 1,
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                    </div>

                    <div className="p-2 rounded-lg bg-[#073B32] border border-emerald-500/30 flex items-center justify-between">
                      <span className="text-[10px] font-bold text-emerald-300">Aceitar</span>
                      <div className="flex items-center gap-1.5">
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              greenPerHour: Math.max(config.yellowPerHour, config.greenPerHour - 1),
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Minus className="w-3 h-3" />
                        </button>
                        <span className="text-xs font-black text-white px-1">
                          R$ {config.greenPerHour}
                        </span>
                        <button
                          onClick={() =>
                            saveNewConfig({
                              ...config,
                              greenPerHour: config.greenPerHour + 1,
                            })
                          }
                          className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                        >
                          <Plus className="w-3 h-3" />
                        </button>
                      </div>
                    </div>
                  </div>
                </div>

                {/* 3. Nota do Passageiro */}
                <div className="p-3.5 rounded-xl bg-[#04241E] border border-white/10 space-y-2.5">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-black text-white">Nota do Passageiro</span>
                    <span className="text-xs font-bold text-white/80">
                      Mínima: <strong className="text-amber-300">{(config.minPassengerRating || 4.75).toFixed(2)} ★</strong> · Ótima: <strong className="text-emerald-300">4,85 ★</strong>
                    </span>
                  </div>

                  {/* Segmented Color Track */}
                  <div className="h-3 rounded-full flex overflow-hidden shadow-inner bg-black/40 border border-white/10">
                    <div className="bg-red-500 h-full transition-all" style={{ width: '25%' }} />
                    <div className="bg-amber-400 h-full transition-all" style={{ width: '35%' }} />
                    <div className="bg-emerald-500 h-full transition-all" style={{ width: '40%' }} />
                  </div>

                  {/* Stepper Nota */}
                  <div className="p-2 rounded-lg bg-[#073B32] border border-white/10 flex items-center justify-between">
                    <span className="text-[11px] font-bold text-white/80">Nota Mínima Aceitável</span>
                    <div className="flex items-center gap-2">
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            minPassengerRating: Math.max(4.0, Number(((config.minPassengerRating || 4.75) - 0.05).toFixed(2))),
                          })
                        }
                        className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                      >
                        <Minus className="w-3 h-3" />
                      </button>
                      <span className="text-xs font-black text-white px-1">
                        {(config.minPassengerRating || 4.75).toFixed(2).replace('.', ',')} ★
                      </span>
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            minPassengerRating: Math.min(4.99, Number(((config.minPassengerRating || 4.75) + 0.05).toFixed(2))),
                          })
                        }
                        className="w-6 h-6 rounded bg-black/40 hover:bg-black/70 text-white font-black flex items-center justify-center text-xs"
                      >
                        <Plus className="w-3 h-3" />
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* CONDIÇÕES MÍNIMAS (CARDS DE SEGURANÇA) */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-3">
                <div>
                  <span className="text-[10px] font-black uppercase tracking-wider text-[#d9ff4f]">
                    Filtros de Proteção
                  </span>
                  <h3 className="text-sm font-black text-white">Condições Mínimas de Corrida</h3>
                </div>

                <div className="grid grid-cols-3 gap-2">
                  {/* Valor Mínimo */}
                  <div className="p-2.5 rounded-xl bg-[#04241E] border border-white/10 text-center flex flex-col justify-between">
                    <span className="text-[10px] text-white/60 font-bold leading-tight">Valor Mín.</span>
                    <div className="text-sm font-black text-white my-1">
                      R$ {(config.minTripPrice || 8.0).toFixed(0)}
                    </div>
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            minTripPrice: Math.max(5, (config.minTripPrice || 8.0) - 1),
                          })
                        }
                        className="w-5 h-5 rounded bg-black/40 text-white text-[11px] font-black"
                      >
                        -
                      </button>
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            minTripPrice: (config.minTripPrice || 8.0) + 1,
                          })
                        }
                        className="w-5 h-5 rounded bg-black/40 text-white text-[11px] font-black"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  {/* Lucro Mínimo */}
                  <div className="p-2.5 rounded-xl bg-[#04241E] border border-white/10 text-center flex flex-col justify-between">
                    <span className="text-[10px] text-white/60 font-bold leading-tight">Lucro Mín.</span>
                    <div className="text-sm font-black text-[#d9ff4f] my-1">
                      R$ {(config.minNetProfit || 5.0).toFixed(0)}
                    </div>
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            minNetProfit: Math.max(2, (config.minNetProfit || 5.0) - 1),
                          })
                        }
                        className="w-5 h-5 rounded bg-black/40 text-white text-[11px] font-black"
                      >
                        -
                      </button>
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            minNetProfit: (config.minNetProfit || 5.0) + 1,
                          })
                        }
                        className="w-5 h-5 rounded bg-black/40 text-white text-[11px] font-black"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  {/* Busca Máxima */}
                  <div className="p-2.5 rounded-xl bg-[#04241E] border border-white/10 text-center flex flex-col justify-between">
                    <span className="text-[10px] text-white/60 font-bold leading-tight">Busca Máx.</span>
                    <div className="text-sm font-black text-white my-1">
                      {(config.maxPickupKm || 3.5).toFixed(1)} km
                    </div>
                    <div className="flex items-center justify-center gap-1">
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            maxPickupKm: Math.max(1, Number(((config.maxPickupKm || 3.5) - 0.5).toFixed(1))),
                          })
                        }
                        className="w-5 h-5 rounded bg-black/40 text-white text-[11px] font-black"
                      >
                        -
                      </button>
                      <button
                        onClick={() =>
                          saveNewConfig({
                            ...config,
                            maxPickupKm: Number(((config.maxPickupKm || 3.5) + 0.5).toFixed(1)),
                          })
                        }
                        className="w-5 h-5 rounded bg-black/40 text-white text-[11px] font-black"
                      >
                        +
                      </button>
                    </div>
                  </div>
                </div>
              </div>

              {/* CUSTO DO VEÍCULO (CÁLCULO REAL POR KM) */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[10px] font-black uppercase tracking-wider text-[#d9ff4f]">
                      Cálculo de Lucro Líquido Real
                    </span>
                    <h3 className="text-sm font-black text-white">Custo do Seu Veículo</h3>
                  </div>
                  <span className="px-2.5 py-1 rounded-lg bg-[#04241E] border border-[#d9ff4f]/50 text-xs font-black text-[#d9ff4f]">
                    R$ {costPerKm.toFixed(2)} / km
                  </span>
                </div>

                <div className="grid grid-cols-3 gap-2">
                  <div>
                    <label className="text-[10px] text-white/70 block mb-1">Combustível (R$/L)</label>
                    <input
                      type="number"
                      step="0.1"
                      value={config.fuelPricePerLiter}
                      onChange={(e) =>
                        saveNewConfig({ ...config, fuelPricePerLiter: Number(e.target.value) })
                      }
                      className="w-full bg-[#04241E] border border-white/15 rounded-lg px-2.5 py-2 text-xs font-black text-white"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] text-white/70 block mb-1">Consumo (km/L)</label>
                    <input
                      type="number"
                      step="0.5"
                      value={config.carKmPerLiter}
                      onChange={(e) =>
                        saveNewConfig({ ...config, carKmPerLiter: Number(e.target.value) })
                      }
                      className="w-full bg-[#04241E] border border-white/15 rounded-lg px-2.5 py-2 text-xs font-black text-white"
                    />
                  </div>
                  <div>
                    <label className="text-[10px] text-white/70 block mb-1">Manut. (R$/km)</label>
                    <input
                      type="number"
                      step="0.05"
                      value={config.maintenancePerKm}
                      onChange={(e) =>
                        saveNewConfig({ ...config, maintenancePerKm: Number(e.target.value) })
                      }
                      className="w-full bg-[#04241E] border border-white/15 rounded-lg px-2.5 py-2 text-xs font-black text-white"
                    />
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* ==========================================================
              TAB 4: HISTÓRICO & CONFERÊNCIA DE PAGAMENTO UBER / 99
             ========================================================== */}
          {activeTab === 'history' && (
            <div className="space-y-3">
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-3.5">
                <span className="text-[11px] font-black uppercase tracking-wider text-[#B8D906]">
                  Auditoria do Motorista
                </span>
                <h2 className="text-base font-black text-white">
                  Histórico de Chamadas & Conferência de Valor
                </h2>
                <p className="text-xs text-white/70 mt-0.5">
                  Confira se o valor pago pela Uber/99 ao final da corrida bateu com o valor ofertado na tela e abra o Street View de qualquer endereço.
                </p>
              </div>

              {rides.map((r) => {
                const vc = getVerdictColors(r.verdict);
                const diff =
                  r.receivedPrice !== null ? Number((r.receivedPrice - r.offeredPrice).toFixed(2)) : null;

                return (
                  <div
                    key={r.id}
                    className="rounded-2xl bg-[#073B32] border border-white/10 p-3.5 space-y-2.5"
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-1.5">
                        <span className="px-2 py-0.5 rounded bg-[#04241E] text-[11px] font-black text-white">
                          {r.platform}
                        </span>
                        <span
                          className="px-2 py-0.5 rounded text-[10px] font-black text-white"
                          style={{ backgroundColor: vc.bg }}
                        >
                          R$ {r.pricePerKm.toFixed(2)}/km · R$ {Math.round(r.pricePerHour)}/h
                        </span>
                      </div>
                      <span className="text-[11px] text-white/60">
                        {new Date(r.timestamp).toLocaleTimeString('pt-BR', {
                          hour: '2-digit',
                          minute: '2-digit',
                        })}
                      </span>
                    </div>

                    {/* Valores: Ofertado vs Recebido */}
                    <div className="grid grid-cols-3 gap-2 bg-[#04241E] rounded-xl p-2.5 border border-white/10 text-center">
                      <div>
                        <div className="text-[10px] text-white/60">Ofertado na Tela</div>
                        <div className="text-sm font-black text-white">
                          R$ {r.offeredPrice.toFixed(2)}
                        </div>
                      </div>
                      <div className="border-x border-white/10">
                        <div className="text-[10px] text-white/60">Lucro Líquido</div>
                        <div className="text-sm font-black text-[#B8D906]">
                          R$ {r.netProfit.toFixed(2)}
                        </div>
                      </div>
                      <div>
                        <div className="text-[10px] text-white/60">Valor Recebido</div>
                        {r.receivedPrice !== null ? (
                          <div
                            className={`text-sm font-black ${
                              diff && diff < 0 ? 'text-red-400' : 'text-emerald-400'
                            }`}
                          >
                            R$ {r.receivedPrice.toFixed(2)}
                            {diff !== null && diff < 0 && ` (${diff.toFixed(2)})`}
                          </div>
                        ) : (
                          <button
                            onClick={() => {
                              setEditingPaymentId(r.id);
                              setReceivedInput(String(r.offeredPrice));
                            }}
                            className="text-xs font-black text-[#B8D906] underline mt-0.5"
                          >
                            Conferir
                          </button>
                        )}
                      </div>
                    </div>

                    {/* Input de conferência de pagamento */}
                    {editingPaymentId === r.id && (
                      <div className="flex items-center gap-2 bg-[#04241E] p-2 rounded-xl border border-[#97B304]">
                        <input
                          type="number"
                          step="0.1"
                          placeholder="Valor pago no app (R$)"
                          value={receivedInput}
                          onChange={(e) => setReceivedInput(e.target.value)}
                          className="flex-1 bg-[#073B32] rounded-lg px-2.5 py-1.5 text-xs font-black text-white"
                        />
                        <button
                          onClick={() => handleVerifyPayment(r.id)}
                          className="px-3 py-1.5 rounded-lg bg-[#97B304] text-[#04241E] text-xs font-black flex items-center gap-1"
                        >
                          <Check className="w-3.5 h-3.5" /> Salvar
                        </button>
                      </div>
                    )}

                    {/* Endereços Clicáveis para Street View */}
                    <div className="space-y-1 text-xs">
                      <button
                        onClick={() => openStreetView('Origem da Corrida', r.originAddress)}
                        className="w-full text-left flex items-center justify-between text-white/85 hover:text-[#B8D906] py-1 px-2 rounded bg-[#04241E]/60"
                      >
                        <span className="truncate">📍 Origem: {r.originAddress}</span>
                        <span className="text-[10px] font-bold text-[#B8D906] shrink-0 ml-2">
                          Street View ↗
                        </span>
                      </button>
                      <button
                        onClick={() => openStreetView('Destino da Corrida', r.destinationAddress)}
                        className="w-full text-left flex items-center justify-between text-white/85 hover:text-emerald-300 py-1 px-2 rounded bg-[#04241E]/60"
                      >
                        <span className="truncate">🏁 Destino: {r.destinationAddress}</span>
                        <span className="text-[10px] font-bold text-emerald-300 shrink-0 ml-2">
                          Street View ↗
                        </span>
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </main>

        {/* STREET VIEW 360° MODAL (INSPEÇÃO DE SEGURANÇA DE RUA EM 1 TOQUE) */}
        {streetViewModal.open && (
          <div className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-end sm:items-center justify-center p-3">
            <div className="w-full max-w-md bg-[#073B32] border-2 border-[#97B304] rounded-2xl overflow-hidden shadow-2xl">
              <div className="p-3.5 bg-[#04241E] flex items-center justify-between border-b border-white/10">
                <div className="min-w-0 pr-2">
                  <span className="text-[10px] font-black uppercase text-[#B8D906]">
                    {streetViewModal.label}
                  </span>
                  <h4 className="text-xs font-bold text-white truncate">
                    {streetViewModal.address}
                  </h4>
                </div>
                <button
                  onClick={() => setStreetViewModal({ open: false, label: '', address: '' })}
                  className="px-2.5 py-1 rounded-lg bg-white/10 text-xs font-black text-white"
                >
                  Fechar ✕
                </button>
              </div>

              {/* Embedded Interactive Map + Direct Google Street View 360 App Launcher */}
              <div className="h-64 w-full bg-black relative">
                <iframe
                  title="Google Maps View"
                  width="100%"
                  height="100%"
                  style={{ border: 0 }}
                  loading="lazy"
                  src={`https://maps.google.com/maps?q=${encodeURIComponent(
                    streetViewModal.address
                  )}&t=k&z=18&ie=UTF8&iwloc=&output=embed`}
                />
              </div>

              <div className="p-3.5 space-y-2 bg-[#073B32]">
                <a
                  href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(
                    streetViewModal.address
                  )}`}
                  target="_blank"
                  rel="noreferrer"
                  className="w-full py-2.5 px-4 rounded-xl bg-[#97B304] text-[#04241E] font-black text-xs flex items-center justify-center gap-2 shadow-lg"
                >
                  <ExternalLink className="w-4 h-4" />
                  Abrir Foto 360° da Rua no App Google Street View
                </a>
              </div>
            </div>
          </div>
        )}

        {/* BOTTOM NAVIGATION BAR (4 TABS COM SEMÁFORO DE VALORES) */}
        <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-md bg-[#073B32] border-t border-[#126152] grid grid-cols-4 py-1.5 px-2 z-30">
          <button
            onClick={() => setActiveTab('copilot')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'copilot' ? 'text-[#d9ff4f] font-black' : 'text-white/60 hover:text-white'
            }`}
          >
            <Power className="w-5 h-5" />
            <span className="text-[11px] mt-0.5">Copiloto</span>
          </button>

          <button
            onClick={() => setActiveTab('overlay')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'overlay' ? 'text-[#d9ff4f] font-black' : 'text-white/60 hover:text-white'
            }`}
          >
            <Eye className="w-5 h-5" />
            <span className="text-[11px] mt-0.5">Transparência</span>
          </button>

          <button
            onClick={() => setActiveTab('targets')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'targets' ? 'text-[#d9ff4f] font-black' : 'text-white/60 hover:text-white'
            }`}
          >
            <Sliders className="w-5 h-5" />
            <span className="text-[11px] mt-0.5 font-bold">Semáforo</span>
          </button>

          <button
            onClick={() => setActiveTab('history')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'history' ? 'text-[#d9ff4f] font-black' : 'text-white/60 hover:text-white'
            }`}
          >
            <History className="w-5 h-5" />
            <span className="text-[11px] mt-0.5">Histórico</span>
          </button>
        </nav>
      </div>
    </div>
  );
}

export default App;

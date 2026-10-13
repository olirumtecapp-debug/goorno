import React, { useState, useEffect, useRef } from 'react';
import { AdminDashboard } from './AdminDashboard';
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
  Check
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
}

export interface RideRecord {
  id: string;
  timestamp: string;
  platform: 'UBER' | '99' | 'INDRIVE' | 'OUTRO';
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
};

export function App() {
  const [isAdminView, setIsAdminView] = useState<boolean>(() => {
    return (
      window.location.pathname.startsWith('/admin') ||
      window.location.hash.includes('admin') ||
      window.location.search.includes('admin')
    );
  });

  useEffect(() => {
    const handleNav = () => {
      setIsAdminView(
        window.location.pathname.startsWith('/admin') ||
        window.location.hash.includes('admin') ||
        window.location.search.includes('admin')
      );
    };
    window.addEventListener('hashchange', handleNav);
    window.addEventListener('popstate', handleNav);
    return () => {
      window.removeEventListener('hashchange', handleNav);
      window.removeEventListener('popstate', handleNav);
    };
  }, []);

  const [activeTab, setActiveTab] = useState<'copilot' | 'overlay' | 'targets' | 'history'>('copilot');
  const [config, setConfig] = useState<DriverConfig>(DEFAULT_CONFIG);
  const [rides, setRides] = useState<RideRecord[]>([]);
  const [copilotActive, setCopilotActive] = useState<boolean>(false);

  // Native Android permissions state
  const [isNative, setIsNative] = useState<boolean>(false);
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
    destinationAddress: string
  ) => {
    const totalKm = Number((pickupKm + tripKm).toFixed(1));
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
    setShowFloatingPreview(true);
    setFloatingCollapsed(false);

    // Also save/evaluate via API
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
        accepted: true,
      }),
    })
      .then((r) => r.json())
      .then((data) => {
        if (data?.ride) {
          setRides((prev) => [data.ride, ...prev]);
          if (config.voiceEnabled && 'speechSynthesis' in window && !isNative) {
            window.speechSynthesis.cancel();
            const vText =
              data.ride.verdict === 'GREEN'
                ? `Boa chamada! ${data.ride.pricePerKm.toFixed(2).replace('.', ',')} reais por quilômetro.`
                : data.ride.verdict === 'YELLOW'
                ? `Atenção. ${data.ride.pricePerKm.toFixed(2).replace('.', ',')} por quilômetro.`
                : `Corrida ruim! Apenas ${data.ride.pricePerKm.toFixed(2).replace('.', ',')} por quilômetro.`;
            const utter = new SpeechSynthesisUtterance(vText);
            utter.lang = 'pt-BR';
            window.speechSynthesis.speak(utter);
          }
        }
      })
      .catch(() => {});
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
  };

  const getVerdictColors = (verdict: 'GREEN' | 'YELLOW' | 'RED') => {
    if (verdict === 'GREEN') {
      return {
        bg: '#0E9F6E',
        border: '#31C48D',
        badge: '🟢 ACEITAR · ÓTIMA',
        text: '#E3FCEF',
      };
    }
    if (verdict === 'YELLOW') {
      return {
        bg: '#D97706',
        border: '#FBBF24',
        badge: '🟡 ATENÇÃO · MÉDIA',
        text: '#FEF3C7',
      };
    }
    return {
      bg: '#DC2626',
      border: '#F87171',
      badge: '🔴 RECUSAR · PREJUÍZO',
      text: '#FEE2E2',
    };
  };

  const vStyle = getVerdictColors(currentRide.verdict);

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

        {/* FLOATING OVERLAY WINDOW (JANELA FLUTUANTE REAL SOBRE A TELA COM TRANSPARÊNCIA) */}
        {showFloatingPreview && (
          <div
            className="fixed top-16 left-1/2 -translate-x-1/2 z-40 w-[92%] max-w-[380px] transition-all duration-200"
            style={{
              opacity: Math.max(0.25, Math.min(1, config.overlayOpacity / 100)),
            }}
          >
            {floatingCollapsed ? (
              /* Pílula Flutuante Minimizada (estilo botão flutuante JetMax / GigU) */
              <div className="flex items-center justify-between bg-[#073B32] border-2 border-[#97B304] rounded-full px-3.5 py-2 shadow-2xl">
                <button
                  onClick={() => setFloatingCollapsed(false)}
                  className="flex items-center gap-2 text-left"
                >
                  <span className="w-2.5 h-2.5 rounded-full bg-[#B8D906] animate-pulse" />
                  <span className="text-xs font-black tracking-wide text-white">
                    {currentRide.platform} · R$ {currentRide.pricePerKm.toFixed(2)}/km · R${' '}
                    {Math.round(currentRide.pricePerHour)}/h
                  </span>
                </button>
                <button
                  onClick={() => setFloatingCollapsed(false)}
                  className="text-[11px] font-bold text-[#B8D906] bg-[#04241E] px-2.5 py-0.5 rounded-full ml-2"
                >
                  Abrir
                </button>
              </div>
            ) : (
              /* Card Semafórico Flutuante Completo (Abre por cima da Uber / 99) */
              <div
                className="rounded-2xl p-3.5 shadow-2xl border-2 text-white backdrop-blur-md"
                style={{
                  backgroundColor: '#062D26',
                  borderColor: vStyle.border,
                }}
              >
                {/* Top Strip: App + Semáforo + Transparência + Minimizar */}
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-1.5">
                    <span className="px-2 py-0.5 rounded-md bg-black/40 text-[11px] font-black tracking-wider text-white">
                      {currentRide.platform}
                    </span>
                    <span
                      className="px-2.5 py-0.5 rounded-md text-[11px] font-black tracking-wide"
                      style={{ backgroundColor: vStyle.bg, color: '#fff' }}
                    >
                      {vStyle.badge}
                    </span>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => setFloatingCollapsed(true)}
                      className="text-[11px] font-bold px-2 py-0.5 rounded bg-black/30 text-white/80 hover:text-white"
                      title="Minimizar para Pílula"
                    >
                      Minimizar
                    </button>
                    <button
                      onClick={() => setShowFloatingPreview(false)}
                      className="text-xs font-black px-2 py-0.5 rounded bg-black/30 text-white/70 hover:text-white"
                    >
                      ✕
                    </button>
                  </div>
                </div>

                {/* Big 3 Numbers (R$/KM, R$/HORA, LUCRO LÍQUIDO) - Leitura em 1 segundo */}
                <div className="grid grid-cols-3 gap-2 my-2 bg-black/35 rounded-xl p-2.5 border border-white/10">
                  <div className="text-center">
                    <div className="text-[10px] font-bold uppercase text-white/60">R$ / KM</div>
                    <div className="text-lg font-black text-white leading-tight">
                      R$ {currentRide.pricePerKm.toFixed(2)}
                    </div>
                  </div>
                  <div className="text-center border-x border-white/10">
                    <div className="text-[10px] font-bold uppercase text-white/60">R$ / HORA</div>
                    <div className="text-lg font-black text-white leading-tight">
                      R$ {Math.round(currentRide.pricePerHour)}
                    </div>
                  </div>
                  <div className="text-center">
                    <div className="text-[10px] font-bold uppercase text-emerald-300/80">LUCRO LÍQ.</div>
                    <div className="text-lg font-black text-[#B8D906] leading-tight">
                      R$ {currentRide.netProfit.toFixed(2)}
                    </div>
                  </div>
                </div>

                {/* Resumo da Chamada */}
                <div className="flex items-center justify-between text-xs text-white/80 px-1 mb-2">
                  <span>
                    Oferta: <strong className="text-white">R$ {currentRide.offeredPrice.toFixed(2)}</strong>
                  </span>
                  <span>
                    Distância: <strong className="text-white">{currentRide.totalKm} km</strong>
                  </span>
                  <span>
                    Tempo: <strong className="text-white">{currentRide.durationMin} min</strong>
                  </span>
                </div>

                {/* Botões 1-Toque Street View (Origem e Destino) direto na janela flutuante */}
                <div className="grid grid-cols-2 gap-1.5 pt-1 border-t border-white/10">
                  <button
                    onClick={() => openStreetView('Local de Embarque (Origem)', currentRide.originAddress)}
                    className="flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-lg bg-[#0B463B] hover:bg-[#0F5C4E] border border-[#97B304]/40 text-[11px] font-bold text-white truncate"
                  >
                    <MapPin className="w-3.5 h-3.5 text-[#B8D906] shrink-0" />
                    <span>Ver Origem (360°)</span>
                  </button>
                  <button
                    onClick={() => openStreetView('Local de Desembarque (Destino)', currentRide.destinationAddress)}
                    className="flex items-center justify-center gap-1.5 py-1.5 px-2 rounded-lg bg-[#0B463B] hover:bg-[#0F5C4E] border border-[#97B304]/40 text-[11px] font-bold text-white truncate"
                  >
                    <Navigation className="w-3.5 h-3.5 text-emerald-300 shrink-0" />
                    <span>Ver Destino (360°)</span>
                  </button>
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
                        Instalar APK Android Nativo (v1.0.0)
                      </span>
                    </div>
                    <a
                      href="/GoOrNo-v1.0.0.apk"
                      download="GoOrNo-v1.0.0.apk"
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
              TAB 3: METAS DO SEMÁFORO & CUSTO DO CARRO (R$/KM E R$/H)
             ========================================================== */}
          {activeTab === 'targets' && (
            <div className="space-y-4">
              {/* Metas do Semáforo */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-3.5">
                <div>
                  <span className="text-[11px] font-black uppercase tracking-wider text-[#B8D906]">
                    Critérios do Seu Semáforo
                  </span>
                  <h2 className="text-base font-black text-white">
                    Quanto Você Quer Ganhar por KM e Hora?
                  </h2>
                </div>

                {/* VERDE (ACEITAR) */}
                <div className="p-3 rounded-xl bg-[#04241E] border border-emerald-500/40 space-y-2.5">
                  <div className="text-xs font-black text-emerald-400">
                    🟢 SINAL VERDE (Corrida Ótima - Aceitar)
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-[11px] text-white/70 block mb-1">Mínimo R$ / KM</label>
                      <input
                        type="number"
                        step="0.1"
                        value={config.greenPerKm}
                        onChange={(e) =>
                          saveNewConfig({ ...config, greenPerKm: Number(e.target.value) })
                        }
                        className="w-full bg-[#073B32] border border-white/15 rounded-lg px-3 py-2 text-sm font-black text-white"
                      />
                    </div>
                    <div>
                      <label className="text-[11px] text-white/70 block mb-1">Mínimo R$ / Hora</label>
                      <input
                        type="number"
                        step="1"
                        value={config.greenPerHour}
                        onChange={(e) =>
                          saveNewConfig({ ...config, greenPerHour: Number(e.target.value) })
                        }
                        className="w-full bg-[#073B32] border border-white/15 rounded-lg px-3 py-2 text-sm font-black text-white"
                      />
                    </div>
                  </div>
                </div>

                {/* AMARELO (ATENÇÃO) */}
                <div className="p-3 rounded-xl bg-[#04241E] border border-amber-500/40 space-y-2.5">
                  <div className="text-xs font-black text-amber-300">
                    🟡 SINAL AMARELO (Corrida Média - Avaliar)
                  </div>
                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="text-[11px] text-white/70 block mb-1">Mínimo R$ / KM</label>
                      <input
                        type="number"
                        step="0.1"
                        value={config.yellowPerKm}
                        onChange={(e) =>
                          saveNewConfig({ ...config, yellowPerKm: Number(e.target.value) })
                        }
                        className="w-full bg-[#073B32] border border-white/15 rounded-lg px-3 py-2 text-sm font-black text-white"
                      />
                    </div>
                    <div>
                      <label className="text-[11px] text-white/70 block mb-1">Mínimo R$ / Hora</label>
                      <input
                        type="number"
                        step="1"
                        value={config.yellowPerHour}
                        onChange={(e) =>
                          saveNewConfig({ ...config, yellowPerHour: Number(e.target.value) })
                        }
                        className="w-full bg-[#073B32] border border-white/15 rounded-lg px-3 py-2 text-sm font-black text-white"
                      />
                    </div>
                  </div>
                  <div className="text-[11px] text-red-300 pt-1">
                    🔴 Abaixo de <strong>R$ {config.yellowPerKm.toFixed(2)}/km</strong> ou{' '}
                    <strong>R$ {config.yellowPerHour}/h</strong> o card ficará <strong>VERMELHO (Recusar)</strong>.
                  </div>
                </div>
              </div>

              {/* Custo por KM do Veículo */}
              <div className="rounded-2xl bg-[#073B32] border border-[#126152] p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <div>
                    <span className="text-[11px] font-black uppercase tracking-wider text-[#B8D906]">
                      Cálculo de Lucro Líquido Real
                    </span>
                    <h3 className="text-sm font-black text-white">Custo do Seu Veículo</h3>
                  </div>
                  <span className="px-2.5 py-1 rounded-lg bg-[#04241E] border border-[#97B304]/50 text-xs font-black text-[#B8D906]">
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

        {/* BOTTOM NAVIGATION BAR (4 SIMPLE TABS LIKE GIGU / JETMAX) */}
        <nav className="fixed bottom-0 left-1/2 -translate-x-1/2 w-full max-w-md bg-[#073B32] border-t border-[#126152] grid grid-cols-4 py-1.5 px-2 z-30">
          <button
            onClick={() => setActiveTab('copilot')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'copilot' ? 'text-[#B8D906] font-black' : 'text-white/60'
            }`}
          >
            <Power className="w-5 h-5" />
            <span className="text-[11px] mt-0.5">Copiloto</span>
          </button>

          <button
            onClick={() => setActiveTab('overlay')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'overlay' ? 'text-[#B8D906] font-black' : 'text-white/60'
            }`}
          >
            <Eye className="w-5 h-5" />
            <span className="text-[11px] mt-0.5">Transparência</span>
          </button>

          <button
            onClick={() => setActiveTab('targets')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'targets' ? 'text-[#B8D906] font-black' : 'text-white/60'
            }`}
          >
            <Sliders className="w-5 h-5" />
            <span className="text-[11px] mt-0.5">Metas R$/km</span>
          </button>

          <button
            onClick={() => setActiveTab('history')}
            className={`flex flex-col items-center justify-center py-1 rounded-xl transition ${
              activeTab === 'history' ? 'text-[#B8D906] font-black' : 'text-white/60'
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

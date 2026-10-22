import React, { useState, useEffect } from 'react';
import {
  LEGAL_METADATA,
  INDEPENDENCE_DISCLAIMER,
  HOW_OFFER_READING_WORKS,
  TERMS_OF_USE_TEXT,
  PRIVACY_POLICY_TEXT,
} from './legalTexts';
import {
  Download,
  Share2,
  Copy,
  Mail,
  ShieldCheck,
  ChevronDown,
  Sparkles,
  ArrowRight,
  ExternalLink,
  CheckCircle2,
  XCircle,
  HelpCircle,
  Eye,
  Sliders,
  Smartphone,
  Lock,
  Car,
  MapPin,
  ZoomIn,
  X,
  ChevronLeft,
  ChevronRight,
  Maximize2,
  Zap,
  AlertTriangle,
  Check,
  Star,
  ShieldAlert
} from 'lucide-react';
import { ContactModal } from './ContactModal';
import { SCREENSHOTS, ScreenshotItem } from './ScreenshotsShowcase';

interface DownloadLandingPageProps {
  onOpenSimulator?: () => void;
  onOpenAdmin?: () => void;
}

export function DownloadLandingPage({ onOpenSimulator, onOpenAdmin }: DownloadLandingPageProps) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [isContactOpen, setIsContactOpen] = useState(false);
  const [openFaq, setOpenFaq] = useState<number | null>(0);
  const [heroMode, setHeroMode] = useState<'good' | 'bad'>('good');
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [selectedScreenshot, setSelectedScreenshot] = useState<ScreenshotItem | null>(null);
  const [isFolderModalOpen, setIsFolderModalOpen] = useState(false);
  const [legalModalType, setLegalModalType] = useState<'terms' | 'privacy' | 'independence' | 'reading' | 'delete' | null>(null);
  const [deletePhone, setDeletePhone] = useState('');
  const [deletePin, setDeletePin] = useState('');
  const [deleteSecret, setDeleteSecret] = useState('');
  const [deleteFeedback, setDeleteFeedback] = useState<string | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);
  const [appVersion, setAppVersion] = useState<string>('3.3.26');

  useEffect(() => {
    fetch('/api/app-version')
      .then((r) => r.json())
      .then((d) => {
        if (d?.versionName) {
          setAppVersion(d.versionName);
        }
      })
      .catch(() => {});
  }, []);

  const filteredScreenshots = activeCategory === 'all'
    ? SCREENSHOTS
    : SCREENSHOTS.filter((s) => s.category === activeCategory);

  const handleNextScreenshot = () => {
    if (!selectedScreenshot) return;
    const currentIndex = filteredScreenshots.findIndex((s) => s.id === selectedScreenshot.id);
    const nextIndex = (currentIndex + 1) % filteredScreenshots.length;
    setSelectedScreenshot(filteredScreenshots[nextIndex]);
  };

  const handlePrevScreenshot = () => {
    if (!selectedScreenshot) return;
    const currentIndex = filteredScreenshots.findIndex((s) => s.id === selectedScreenshot.id);
    const prevIndex = (currentIndex - 1 + filteredScreenshots.length) % filteredScreenshots.length;
    setSelectedScreenshot(filteredScreenshots[prevIndex]);
  };

  const handleDownloadApk = () => {
    // Redireciona diretamente para o link de download oficial do APK v3.3.28
    window.location.href = '/GoOrNo.apk';
  };

  const toggleFaq = (index: number) => {
    setOpenFaq(openFaq === index ? null : index);
  };

  const scrollToSection = (id: string) => {
    setIsMenuOpen(false);
    const element = document.getElementById(id);
    if (element) {
      const headerOffset = 95;
      const elementPosition = element.getBoundingClientRect().top;
      const offsetPosition = elementPosition + window.pageYOffset - headerOffset;
      window.scrollTo({
        top: Math.max(0, offsetPosition),
        behavior: 'smooth'
      });
    }
  };

  return (
    <div className="font-dm min-h-screen bg-[#f3f5ed] text-[#071d19] selection:bg-[#d9ff4f] selection:text-[#071d19]">
      {/* HEADER FIXO SUPERIOR */}
      <header className="site-header">
        <a href="#top" className="brand" onClick={(e) => { e.preventDefault(); scrollToSection('top'); }}>
          <img
            src="/assets/logo-goorno-clean.png"
            alt="GoOrNo"
            onError={(e) => {
              (e.target as HTMLImageElement).src = '/logo-goorno-clean.png';
            }}
          />
          <span>
            GoOrNo <small>v{appVersion}</small>
          </span>
        </a>

        {/* BOTÃO MOBILE */}
        <button
          className="menu-toggle"
          onClick={() => setIsMenuOpen(!isMenuOpen)}
          aria-label={isMenuOpen ? 'Fechar menu' : 'Abrir menu'}
        >
          {isMenuOpen ? '✕' : '☰'}
        </button>

        {/* NAVEGAÇÃO PRINCIPAL */}
        <nav className={`nav-links ${isMenuOpen ? 'open' : ''}`}>
          <a
            href="#como-funciona"
            onClick={(e) => {
              e.preventDefault();
              scrollToSection('como-funciona');
            }}
          >
            Como funciona
          </a>
          <a
            href="#telas"
            onClick={(e) => {
              e.preventDefault();
              scrollToSection('telas');
            }}
          >
            Telas do app
          </a>
          <a
            href="#duvidas"
            onClick={(e) => {
              e.preventDefault();
              scrollToSection('duvidas');
            }}
          >
            Dúvidas
          </a>
          <button
            onClick={() => {
              setIsMenuOpen(false);
              setIsContactOpen(true);
            }}
            className="bg-transparent border-0 text-[#c2d0ca] hover:text-[#d9ff4f] text-sm font-medium transition cursor-pointer text-left py-2 sm:py-0 flex items-center gap-1.5"
          >
            <Mail className="w-3.5 h-3.5 text-[#d9ff4f]" />
            Fale conosco
          </button>
          <button
            onClick={handleDownloadApk}
            className="nav-cta cursor-pointer border-0"
          >
            Baixar grátis <span>↗</span>
          </button>
        </nav>
      </header>

      <main id="top">
        {/* HERO SECTION */}
        <section className="hero section-shell">
          <div className="hero-copy">
            <div className="eyebrow">
              <span className="pulse-dot"></span> Copiloto oficial do motorista
            </div>
            <h1>
              Aceite só as corridas que <em>dão lucro.</em>
            </h1>
            <p className="hero-lede">
              O GoOrNo lê a tela da Uber e da 99 e mostra o valor real de cada chamada em menos de 1 segundo.
            </p>
            <div className="hero-actions" id="download">
              <button
                onClick={handleDownloadApk}
                className="button button-primary cursor-pointer border-0"
              >
                Baixar GoOrNo grátis <span>↓</span>
              </button>
              <button
                onClick={() => scrollToSection('telas')}
                className="button button-ghost cursor-pointer"
              >
                Ver o app na prática <span>↗</span>
              </button>
            </div>
            <div className="trust-row">
              <span>
                <strong>7 dias</strong> grátis
              </span>
              <span className="trust-separator"></span>
              <span>Sem cartão</span>
              <span className="trust-separator"></span>
              <span>Android 8+</span>
              <span className="trust-separator"></span>
              <span>Versão oficial v{appVersion}</span>
            </div>

            {/* AVISO DE CONFIANÇA E SEGURANÇA DO APK */}
            <div className="mt-5 p-3.5 rounded-2xl bg-[#092b24] border border-[#175244] text-left max-w-xl shadow-md">
              <div className="flex items-start gap-3">
                <div className="w-9 h-9 rounded-xl bg-emerald-500/15 border border-emerald-500/30 flex items-center justify-center shrink-0 text-emerald-400 mt-0.5">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <div>
                  <div className="flex flex-wrap items-center gap-2 mb-1">
                    <span className="font-space font-bold text-xs text-white">
                      Download Seguro & Direto (Arquivo APK)
                    </span>
                    <span className="bg-emerald-500/20 text-emerald-300 text-[10px] font-extrabold px-2 py-0.5 rounded-full border border-emerald-500/30">
                      PRIVACIDADE TOTAL
                    </span>
                  </div>
                  <p className="font-dm text-[11.5px] text-[#b8ccc3] leading-relaxed m-0">
                    O Android exibe um aviso padrão (<em>"O arquivo pode ser nocivo"</em>) para qualquer aplicativo baixado fora da Play Store. <strong>Pode baixar com total tranquilidade:</strong> o GoOrNo não pede senhas, não acessa contas bancárias e não coloca seu aparelho em risco. Basta tocar em <strong>"Fazer o download mesmo assim"</strong>.
                  </p>
                </div>
              </div>
            </div>
          </div>

          <div className="hero-visual">
            <div className="visual-orbit orbit-one"></div>
            <div className="visual-orbit orbit-two"></div>

            {/* MOCKUP INTERATIVO DO SMARTPHONE */}
            <div className="phone-frame">
              <div className="phone-notch"></div>
              <div className="phone-screen">
                <div className="fake-status">
                  <span>9:41</span>
                  <span>▮▮▮ ᯤ</span>
                </div>
                <div className="uber-map">
                  <div className="map-road road-a"></div>
                  <div className="map-road road-b"></div>
                  <div className="map-pin">●</div>
                </div>

                {/* CARD FLUTUANTE DE CHAMADA REAL */}
                <div className="offer-card">
                  <div className="offer-top">
                    <span>{heroMode === 'good' ? 'UBER X' : '99 POP'}</span>
                    <span className="live-tag">
                      {heroMode === 'good' ? '● NOVA CHAMADA' : '● NOVA CHAMADA'}
                    </span>
                  </div>
                  <strong>
                    {heroMode === 'good' ? 'R$ 18,90' : 'R$ 11,25'}
                  </strong>
                  <div className="offer-meta">
                    <span>{heroMode === 'good' ? '7,2 km' : '9,0 km'}</span>
                    <span>•</span>
                    <span>{heroMode === 'good' ? '18 min' : '26 min'}</span>
                  </div>

                  {heroMode === 'good' ? (
                    <div className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-800 border border-emerald-200 text-[9.5px] font-bold my-1">
                      <span>⭐ Região Favorita: Jardins</span>
                    </div>
                  ) : (
                    <div className="flex items-center gap-1 px-2 py-0.5 rounded-md bg-rose-50 text-rose-800 border border-rose-200 text-[9.5px] font-bold my-1">
                      <span>🚨 Alerta: Área de Risco no Destino</span>
                    </div>
                  )}

                  {heroMode === 'good' ? (
                    <div className="decision decision-good">
                      <span>●</span> ACEITAR <b>R$ 2,63/km</b>
                    </div>
                  ) : (
                    <div className="decision" style={{ background: '#ffe1d9', color: '#a34b3a' }}>
                      <span>●</span> RECUSAR <b>R$ 1,25/km</b>
                    </div>
                  )}

                  {/* Alternador interativo para demonstrar na tela */}
                  <div className="mt-2.5 pt-2 border-t border-[rgba(7,29,25,0.08)] flex items-center justify-between text-[10px]">
                    <span className="text-[#78867f] font-medium">Simular:</span>
                    <div className="flex gap-1">
                      <button
                        onClick={() => setHeroMode('good')}
                        className={`px-2 py-0.5 rounded-full font-bold transition cursor-pointer border-0 ${
                          heroMode === 'good'
                            ? 'bg-[#071d19] text-[#d9ff4f]'
                            : 'bg-black/5 text-[#78867f]'
                        }`}
                      >
                        Boa
                      </button>
                      <button
                        onClick={() => setHeroMode('bad')}
                        className={`px-2 py-0.5 rounded-full font-bold transition cursor-pointer border-0 ${
                          heroMode === 'bad'
                            ? 'bg-[#071d19] text-[#ff6e58]'
                            : 'bg-black/5 text-[#78867f]'
                        }`}
                      >
                        Ruim
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            {/* PÍLULAS FLUTUANTES COM OS NÚMEROS REAIS */}
            <div className="floating-stat stat-km">
              <small>GANHO REAL</small>
              <strong>
                {heroMode === 'good' ? 'R$ 2,63' : 'R$ 1,25'}
                <span>/km</span>
              </strong>
              <b style={{ color: heroMode === 'good' ? '#83a000' : '#a34b3a' }}>
                {heroMode === 'good' ? '↑ ótima corrida' : '↓ prejuízo'}
              </b>
            </div>

            <div className="floating-stat stat-time">
              <small>PROJEÇÃO</small>
              <strong>
                {heroMode === 'good' ? 'R$ 63' : 'R$ 25'}
                <span>/h</span>
              </strong>
              <b>calculado na hora</b>
            </div>
          </div>
        </section>

        {/* FAIXA DE BENEFÍCIOS (01, 02, 03) */}
        <section className="proof-strip section-shell">
          <div>
            <span className="proof-icon">01</span>
            <strong>Leia a chamada</strong>
            <small>sem digitar nada</small>
          </div>
          <div>
            <span className="proof-icon">02</span>
            <strong>Veja o ganho real</strong>
            <small>R$/km e R$/hora</small>
          </div>
          <div>
            <span className="proof-icon">03</span>
            <strong>Decida rápido</strong>
            <small>aceitar ou recusar</small>
          </div>
        </section>

        {/* SEÇÃO COMPARATIVA: BOA VS RUIM */}
        <section className="split-section section-shell" id="como-funciona">
          <div className="section-intro">
            <div className="eyebrow">Decisão em um relance</div>
            <h2>Não trabalhe no escuro.</h2>
            <p>
              Antes de sair acelerando, saiba se a corrida paga o seu tempo, combustível e desgaste do carro.
            </p>
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 w-full max-w-4xl mx-auto">
            {/* CARD COMPENSA (VALE A PENA) */}
            <article 
              onClick={() => {
                const item = SCREENSHOTS.find((s) => s.id === 'viagem-compensa');
                if (item) setSelectedScreenshot(item);
              }}
              className="bg-[#f2fae8] border-2 border-[#a6d84a] rounded-3xl p-5 sm:p-6 shadow-md hover:shadow-2xl transition-all duration-300 flex flex-col justify-between group cursor-pointer"
            >
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className="inline-flex items-center gap-1.5 text-xs font-black px-3 py-1 rounded-full bg-[#166534] text-white tracking-wide font-space shadow-xs">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    ✓ COMPENSA
                  </span>
                  <span className="text-[11px] font-bold text-[#166534] bg-white/90 px-2.5 py-0.5 rounded-full border border-[#166534]/20">
                    Aprovada em Verde
                  </span>
                </div>

                <div className="flex items-baseline gap-2 mb-1">
                  <span className="font-space font-black text-3xl sm:text-4xl text-[#071d19] tracking-tight">R$ 4,60</span>
                  <span className="text-sm font-bold text-[#365922]">/km · R$ 106,87/h</span>
                </div>
                <h3 className="font-space font-black text-lg text-[#071d19] mb-1">
                  Vale a pena aceitar
                </h3>
                <p className="text-xs sm:text-sm text-[#3b5936] leading-relaxed mb-4">
                  R$ 28,50 por 6,2 km (16 min). O GoOrNo calcula combustível + manutenção preventiva e confirma que sobra lucro líquido real no seu bolso.
                </p>

                {/* Smartphone Preview Mockup */}
                <div className="relative mx-auto my-2 p-1.5 bg-[#09110e] rounded-[26px] border-[2.5px] border-[#223530] shadow-md max-w-[190px] group-hover:scale-105 transition-transform duration-300">
                  <div className="absolute top-2 left-1/2 -translate-x-1/2 w-10 h-2 bg-black rounded-full z-10 pointer-events-none"></div>
                  <div className="relative aspect-[9/18.5] rounded-[20px] overflow-hidden bg-black flex items-center justify-center">
                    <img
                      src="/telas/ViagemCompensa.jpeg"
                      alt="Corrida que compensa no GoOrNo"
                      className="w-full h-full object-cover"
                      loading="lazy"
                    />
                  </div>
                </div>
              </div>

              <div className="mt-4 pt-3 border-t border-[#166534]/15 flex items-center justify-between text-xs font-bold text-[#166534]">
                <span className="flex items-center gap-1">Toque para ver a tela oficial ↗</span>
                <span className="bg-[#166534] text-white px-3.5 py-1 rounded-full text-[11px] shadow-xs">ACEITAR</span>
              </div>
            </article>

            {/* CARD NÃO COMPENSA (MELHOR RECUSAR) */}
            <article 
              onClick={() => {
                const item = SCREENSHOTS.find((s) => s.id === 'viagem-nao-compensa');
                if (item) setSelectedScreenshot(item);
              }}
              className="bg-[#fef2f2] border-2 border-[#fca5a5] rounded-3xl p-5 sm:p-6 shadow-md hover:shadow-2xl transition-all duration-300 flex flex-col justify-between group cursor-pointer"
            >
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className="inline-flex items-center gap-1.5 text-xs font-black px-3 py-1 rounded-full bg-[#dc2626] text-white tracking-wide font-space shadow-xs">
                    <XCircle className="w-3.5 h-3.5" />
                    ✕ NÃO COMPENSA
                  </span>
                  <span className="text-[11px] font-bold text-[#dc2626] bg-white/90 px-2.5 py-0.5 rounded-full border border-[#dc2626]/20">
                    Alerta Vermelho
                  </span>
                </div>

                <div className="flex items-baseline gap-2 mb-1">
                  <span className="font-space font-black text-3xl sm:text-4xl text-[#071d19] tracking-tight">R$ 1,60</span>
                  <span className="text-sm font-bold text-[#991b1b]">/km · R$ 39,11/h</span>
                </div>
                <h3 className="font-space font-black text-lg text-[#071d19] mb-1">
                  Melhor recusar na hora
                </h3>
                <p className="text-xs sm:text-sm text-[#7f1d1d] leading-relaxed mb-4">
                  R$ 37,15 por 23,2 km (57 min). O copiloto detecta remuneração insuficiente e avisa que você vai pagar para trabalhar se fizer essa corrida.
                </p>

                {/* Smartphone Preview Mockup */}
                <div className="relative mx-auto my-2 p-1.5 bg-[#09110e] rounded-[26px] border-[2.5px] border-[#223530] shadow-md max-w-[190px] group-hover:scale-105 transition-transform duration-300">
                  <div className="absolute top-2 left-1/2 -translate-x-1/2 w-10 h-2 bg-black rounded-full z-10 pointer-events-none"></div>
                  <div className="relative aspect-[9/18.5] rounded-[20px] overflow-hidden bg-black flex items-center justify-center">
                    <img
                      src="/telas/ViagemNaoCompensa.jpeg"
                      alt="Corrida que não compensa no GoOrNo"
                      className="w-full h-full object-cover"
                      loading="lazy"
                    />
                  </div>
                </div>
              </div>

              <div className="mt-4 pt-3 border-t border-[#dc2626]/15 flex items-center justify-between text-xs font-bold text-[#dc2626]">
                <span className="flex items-center gap-1">Toque para ver a tela oficial ↗</span>
                <span className="bg-[#dc2626] text-white px-3.5 py-1 rounded-full text-[11px] shadow-xs">RECUSAR</span>
              </div>
            </article>
          </div>
        </section>

        {/* SEÇÃO: 4 FORMATOS DE CARDS FLUTUANTES & PERSONALIZAÇÃO TOTAL */}
        <section className="section-shell py-12" id="formatos-cards">
          <div className="max-w-5xl mx-auto bg-[#071d19] text-white rounded-3xl p-6 sm:p-10 border border-white/10 shadow-xl relative overflow-hidden">
            <div className="text-center max-w-2xl mx-auto mb-8">
              <span className="font-space font-bold text-xs uppercase tracking-widest text-[#d9ff4f] bg-[#d9ff4f]/15 px-3.5 py-1 rounded-full border border-[#d9ff4f]/30 inline-flex items-center gap-1.5 mb-3">
                <Sparkles className="w-3.5 h-3.5" />
                Personalização Visual Exclusiva
              </span>
              <h2 className="font-space text-2xl sm:text-3xl font-bold tracking-tight mb-2 text-white">
                4 Modelos de Janela Flutuante à Sua Escolha
              </h2>
              <p className="font-dm text-xs sm:text-sm text-[#c2d0ca] leading-relaxed">
                Você escolhe o formato que mais combina com seu estilo de direção, com total liberdade para ajustar o modo claro ou escuro e a transparência do card.
              </p>
            </div>

            {/* GRID DOS 4 TIPOS DE CARDS */}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
              <div 
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'card-tipo-1');
                  if (item) setSelectedScreenshot(item);
                }}
                className="bg-white/5 border border-white/10 hover:border-[#d9ff4f]/60 rounded-2xl p-4 transition-all duration-200 cursor-pointer group flex flex-col justify-between"
              >
                <div>
                  <span className="text-[10px] font-black px-2 py-0.5 rounded bg-emerald-600 text-white font-space inline-block mb-2">
                    MODELO 1 · RECOMENDADO
                  </span>
                  <h4 className="font-space font-bold text-sm text-white group-hover:text-[#d9ff4f] transition mb-1">
                    Card Impacto Pro
                  </h4>
                  <p className="font-dm text-xs text-[#a9beb6] leading-relaxed mb-3">
                    Km e tempo no topo, valor gigante no centro e faixa sólida com o veredito (Compensa / Analisar / Recusar).
                  </p>
                </div>
                <div className="relative mx-auto w-full p-1 bg-[#09110e] rounded-[20px] border border-[#223530] max-w-[140px]">
                  <div className="aspect-[9/18.5] rounded-[16px] overflow-hidden bg-black flex items-center justify-center">
                    <img src="/telas/CarTipo1.jpeg" alt="Card Impacto Pro" className="w-full h-full object-cover group-hover:scale-105 transition" />
                  </div>
                </div>
              </div>

              <div 
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'card-tipo-2');
                  if (item) setSelectedScreenshot(item);
                }}
                className="bg-white/5 border border-white/10 hover:border-[#d9ff4f]/60 rounded-2xl p-4 transition-all duration-200 cursor-pointer group flex flex-col justify-between"
              >
                <div>
                  <span className="text-[10px] font-black px-2 py-0.5 rounded bg-blue-600 text-white font-space inline-block mb-2">
                    MODELO 2 · COLUNAS
                  </span>
                  <h4 className="font-space font-bold text-sm text-white group-hover:text-[#d9ff4f] transition mb-1">
                    Card 3 Pilares
                  </h4>
                  <p className="font-dm text-xs text-[#a9beb6] leading-relaxed mb-3">
                    Resumo no cabeçalho e 3 colunas verticais com barras coloridas (R$/km, R$/hora e nota do passageiro).
                  </p>
                </div>
                <div className="relative mx-auto w-full p-1 bg-[#09110e] rounded-[20px] border border-[#223530] max-w-[140px]">
                  <div className="aspect-[9/18.5] rounded-[16px] overflow-hidden bg-black flex items-center justify-center">
                    <img src="/telas/CarTipo2.jpeg" alt="Card 3 Pilares" className="w-full h-full object-cover group-hover:scale-105 transition" />
                  </div>
                </div>
              </div>

              <div 
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'card-tipo-3');
                  if (item) setSelectedScreenshot(item);
                }}
                className="bg-white/5 border border-white/10 hover:border-[#d9ff4f]/60 rounded-2xl p-4 transition-all duration-200 cursor-pointer group flex flex-col justify-between"
              >
                <div>
                  <span className="text-[10px] font-black px-2 py-0.5 rounded bg-purple-700 text-white font-space inline-block mb-2">
                    MODELO 3 · TRADICIONAL
                  </span>
                  <h4 className="font-space font-bold text-sm text-white group-hover:text-[#d9ff4f] transition mb-1">
                    Card Clássico
                  </h4>
                  <p className="font-dm text-xs text-[#a9beb6] leading-relaxed mb-3">
                    Layout completo com tags individuais de faturamento por hora, ganho por km e contagem regressiva.
                  </p>
                </div>
                <div className="relative mx-auto w-full p-1 bg-[#09110e] rounded-[20px] border border-[#223530] max-w-[140px]">
                  <div className="aspect-[9/18.5] rounded-[16px] overflow-hidden bg-black flex items-center justify-center">
                    <img src="/telas/CarTipo3.jpeg" alt="Card Clássico" className="w-full h-full object-cover group-hover:scale-105 transition" />
                  </div>
                </div>
              </div>

              <div 
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'card-tipo-4');
                  if (item) setSelectedScreenshot(item);
                }}
                className="bg-white/5 border border-white/10 hover:border-[#d9ff4f]/60 rounded-2xl p-4 transition-all duration-200 cursor-pointer group flex flex-col justify-between"
              >
                <div>
                  <span className="text-[10px] font-black px-2 py-0.5 rounded bg-amber-600 text-white font-space inline-block mb-2">
                    MODELO 4 · DISCRETO
                  </span>
                  <h4 className="font-space font-bold text-sm text-white group-hover:text-[#d9ff4f] transition mb-1">
                    Bolinha Semáforo
                  </h4>
                  <p className="font-dm text-xs text-[#a9beb6] leading-relaxed mb-3">
                    Pílula horizontal ultrafina que ocupa o mínimo de tela, sem tapar a rota do GPS ou o mapa da corrida.
                  </p>
                </div>
                <div className="relative mx-auto w-full p-1 bg-[#09110e] rounded-[20px] border border-[#223530] max-w-[140px]">
                  <div className="aspect-[9/18.5] rounded-[16px] overflow-hidden bg-black flex items-center justify-center">
                    <img src="/telas/CarTipo4.jpeg" alt="Card Discreto" className="w-full h-full object-cover group-hover:scale-105 transition" />
                  </div>
                </div>
              </div>
            </div>

            {/* 3 PILARES DE CUSTOMIZAÇÃO */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 pt-4 border-t border-white/10">
              <div className="bg-white/5 rounded-2xl p-3.5 border border-white/10 flex items-start gap-3">
                <span className="text-xl">☀️🌙</span>
                <div>
                  <h5 className="font-space font-bold text-xs text-white">Modo Claro e Modo Escuro</h5>
                  <p className="font-dm text-[11px] text-[#c2d0ca] leading-relaxed">
                    Alterne o tema do card para rodar no sol forte durante o dia ou com tema noturno sem cansar a vista.
                  </p>
                </div>
              </div>

              <div className="bg-white/5 rounded-2xl p-3.5 border border-white/10 flex items-start gap-3">
                <span className="text-xl">👁️</span>
                <div>
                  <h5 className="font-space font-bold text-xs text-white">Transparência Ajustável</h5>
                  <p className="font-dm text-[11px] text-[#c2d0ca] leading-relaxed">
                    Ajuste a opacidade de 0% a 100% para ver o mapa de trânsito por trás da janela com total nitidez.
                  </p>
                </div>
              </div>

              <div className="bg-white/5 rounded-2xl p-3.5 border border-white/10 flex items-start gap-3">
                <span className="text-xl">📍</span>
                <div>
                  <h5 className="font-space font-bold text-xs text-white">Posicionamento Livre</h5>
                  <p className="font-dm text-[11px] text-[#c2d0ca] leading-relaxed">
                    Fixe a janelinha no topo, centro, rodapé, esquerda ou direita da tela conforme a sua preferência.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* SEÇÃO DE RECURSOS / COPILOTO */}
        <section className="feature-section section-shell">
          <div className="feature-heading">
            <div className="eyebrow">Seu copiloto na rua</div>
            <h2>
              Mais clareza.
              <br />
              <em>Mais lucro.</em>
            </h2>
          </div>
          <div className="feature-list">
            <article>
              <span className="feature-number">01</span>
              <div>
                <h3>R$/km e R$/hora</h3>
                <p>Veja o que realmente sobra por quilômetro rodado e por hora trabalhada.</p>
              </div>
            </article>
            <article>
              <span className="feature-number">02</span>
              <div>
                <h3>Veredito em cores</h3>
                <p>Verde para aceitar com confiança, amarelo para analisar e vermelho para recusar.</p>
              </div>
            </article>
            <article>
              <span className="feature-number">03</span>
              <div>
                <h3>Street View em 1 toque</h3>
                <p>Confira a fachada do embarque e do destino em 360° antes de se deslocar.</p>
              </div>
            </article>
            <article>
              <span className="feature-number">04</span>
              <div>
                <h3>Custo real do veículo</h3>
                <p>Configure combustível e consumo para decidir com a realidade do seu bolso.</p>
              </div>
            </article>
            <article>
              <span className="feature-number">05</span>
              <div>
                <h3>⭐ Bairros Favoritos</h3>
                <p>Trabalhe na sua zona de conforto. O card destaca na hora se a corrida é nos seus bairros preferidos.</p>
              </div>
            </article>
            <article>
              <span className="feature-number">06</span>
              <div>
                <h3>🚨 Radar de Áreas de Risco</h3>
                <p>Alerta vermelho antecipado se o embarque ou destino for em local perigoso. Volte para casa com segurança.</p>
              </div>
            </article>
          </div>
        </section>

        {/* SEÇÃO DESTAQUE: BAIRROS FAVORITOS & ÁREAS DE RISCO */}
        <section className="section-shell" style={{ paddingTop: '0.5rem', paddingBottom: '2.5rem' }}>
          <div className="bg-gradient-to-br from-[#071d19] to-[#0c3128] rounded-3xl p-6 sm:p-10 text-white border border-[#175244] shadow-xl">
            <div className="max-w-3xl mb-8">
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-[#d9ff4f]/15 text-[#d9ff4f] border border-[#d9ff4f]/30 font-space font-bold text-xs uppercase tracking-wider mb-3">
                <MapPin className="w-3.5 h-3.5" /> Geolocalização Inteligente
              </span>
              <h2 className="font-space font-bold text-2xl sm:text-3xl text-white mb-3">
                Você no controle de <em>onde quer rodar.</em>
              </h2>
              <p className="font-dm text-sm sm:text-base text-[#b8ccc3] leading-relaxed m-0">
                Cada motorista tem seus bairros prediletos e locais que prefere evitar. O GoOrNo lê o embarque e desembarque da chamada e aplica seu filtro de segurança e preferência instantaneamente.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
              {/* CARD 1: BAIRROS FAVORITOS */}
              <div className="p-6 rounded-2xl bg-[#092b24] border border-emerald-500/30 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="w-10 h-10 rounded-xl bg-amber-400/20 text-amber-300 border border-amber-400/30 flex items-center justify-center">
                      <Star className="w-5 h-5 fill-amber-400 text-amber-400" />
                    </span>
                    <span className="text-[11px] font-extrabold uppercase px-2.5 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                      Zona de Conforto
                    </span>
                  </div>
                  <h3 className="font-space font-bold text-lg text-white mb-2">
                    ⭐ Radar de Bairros Favoritos
                  </h3>
                  <p className="font-dm text-xs sm:text-sm text-[#b8ccc3] leading-relaxed mb-4">
                    Gosta de rodar perto de casa ou em regiões nobres e movimentadas? Cadastre seus bairros favoritos ou adicione com <strong>1 toque direto no histórico de corridas</strong>. Quando tocar uma viagem compatível, o card carimba com destaque dourado:
                  </p>
                </div>
                <div className="p-3 rounded-xl bg-[#051915] border border-emerald-500/40 text-xs font-mono text-emerald-300 flex items-center gap-2">
                  <Star className="w-4 h-4 text-amber-400 shrink-0 fill-amber-400" />
                  <span><strong>⭐ REGIÃO FAVORITA:</strong> Centro / Jardins</span>
                </div>
              </div>

              {/* CARD 2: ÁREAS DE RISCO */}
              <div className="p-6 rounded-2xl bg-[#092b24] border border-rose-500/30 flex flex-col justify-between">
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="w-10 h-10 rounded-xl bg-rose-500/20 text-rose-300 border border-rose-500/30 flex items-center justify-center">
                      <ShieldAlert className="w-5 h-5 text-rose-400" />
                    </span>
                    <span className="text-[11px] font-extrabold uppercase px-2.5 py-1 rounded-full bg-rose-500/20 text-rose-300 border border-rose-500/30">
                      Proteção Noturna
                    </span>
                  </div>
                  <h3 className="font-space font-bold text-lg text-white mb-2">
                    🚨 Escudo de Áreas de Risco
                  </h3>
                  <p className="font-dm text-xs sm:text-sm text-[#b8ccc3] leading-relaxed mb-4">
                    Evite entrar em becos perigosos ou bairros com histórico de assalto. O GoOrNo avisa antes de você aceitar a corrida se a origem ou o destino estiverem na sua lista de alerta, evitando sustos e emboscadas:
                  </p>
                </div>
                <div className="p-3 rounded-xl bg-[#051915] border border-rose-500/40 text-xs font-mono text-rose-300 flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
                  <span><strong>🚨 ATENÇÃO:</strong> Destino em Área de Risco</span>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* TELAS REAIS DO APLICATIVO */}
        <section className="screens-section section-shell" id="telas">
          <div className="section-intro center">
            <div className="eyebrow">Telas reais do aplicativo · 21 capturas oficiais</div>
            <h2>O que você vê é o que você usa.</h2>
            <p>
              Interface simples, leitura ultra rápida e sem distração no trânsito. Confira abaixo todas as 21 telas reais com Uber e 99.
            </p>
          </div>

          {/* DESTAQUE PRINCIPAL: MOCKUP COMPARATIVO (CARD FLUTUANTE) */}
          <div className="screens-stage">
            <div className="screen-caption caption-left">
              <span>01</span>
              <strong>Card flutuante</strong>
              <small>aparece sobre a sua chamada</small>
            </div>

            <img
              className="screen-image screen-back cursor-pointer"
              src="/assets/UberRuim.jpeg"
              alt="Tela do GoOrNo mostrando uma corrida ruim no trânsito"
              onClick={() => {
                const item = SCREENSHOTS.find((s) => s.id === 'uber-ruim');
                if (item) setSelectedScreenshot(item);
              }}
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/UberRuim.jpeg';
              }}
            />
            <img
              className="screen-image screen-front cursor-pointer"
              src="/assets/UberBoa.jpeg"
              alt="Tela do GoOrNo mostrando uma corrida boa aprovada em verde"
              onClick={() => {
                const item = SCREENSHOTS.find((s) => s.id === 'uber-boa');
                if (item) setSelectedScreenshot(item);
              }}
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/UberBoa.jpeg';
              }}
            />

            <div className="screen-caption caption-right">
              <span>02</span>
              <strong>Decisão instantânea</strong>
              <small>aceite com total segurança</small>
            </div>
          </div>

          {/* FILTROS DE CATEGORIA DAS 22 TELAS */}
          <div className="mt-14 mb-8">
            <div className="text-center mb-5">
              <span className="font-space font-bold text-xs uppercase tracking-widest text-[#638076] bg-[#e6ebdf] px-3.5 py-1.5 rounded-full border border-[rgba(7,29,25,0.08)] inline-flex items-center gap-2">
                <Sparkles className="w-3.5 h-3.5 text-[#799900]" />
                Explore Todas as 22 Telas Oficiais por Categoria
              </span>
            </div>
            <div className="flex flex-wrap items-center justify-center gap-2">
              {[
                { key: 'all', label: 'Todas as Telas (22)', icon: Sparkles },
                { key: 'cards_flutuantes', label: '🎛️ 4 Cards Flutuantes (7)', icon: Car },
                { key: 'streetview', label: '📍 Notificação & Street View (3)', icon: MapPin },
                { key: 'atalhos', label: '⚡ Atalhos & Trânsito (4)', icon: Zap },
                { key: 'safety', label: '🛡️ Segurança & Áreas de Risco (6)', icon: ShieldCheck },
                { key: 'config', label: '⚙️ Metas & Custos (2)', icon: Sliders }
              ].map((cat) => {
                const Icon = cat.icon;
                const isSelected = activeCategory === cat.key;
                return (
                  <button
                    key={cat.key}
                    onClick={() => setActiveCategory(cat.key)}
                    className={`px-3.5 py-2 rounded-xl text-xs sm:text-sm font-bold transition flex items-center gap-2 cursor-pointer border ${
                      isSelected
                        ? 'bg-[#071d19] text-[#d9ff4f] border-[#071d19] shadow-xs'
                        : 'bg-white border-[rgba(7,29,25,0.12)] text-[#556961] hover:text-[#071d19] hover:border-[#071d19]'
                    }`}
                  >
                    <Icon className="w-4 h-4" />
                    <span>{cat.label}</span>
                  </button>
                );
              })}
            </div>
          </div>

          {/* GRID COM OS CARDS DAS TELAS REAIS */}
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
            {filteredScreenshots.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelectedScreenshot(item)}
                className="bg-white border border-[rgba(7,29,25,0.12)] rounded-3xl p-3.5 shadow-xs hover:shadow-2xl transition-all duration-300 cursor-pointer flex flex-col justify-between group hover:-translate-y-1.5"
              >
                {/* MOLDURA DE SMARTPHONE REALISTA */}
                <div className="relative mx-auto w-full pt-1 pb-2">
                  <div className="relative mx-auto p-1.5 bg-[#0a110f] rounded-[28px] border-[2.5px] border-[#20342f] shadow-lg max-w-[210px] group-hover:border-[#799900]/80 transition-colors">
                    {/* Dynamic Island / Câmera */}
                    <div className="absolute top-2.5 left-1/2 -translate-x-1/2 w-12 h-2.5 bg-black rounded-full z-10 flex items-center justify-end pr-1 pointer-events-none">
                      <div className="w-1.5 h-1.5 rounded-full bg-[#1b2220]"></div>
                    </div>
                    {/* Tela interna */}
                    <div className="relative aspect-[9/18.5] rounded-[22px] overflow-hidden bg-black flex items-center justify-center">
                      <img
                        src={`/telas/${item.filename}`}
                        alt={item.title}
                        className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                        loading="lazy"
                        onError={(e) => {
                          (e.target as HTMLImageElement).src = '/assets/logo-goorno-clean.png';
                        }}
                      />
                    </div>
                  </div>

                  {/* Badge Flutuante no Topo */}
                  <div className="absolute top-3 left-2 right-2 flex items-center justify-between pointer-events-none z-10">
                    {item.badge && (
                      <span
                        className={`text-[9.5px] font-extrabold px-2 py-0.5 rounded-full shadow-md backdrop-blur-xs ${
                          item.badgeType === 'green'
                            ? 'bg-emerald-600 text-white'
                            : item.badgeType === 'red'
                            ? 'bg-rose-600 text-white'
                            : item.badgeType === 'amber'
                            ? 'bg-amber-600 text-white'
                            : item.badgeType === 'purple'
                            ? 'bg-purple-700 text-white'
                            : 'bg-[#071d19] text-[#d9ff4f]'
                        }`}
                      >
                        {item.badge}
                      </span>
                    )}
                    <span className="w-6 h-6 rounded-full bg-[#071d19]/80 text-white grid place-items-center opacity-0 group-hover:opacity-100 transition-opacity ml-auto">
                      <ZoomIn className="w-3.5 h-3.5" />
                    </span>
                  </div>
                </div>

                {/* CONTEÚDO DO CARD */}
                <div className="pt-2 flex-1 flex flex-col justify-between">
                  <div>
                    <span className="text-[10px] font-bold text-[#7a8982] uppercase tracking-wider block mb-1">
                      {item.categoryLabel}
                    </span>
                    <h3 className="font-space font-bold text-sm text-[#071d19] leading-snug mb-1.5 group-hover:text-emerald-800 transition">
                      {item.title}
                    </h3>
                    <p className="font-dm text-xs text-[#63756b] line-clamp-2 leading-relaxed">
                      {item.summary}
                    </p>
                  </div>

                  <div className="mt-3 pt-2.5 border-t border-[rgba(7,29,25,0.06)] flex items-center justify-between text-xs font-bold text-[#071d19]">
                    <span className="text-emerald-800">Toque para ampliar</span>
                    <ArrowRight className="w-4 h-4 text-[#7a8982] group-hover:translate-x-1 transition" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* SEÇÃO DE INSTALAÇÃO & APK */}
        <section className="install-section section-shell" id="instalacao">
          <div className="install-main">
            <div className="install-copy">
              <div className="eyebrow">Comece em menos de 1 minuto</div>
              <h2>
                Baixe. Ative.
                <br />
                <em>Rode tranquilo.</em>
              </h2>
              <p>
                O GoOrNo é distribuído por APK oficial direto porque precisa abrir sobre a Uber e a 99 com máxima velocidade e atualização em tempo real. Sem anúncios irritantes e sem mensalidade para testar.
              </p>
              <button
                onClick={handleDownloadApk}
                className="button button-primary cursor-pointer border-0"
                style={{ background: '#d9ff4f', color: '#071d19' }}
              >
                Baixar APK grátis <span>↓</span>
              </button>
            </div>
            <div className="steps">
              <article>
                <span>01</span>
                <h3>Baixe o APK</h3>
                <p>Toque no botão e confirme o download direto pelo navegador Chrome.</p>
              </article>
              <article>
                <span>02</span>
                <h3>Permita a instalação</h3>
                <p>Ative “Permitir desta fonte” quando o Android solicitar a permissão.</p>
              </article>
              <article>
                <span>03</span>
                <h3>Ative seus 7 dias</h3>
                <p>Informe seu WhatsApp e conceda as duas permissões do copiloto.</p>
              </article>
            </div>
          </div>

          {/* GUIA DE TRANSPARÊNCIA E SEGURANÇA DO APK (LARGURA TOTAL, SEM CORTE DE TEXTO E SEM BARRAS) */}
          <div className="install-security">
            <div className="text-center max-w-2xl mx-auto mb-8">
              <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 font-space font-bold text-xs uppercase tracking-wider mb-2">
                <ShieldCheck className="w-3.5 h-3.5" /> Segurança & Transparência
              </span>
              <h3 className="font-space font-bold text-xl sm:text-2xl text-white mb-2">
                Dúvidas sobre baixar o APK? Veja como é 100% seguro:
              </h3>
              <p className="font-dm text-sm text-[#b8ccc3]">
                Criamos um copiloto feito para quem trabalha duro. Sem pegadinhas, sem riscos e com total clareza.
              </p>
            </div>

            <div className="install-security-grid">
              <div className="install-security-card">
                <div className="install-security-icon bg-[#d9ff4f]/15 border border-[#d9ff4f]/30 text-[#d9ff4f]">
                  <HelpCircle className="w-5 h-5" />
                </div>
                <div className="install-security-content">
                  <h4>
                    O que é um arquivo APK?
                  </h4>
                  <p>
                    APK (Android Package Kit) é o formato oficial e original de instalação de aplicativos para celulares Android (assim como o instalador .exe no Windows). É o arquivo legítimo do GoOrNo que instala direto no seu smartphone.
                  </p>
                </div>
              </div>

              <div className="install-security-card">
                <div className="install-security-icon bg-amber-500/15 border border-amber-500/30 text-amber-400">
                  <AlertTriangle className="w-5 h-5" />
                </div>
                <div className="install-security-content">
                  <h4>
                    Aviso: "O arquivo pode ser nocivo"?
                  </h4>
                  <p>
                    Esse é um aviso preventivo <strong>padrão e genérico</strong> do Google Chrome para qualquer arquivo baixado fora da Play Store. Não é vírus: o GoOrNo foi feito exclusivamente para calcular corridas. Ele não pede senhas, não acessa contas bancárias nem contatos pessoais — apenas lê os dados da corrida na tela para projetar seu ganho em tempo real.
                  </p>
                </div>
              </div>

              <div className="install-security-card">
                <div className="install-security-icon bg-cyan-500/15 border border-cyan-500/30 text-cyan-300">
                  <Smartphone className="w-5 h-5" />
                </div>
                <div className="install-security-content">
                  <h4>
                    Aviso: "Permitir desta fonte"?
                  </h4>
                  <p>
                    O Android protege você exigindo autorização para o navegador Chrome abrir instaladores. Quando o alerta surgir, basta tocar em <strong>"Configurações"</strong> e ativar a chavinha <strong>"Permitir desta fonte"</strong>. Leva 3 segundos.
                  </p>
                </div>
              </div>

              <div className="install-security-card">
                <div className="install-security-icon bg-emerald-500/15 border border-emerald-500/30 text-emerald-400">
                  <ShieldCheck className="w-5 h-5" />
                </div>
                <div className="install-security-content">
                  <h4>
                    Privacidade & Foco na Corrida
                  </h4>
                  <p>
                    O GoOrNo funciona lendo os valores da chamada na tela. Não acessa suas contas bancárias, não pede suas senhas e não altera os aplicativos da Uber ou 99.
                  </p>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* SEÇÃO FAQ / DÚVIDAS FREQUENTES */}
        <section className="faq-section section-shell" id="duvidas">
          <div className="section-intro">
            <div className="eyebrow">Ainda ficou uma dúvida?</div>
            <h2>Transparência em primeiro lugar.</h2>
          </div>
          <div className="faq-list">
            <details
              open={openFaq === 0}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(0);
              }}
            >
              <summary>
                O GoOrNo realmente é gratuito para testar? <span>{openFaq === 0 ? '−' : '+'}</span>
              </summary>
              <p>
                Sim. Você tem 7 dias de teste completo e irrestrito. Não precisa cadastrar nenhum cartão de crédito nem pagar nada adiantado.
              </p>
            </details>

            <details
              open={openFaq === 1}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(1);
              }}
            >
              <summary>
                O aplicativo dá banimento na Uber ou 99? <span>{openFaq === 1 ? '−' : '+'}</span>
              </summary>
              <p>
                Não. O GoOrNo funciona de forma visual no seu aparelho, lendo os dados que aparecem na tela (assim como os seus olhos fazem) e calculando o lucro. Ele não intercepta servidores nem altera os aplicativos oficiais.
              </p>
            </details>

            <details
              open={openFaq === 2}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(2);
              }}
            >
              <summary>
                É seguro baixar o arquivo APK? Por que o celular avisa sobre “arquivo nocivo”? <span>{openFaq === 2 ? '−' : '+'}</span>
              </summary>
              <p>
                O aviso "O arquivo pode ser nocivo" é uma mensagem padrão e automática que o Google Chrome exibe para qualquer aplicativo baixado no formato APK fora da Play Store.
                <br /><br />
                Isso não significa vírus: o GoOrNo foi feito exclusivamente para calcular corridas. Ele não pede senhas, não acessa contas bancárias nem contatos pessoais — apenas lê os dados da corrida na tela para projetar seu ganho em tempo real. Basta tocar em <strong>"Fazer o download mesmo assim"</strong> e, ao instalar, ativar <strong>"Permitir desta fonte"</strong> nas configurações do Chrome.
              </p>
            </details>

            <details
              open={openFaq === 6}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(6);
              }}
            >
              <summary>
                Por que o GoOrNo é distribuído em APK e não somente pela Play Store? <span>{openFaq === 6 ? '−' : '+'}</span>
              </summary>
              <p>
                Distribuir o APK oficial direto pelo site permite aplicar melhorias e ajustes rápidos sempre que a Uber ou a 99 alteram suas telas ou regras, sem a demora da aprovação de lojas de apps. Além disso, você tem uma versão limpa, sem anúncios chatos e sem travas.
              </p>
            </details>

            <details
              open={openFaq === 8}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(8);
              }}
            >
              <summary>
                Como funciona o filtro de Bairros Favoritos e Áreas de Risco? <span>{openFaq === 8 ? '−' : '+'}</span>
              </summary>
              <p>
                O GoOrNo traz duas ferramentas essenciais de geolocalização para você ter total controle de onde roda:
                <br /><br />
                <strong>1. ⭐ Bairros Favoritos (Zona de Conforto):</strong> Cadastre os bairros onde você prefere trabalhar. Quando a corrida embarcar ou desembarcar nessa região, o card flutuante carimba com destaque dourado (ex: <em>"⭐ Região Favorita: Centro"</em>).
                <br /><br />
                <strong>2. 🚨 Áreas de Risco:</strong> Defina bairros ou locais que você prefere evitar. Ao tocar uma chamada para esses locais, o card emite alerta vermelho imediato para você recusar sem hesitar.
                <br /><br />
                <strong>Facilidade máxima:</strong> Além de configurar manualmente no menu de Segurança, você pode marcar qualquer endereço como Favorito ou Risco com <strong>1 toque direto no Histórico de Corridas</strong> do aplicativo!
              </p>
            </details>

            <details
              open={openFaq === 7}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(7);
              }}
            >
              <summary>
                Como funciona o suporte se eu tiver alguma dúvida? <span>{openFaq === 7 ? '−' : '+'}</span>
              </summary>
              <p>
                O GoOrNo conta com um canal de <strong>Caixa Postal</strong> direto (pelo botão "Fale Conosco" no rodapé do site e dentro do aplicativo). Todas as mensagens, dúvidas e sugestões são enviadas diretamente para o desenvolvedor do app e respondidas assim que possível pelo seu canal de preferência (WhatsApp ou e-mail).
              </p>
            </details>

            <details
              open={openFaq === 3}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(3);
              }}
            >
              <summary>
                Como funcionam as atualizações do GoOrNo? <span>{openFaq === 3 ? '−' : '+'}</span>
              </summary>
              <p>
                O GoOrNo verifica atualizações automaticamente ao iniciar. Assim que uma nova versão estiver disponível, surge um aviso direto na sua tela e você atualiza em 1 clique sem perder suas configurações.
              </p>
            </details>

            <details
              open={openFaq === 4}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(4);
              }}
            >
              <summary>
                Quais celulares são compatíveis? <span>{openFaq === 4 ? '−' : '+'}</span>
              </summary>
              <p>
                Qualquer smartphone Android com versão 8.0 ou superior (Samsung, Motorola, Xiaomi, Realme, etc.). Basta conceder a permissão de sobreposição de tela e leitor de acessibilidade.
              </p>
            </details>

            <details
              open={openFaq === 5}
              onClick={(e) => {
                e.preventDefault();
                toggleFaq(5);
              }}
            >
              <summary>
                Como funciona o pagamento? A liberação do plano é automática? <span>{openFaq === 5 ? '−' : '+'}</span>
              </summary>
              <p>
                Sim, 100% automático e imediato! Ao pagar via PIX qualquer um dos planos oficiais (Mensal, Trimestral, Semestral ou Anual), o gateway bancário do Asaas confirma o recebimento em poucos segundos e o aplicativo libera o seu acesso na mesma hora, sem precisar enviar comprovante ou aguardar aprovação manual. Durante a promoção de lançamento, você garante desconto exclusivo na primeira contratação!
              </p>
            </details>
          </div>
        </section>

                {/* SEÇÃO DO FOLDER OFICIAL DE DIVULGAÇÃO & COMPARTILHAMENTO */}
        <section className="section-shell" id="folder">
          <div className="bg-gradient-to-br from-[#071d19] via-[#0c2f27] to-[#071d19] border border-[#d9ff4f]/30 rounded-3xl p-6 sm:p-10 text-white relative overflow-hidden shadow-2xl">
            {/* Elementos decorativos */}
            <div className="absolute top-0 right-0 w-96 h-96 bg-[#d9ff4f]/5 rounded-full blur-3xl pointer-events-none" />
            <div className="absolute bottom-0 left-0 w-80 h-80 bg-emerald-500/5 rounded-full blur-3xl pointer-events-none" />

            <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-center relative z-10">
              {/* Coluna 1: Preview do Folder */}
              <div className="lg:col-span-5 flex justify-center">
                <div
                  className="relative group cursor-pointer max-w-[270px] sm:max-w-[310px]"
                  onClick={() => setIsFolderModalOpen(true)}
                >
                  <div className="absolute -inset-2 bg-gradient-to-r from-[#d9ff4f]/40 to-emerald-400/40 rounded-3xl blur-md group-hover:blur-lg transition opacity-75" />
                  <div className="relative rounded-2xl overflow-hidden border-2 border-[#d9ff4f] bg-[#071d19] shadow-2xl">
                    <img
                      src="/folder-goorno.png"
                      alt="Folder Oficial GoOrNo com QR Code"
                      className="w-full h-auto object-contain block transform group-hover:scale-[1.02] transition duration-300"
                    />
                  </div>
                  <div className="absolute bottom-3 left-3 right-3 bg-[#071d19]/90 backdrop-blur-md border border-white/20 rounded-xl py-1.5 px-3 text-center text-[11px] font-space font-bold text-[#d9ff4f] shadow-lg">
                    🔍 Toque para ampliar o folder
                  </div>
                </div>
              </div>

              {/* Coluna 2: Informações e Ações de Compartilhamento */}
              <div className="lg:col-span-7 flex flex-col justify-center space-y-5">
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-[#d9ff4f]/15 border border-[#d9ff4f]/30 text-[#d9ff4f] font-space font-bold text-xs uppercase w-fit">
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>Material Oficial de Divulgação</span>
                </div>

                <h3 className="font-space font-black text-2xl sm:text-3xl text-white tracking-tight leading-tight m-0">
                  Folder com QR Code do GoOrNo
                </h3>

                <p className="font-dm text-sm sm:text-base text-[#b8ccc3] leading-relaxed m-0">
                  Quer apresentar o GoOrNo para colegas motoristas, imprimir panfletos ou compartilhar nos grupos de WhatsApp? Use o nosso folder oficial de alta resolução com QR Code apontando diretamente para o teste grátis de 7 dias.
                </p>

                {/* Destaques rápidos */}
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                  <div className="bg-white/5 border border-white/10 rounded-xl p-3 flex items-start gap-2.5">
                    <div className="p-1.5 rounded-lg bg-[#d9ff4f]/20 text-[#d9ff4f] shrink-0 mt-0.5">
                      <Check className="w-3.5 h-3.5" />
                    </div>
                    <div>
                      <h5 className="font-space font-bold text-xs text-white m-0 mb-0.5">QR Code Integrado</h5>
                      <p className="font-dm text-[11px] text-[#b8ccc3] m-0">Basta apontar a câmera do celular para abrir o site na hora.</p>
                    </div>
                  </div>

                  <div className="bg-white/5 border border-white/10 rounded-xl p-3 flex items-start gap-2.5">
                    <div className="p-1.5 rounded-lg bg-emerald-500/20 text-emerald-300 shrink-0 mt-0.5">
                      <Check className="w-3.5 h-3.5" />
                    </div>
                    <div>
                      <h5 className="font-space font-bold text-xs text-white m-0 mb-0.5">Alta Resolução</h5>
                      <p className="font-dm text-[11px] text-[#b8ccc3] m-0">Pronto para envio digital ou impressão gráfica nítida.</p>
                    </div>
                  </div>
                </div>

                {/* Botões de Ação */}
                <div className="flex flex-wrap items-center gap-3 pt-2">
                  <a
                    href="/folder-goorno.png"
                    download="Folder-Oficial-GoOrNo.png"
                    className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-black text-xs sm:text-sm px-5 py-3 rounded-xl transition inline-flex items-center gap-2 no-underline shadow-md"
                  >
                    <Download className="w-4 h-4" /> Baixar Folder (PNG)
                  </a>

                  <button
                    onClick={() => {
                      const shareText = "Conheça o GoOrNo: Seu Copiloto Inteligente para Uber e 99! Acesse https://goorno-app.vercel.app/ e teste 7 dias grátis.";
                      if (navigator.share) {
                        navigator.share({
                          title: "Folder Oficial GoOrNo",
                          text: shareText,
                          url: "https://goorno-app.vercel.app/folder-goorno.png"
                        }).catch(() => {});
                      } else {
                        window.open(`https://api.whatsapp.com/send?text=${encodeURIComponent(shareText + "\n\nVeja o folder: https://goorno-app.vercel.app/folder-goorno.png")}`, '_blank');
                      }
                    }}
                    className="bg-emerald-600 hover:bg-emerald-500 text-white font-space font-bold text-xs sm:text-sm px-4 py-3 rounded-xl transition cursor-pointer border-0 inline-flex items-center gap-2 shadow-sm"
                  >
                    <Share2 className="w-4 h-4" /> Compartilhar no WhatsApp
                  </button>

                  <button
                    onClick={() => {
                      navigator.clipboard.writeText("https://goorno-app.vercel.app/folder-goorno.png");
                      alert("Link do folder copiado para a área de transferência!");
                    }}
                    className="bg-white/10 hover:bg-white/20 text-white font-space font-bold text-xs sm:text-sm px-4 py-3 rounded-xl transition cursor-pointer border border-white/20 inline-flex items-center gap-2"
                  >
                    <Copy className="w-4 h-4" /> Copiar Link da Imagem
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>

        {/* BANNER CTA FINAL */}
        <section className="final-cta section-shell">
          <div>
            <div className="eyebrow">A próxima corrida começa aqui</div>
            <h2>
              Pare de adivinhar.
              <br />
              <em>Comece a decidir.</em>
            </h2>
          </div>
          <div className="flex flex-col items-center sm:items-end gap-3">
            <button
              onClick={handleDownloadApk}
              className="button button-light cursor-pointer border-0"
            >
              Baixar GoOrNo grátis <span>↗</span>
            </button>
            <div className="flex items-center gap-2 text-xs text-[#b8ccc3]">
              <ShieldCheck className="w-4 h-4 text-[#d9ff4f]" />
              <span>APK Oficial • Instalação Rápida • 7 Dias Grátis</span>
            </div>
          </div>
        </section>
      </main>

            {/* LIGHTBOX DO FOLDER OFICIAL */}
      {isFolderModalOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/90 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6 animate-fadeIn"
          onClick={() => setIsFolderModalOpen(false)}
        >
          <div
            className="bg-[#071d19] border border-white/20 rounded-3xl p-4 max-w-md w-full max-h-[95vh] flex flex-col items-center shadow-2xl relative"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={() => setIsFolderModalOpen(false)}
              className="absolute top-4 right-4 bg-white/10 hover:bg-white/25 text-white p-2 rounded-full border-0 cursor-pointer transition z-20"
            >
              <X className="w-5 h-5" />
            </button>
            <div className="overflow-y-auto max-h-[75vh] w-full flex justify-center py-2 no-scrollbar">
              <img
                src="/folder-goorno.png"
                alt="Folder GoOrNo com QR Code"
                className="w-full h-auto max-w-[340px] rounded-xl shadow-lg border border-[#d9ff4f]/40"
              />
            </div>
            <div className="flex items-center gap-3 mt-4 w-full justify-center">
              <a
                href="/folder-goorno.png"
                download="Folder-Oficial-GoOrNo.png"
                className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-black text-xs px-4 py-2.5 rounded-xl transition inline-flex items-center gap-1.5 no-underline shadow-xs"
              >
                <Download className="w-4 h-4" /> Baixar PNG
              </a>
              <button
                onClick={() => {
                  navigator.clipboard.writeText("https://goorno-app.vercel.app/folder-goorno.png");
                  alert("Link do folder copiado!");
                }}
                className="bg-white/10 hover:bg-white/20 text-white font-space font-bold text-xs px-4 py-2.5 rounded-xl border border-white/20 cursor-pointer"
              >
                Copiar Link
              </button>
            </div>
          </div>
        </div>
      )}

            {/* FOOTER */}
      <footer className="site-footer section-shell">
        <div className="brand footer-brand">
          <img
            src="/assets/logo-goorno-clean.png"
            alt="GoOrNo"
            onError={(e) => {
              (e.target as HTMLImageElement).src = '/logo-goorno-clean.png';
            }}
          />
          <span>
            GoOrNo <small>Copiloto de rentabilidade</small>
          </span>
        </div>

        <p className="text-xs text-[#7a8982] m-0">© 2026 GoOrNo. Feito para quem trabalha na rua.</p>

        <div className="flex items-center gap-3 text-xs font-space font-bold">
          <button
            onClick={() => setLegalModalType('terms')}
            className="bg-transparent border-0 text-[#071d19] hover:text-[#799900] cursor-pointer p-0 underline"
          >
            Termos
          </button>
          <span className="text-slate-300">•</span>
          <button
            onClick={() => setLegalModalType('privacy')}
            className="bg-transparent border-0 text-[#071d19] hover:text-[#799900] cursor-pointer p-0 underline"
          >
            Privacidade
          </button>
          <span className="text-slate-300">•</span>
          <button
            onClick={() => setLegalModalType('independence')}
            className="bg-transparent border-0 text-[#071d19] hover:text-[#799900] cursor-pointer p-0 underline"
          >
            Independência
          </button>
          <span className="text-slate-300">•</span>
          <button
            onClick={() => setIsContactOpen(true)}
            className="bg-transparent border-0 text-[#071d19] hover:text-[#799900] cursor-pointer flex items-center gap-1"
          >
            <Mail className="w-3.5 h-3.5" />
            <span>Contato</span>
          </button>
          {onOpenAdmin && (
            <>
              <span className="text-slate-300">•</span>
              <button
                onClick={onOpenAdmin}
                className="bg-transparent border-0 text-[#7a8982] hover:text-[#071d19] transition cursor-pointer flex items-center gap-1"
                title="Acesso Administrativo"
              >
                <Lock className="w-3 h-3" />
                <span>Painel</span>
              </button>
            </>
          )}
        </div>
      </footer>

      {/* MODAL DE FALE CONOSCO (CAIXA POSTAL DIRETA) */}
      <ContactModal
        isOpen={isContactOpen}
        onClose={() => setIsContactOpen(false)}
        source="web"
      />

      {/* LIGHTBOX MODAL PARA AMPLIAR TELAS REAIS */}
      {selectedScreenshot && (
        <div
          className="fixed inset-0 z-50 bg-black/85 backdrop-blur-xs flex items-center justify-center p-2 sm:p-4 md:p-6 animate-fadeIn"
          onClick={() => setSelectedScreenshot(null)}
        >
          <div
            className="bg-[#071d19] text-white border border-white/15 rounded-2xl sm:rounded-3xl max-w-4xl w-full max-h-[92vh] flex flex-col md:flex-row shadow-2xl relative overflow-hidden no-scrollbar"
            onClick={(e) => e.stopPropagation()}
          >
            {/* BOTÃO FECHAR FIXO */}
            <button
              onClick={() => setSelectedScreenshot(null)}
              className="absolute top-3 right-3 z-30 w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-black/75 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 shadow-md"
              title="Fechar"
              aria-label="Fechar"
            >
              <X className="w-4 h-4 sm:w-5 sm:h-5" />
            </button>

            {/* ÁREA DA IMAGEM COM MOLDURA DE SMARTPHONE */}
            <div className="w-full md:w-1/2 bg-black/60 flex items-center justify-center p-4 relative shrink-0 min-h-[300px] md:min-h-[520px]">
              {/* Smartphone Frame Ampliado */}
              <div className="relative p-2.5 bg-[#09110e] rounded-[38px] border-[3.5px] border-[#223530] shadow-2xl max-w-[260px] sm:max-w-[280px]">
                {/* Dynamic Island */}
                <div className="absolute top-3.5 left-1/2 -translate-x-1/2 w-16 h-3 bg-black rounded-full z-20 flex items-center justify-end pr-1 pointer-events-none">
                  <div className="w-1.5 h-1.5 rounded-full bg-[#1b2220]"></div>
                </div>
                <div className="relative aspect-[9/18.5] rounded-[30px] overflow-hidden bg-black flex items-center justify-center">
                  <img
                    src={`/telas/${selectedScreenshot.filename}`}
                    alt={selectedScreenshot.title}
                    className="w-full h-full object-contain"
                  />
                </div>
              </div>
              <button
                onClick={handlePrevScreenshot}
                className="absolute left-2.5 top-1/2 -translate-y-1/2 w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-black/70 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 z-10 shadow-md"
                title="Imagem anterior"
                aria-label="Imagem anterior"
              >
                <ChevronLeft className="w-4 h-4 sm:w-5 sm:h-5" />
              </button>
              <button
                onClick={handleNextScreenshot}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-black/70 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 z-10 shadow-md"
                title="Próxima imagem"
                aria-label="Próxima imagem"
              >
                <ChevronRight className="w-4 h-4 sm:w-5 sm:h-5" />
              </button>
            </div>

            {/* DETALHES DA TELA SEM BARRA DE ROLAGEM */}
            <div className="w-full md:w-1/2 p-4 sm:p-5 md:p-6 flex flex-col justify-between overflow-y-auto no-scrollbar">
              <div className="space-y-2.5 sm:space-y-3">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[11px] sm:text-xs font-bold text-[#c2d0ca] uppercase tracking-wider font-dm">
                    {selectedScreenshot.categoryLabel}
                  </span>
                  {selectedScreenshot.badge && (
                    <span
                      className={`text-[10px] font-extrabold px-2.5 py-0.5 rounded-full ${
                        selectedScreenshot.badgeType === 'green'
                          ? 'bg-emerald-600 text-white'
                          : selectedScreenshot.badgeType === 'red'
                          ? 'bg-rose-600 text-white'
                          : selectedScreenshot.badgeType === 'amber'
                          ? 'bg-amber-600 text-white'
                          : selectedScreenshot.badgeType === 'purple'
                          ? 'bg-purple-700 text-white'
                          : 'bg-[#d9ff4f] text-[#071d19]'
                      }`}
                    >
                      {selectedScreenshot.badge}
                    </span>
                  )}
                </div>

                <h2 className="font-space text-lg sm:text-xl md:text-2xl font-bold text-white leading-tight">
                  {selectedScreenshot.title}
                </h2>

                <div className="bg-white/5 border border-white/10 rounded-xl p-3 sm:p-3.5">
                  <h4 className="text-[10px] sm:text-[11px] font-bold text-[#d9ff4f] uppercase mb-1 font-space">
                    O que esta tela mostra:
                  </h4>
                  <p className="text-xs sm:text-[13px] text-[#c2d0ca] leading-relaxed font-dm m-0">
                    {selectedScreenshot.summary}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-xl p-3 sm:p-3.5">
                  <h4 className="text-[10px] sm:text-[11px] font-bold text-white uppercase mb-1 flex items-center gap-1.5 font-space">
                    <Sparkles className="w-3.5 h-3.5 text-[#d9ff4f]" />
                    Como isso protege o seu bolso:
                  </h4>
                  <p className="text-xs sm:text-[13px] text-[#c2d0ca] leading-relaxed font-dm m-0">
                    {selectedScreenshot.highlightText}
                  </p>
                </div>
              </div>

              <div className="pt-3 sm:pt-4 mt-3 sm:mt-4 border-t border-white/10 flex items-center justify-between gap-3">
                <span className="text-[11px] sm:text-xs text-[#7a8982] truncate">
                  {selectedScreenshot.filename}
                </span>
                <button
                  onClick={handleDownloadApk}
                  className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-bold text-xs px-4 py-2.5 rounded-full transition shadow-xs cursor-pointer border-0 shrink-0"
                >
                  Baixar APK Oficial ↗
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* MODAL JURÍDICO & LGPD (TERMOS, PRIVACIDADE, INDEPENDÊNCIA E EXCLUSÃO) */}
      {legalModalType && (
        <div
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-xs flex items-center justify-center p-3 sm:p-5 animate-fadeIn"
          onClick={() => setLegalModalType(null)}
        >
          <div
            className="bg-white text-slate-800 rounded-3xl max-w-3xl w-full max-h-[90vh] flex flex-col shadow-2xl overflow-hidden border border-slate-200"
            onClick={(e) => e.stopPropagation()}
          >
            {/* CABEÇALHO DO MODAL */}
            <div className="p-5 sm:p-6 bg-[#071d19] text-white flex items-center justify-between border-b border-emerald-950 shrink-0">
              <div className="flex items-center gap-3">
                <span className="text-2xl">
                  {legalModalType === 'terms' && '📜'}
                  {legalModalType === 'privacy' && '🛡️'}
                  {legalModalType === 'independence' && '⚖️'}
                  {legalModalType === 'delete' && '⚠️'}
                </span>
                <div>
                  <h3 className="font-space font-bold text-lg sm:text-xl text-white m-0">
                    {legalModalType === 'terms' && 'Termos de Uso do GoOrNo'}
                    {legalModalType === 'privacy' && 'Política de Privacidade & LGPD'}
                    {legalModalType === 'independence' && 'Natureza Jurídica, Independência e Funcionamento'}
                    {legalModalType === 'delete' && 'Solicitação de Exclusão de Conta e Dados (LGPD)'}
                  </h3>
                  <span className="text-xs text-[#a2b5ac] font-dm">
                    {legalModalType === 'delete'
                      ? 'Direito de Eliminação - Art. 18 da Lei 13.709/2018'
                      : 'Transparência, Legalidade e Autonomia do Motorista'}
                  </span>
                </div>
              </div>
              <button
                onClick={() => setLegalModalType(null)}
                className="w-9 h-9 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition cursor-pointer border-0"
                title="Fechar"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* CORPO DO TEXTO SCROLLÁVEL */}
            <div className="p-6 sm:p-8 overflow-y-auto font-dm text-sm leading-relaxed text-slate-700 space-y-4">
              {legalModalType === 'terms' && (
                <div className="whitespace-pre-line text-xs sm:text-sm font-sans text-slate-800 leading-relaxed bg-slate-50 p-4 sm:p-6 rounded-2xl border border-slate-200">
                  {TERMS_OF_USE_TEXT}
                </div>
              )}

              {legalModalType === 'privacy' && (
                <div className="whitespace-pre-line text-xs sm:text-sm font-sans text-slate-800 leading-relaxed bg-slate-50 p-4 sm:p-6 rounded-2xl border border-slate-200">
                  {PRIVACY_POLICY_TEXT}
                </div>
              )}

              {legalModalType === 'independence' && (
                <div className="space-y-4">
                  <div className="p-4 rounded-2xl bg-amber-50 border border-amber-200 text-amber-900 text-xs sm:text-sm leading-relaxed">
                    <strong>Resumo Executivo:</strong> {INDEPENDENCE_DISCLAIMER.summary}
                  </div>
                  <div className="whitespace-pre-line text-xs sm:text-sm font-sans text-slate-800 leading-relaxed bg-slate-50 p-4 sm:p-6 rounded-2xl border border-slate-200">
                    {INDEPENDENCE_DISCLAIMER.fullText}
                  </div>
                </div>
              )}

              {legalModalType === 'delete' && (
                <div className="space-y-4">
                  <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-900 text-xs sm:text-sm leading-relaxed">
                    <strong>Atenção:</strong> Conforme o Artigo 18 da LGPD, a exclusão da sua conta remove permanentemente sua licença, seu vínculo de telefone/placa e todos os backups das suas metas na nuvem. Esta operação é irreversível.
                  </div>

                  <form
                    onSubmit={async (e) => {
                      e.preventDefault();
                      setDeleteLoading(true);
                      setDeleteFeedback(null);
                      try {
                        const res = await fetch('/api/license/delete-account', {
                          method: 'POST',
                          headers: { 'Content-Type': 'application/json' },
                          body: JSON.stringify({
                            phone: deletePhone,
                            pin: deletePin,
                            secret: deleteSecret,
                          }),
                        });
                        const data = await res.json();
                        if (res.ok && data.success) {
                          setDeleteFeedback('Conta e dados pessoais excluídos com sucesso do banco de dados do GoOrNo.');
                          setDeletePhone('');
                          setDeletePin('');
                          setDeleteSecret('');
                        } else {
                          setDeleteFeedback(data.message || 'Erro ao processar exclusão. Verifique os dados informados.');
                        }
                      } catch (err: any) {
                        setDeleteFeedback('Erro de conexão ao solicitar exclusão. Tente novamente mais tarde.');
                      } finally {
                        setDeleteLoading(false);
                      }
                    }}
                    className="space-y-3 bg-slate-50 p-5 rounded-2xl border border-slate-200"
                  >
                    <div>
                      <label className="block text-xs font-bold text-slate-700 mb-1">
                        Número de Telefone / WhatsApp (ex: 11999999999)
                      </label>
                      <input
                        type="text"
                        required
                        value={deletePhone}
                        onChange={(e) => setDeletePhone(e.target.value)}
                        placeholder="Seu telefone cadastrado"
                        className="w-full px-3 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:border-emerald-600"
                      />
                    </div>

                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <div>
                        <label className="block text-xs font-bold text-slate-700 mb-1">
                          PIN de 4 dígitos
                        </label>
                        <input
                          type="password"
                          maxLength={6}
                          value={deletePin}
                          onChange={(e) => setDeletePin(e.target.value)}
                          placeholder="Seu PIN de acesso"
                          className="w-full px-3 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:border-emerald-600"
                        />
                      </div>
                      <div>
                        <label className="block text-xs font-bold text-slate-700 mb-1">
                          Placa do Carro ou Palavra-Chave
                        </label>
                        <input
                          type="text"
                          value={deleteSecret}
                          onChange={(e) => setDeleteSecret(e.target.value)}
                          placeholder="Placa cadastrada"
                          className="w-full px-3 py-2 text-sm border border-slate-300 rounded-xl focus:outline-none focus:border-emerald-600"
                        />
                      </div>
                    </div>

                    {deleteFeedback && (
                      <div className="p-3 rounded-xl bg-slate-200 text-xs text-slate-800 font-medium">
                        {deleteFeedback}
                      </div>
                    )}

                    <div className="pt-2">
                      <button
                        type="submit"
                        disabled={deleteLoading}
                        className="w-full py-2.5 px-4 rounded-xl bg-rose-600 hover:bg-rose-700 text-white font-space font-bold text-xs transition cursor-pointer border-0 shadow-sm disabled:opacity-50"
                      >
                        {deleteLoading ? 'Processando exclusão...' : 'Confirmar Exclusão Definitiva (LGPD)'}
                      </button>
                    </div>
                  </form>
                </div>
              )}
            </div>

            {/* RODAPÉ DO MODAL */}
            <div className="p-4 sm:p-5 bg-slate-50 border-t border-slate-200 flex items-center justify-between text-xs text-slate-500 shrink-0">
              <span>Canal Oficial de Privacidade: {LEGAL_METADATA.contactEmail}</span>
              <button
                onClick={() => setLegalModalType(null)}
                className="px-4 py-2 rounded-xl bg-[#071d19] text-white hover:bg-[#0c3128] font-space font-bold text-xs transition cursor-pointer border-0"
              >
                Fechar
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
}

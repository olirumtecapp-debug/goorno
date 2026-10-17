import React, { useState, useEffect } from 'react';
import {
  Download,
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
  Zap
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
  const [appVersion, setAppVersion] = useState<string>('3.3.12');

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
    // Redireciona diretamente para o link de download oficial do APK v3.3.7
    window.location.href = '/download/apk';
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
          <div className="comparison-grid">
            <article className="comparison-card good">
              <div className="card-label">● VIAGEM BOA</div>
              <div className="card-price">
                R$ 2,17<span>/km</span>
              </div>
              <h3>Vale a pena aceitar</h3>
              <p>R$ 10,83 por 5 km totais. O GoOrNo aprova em verde.</p>
              <div className="card-result">
                ACEITAR <span>↗</span>
              </div>
            </article>

            <article className="comparison-card bad">
              <div className="card-label">● VIAGEM RUIM</div>
              <div className="card-price">
                R$ 1,25<span>/km</span>
              </div>
              <h3>Melhor recusar</h3>
              <p>R$ 11,25 por 9 km totais. Não pague para trabalhar.</p>
              <div className="card-result">
                RECUSAR <span>↘</span>
              </div>
            </article>
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

          {/* FILTROS DE CATEGORIA DAS 21 TELAS */}
          <div className="mt-14 mb-8">
            <div className="text-center mb-5">
              <span className="font-space font-bold text-xs uppercase tracking-widest text-[#638076] bg-[#e6ebdf] px-3.5 py-1.5 rounded-full border border-[rgba(7,29,25,0.08)] inline-flex items-center gap-2">
                <Sparkles className="w-3.5 h-3.5 text-[#799900]" />
                Explore Todas as 21 Telas Oficiais por Categoria
              </span>
            </div>
            <div className="flex flex-wrap items-center justify-center gap-2">
              {[
                { key: 'all', label: 'Todas as Telas (21)', icon: Sparkles },
                { key: 'uber_99', label: '🚗 Uber & 99 na Prática (7)', icon: Car },
                { key: 'streetview', label: '📍 Notificação & Street View (3)', icon: MapPin },
                { key: 'config', label: '⚙️ Metas & Calculadora (5)', icon: Sliders },
                { key: 'safety', label: '🛡️ Segurança & Auditoria (6)', icon: ShieldCheck }
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
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
            {filteredScreenshots.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelectedScreenshot(item)}
                className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl overflow-hidden shadow-xs hover:shadow-xl transition-all duration-200 cursor-pointer flex flex-col group hover:-translate-y-1"
              >
                {/* IMAGEM COM BADGE */}
                <div className="relative aspect-[9/16] bg-[#fbfcf7] overflow-hidden flex items-center justify-center p-2.5 border-b border-[rgba(7,29,25,0.06)]">
                  <img
                    src={`/telas/${item.filename}`}
                    alt={item.title}
                    className="w-full h-full object-contain rounded-lg group-hover:scale-[1.02] transition-transform duration-300"
                    loading="lazy"
                    onError={(e) => {
                      (e.target as HTMLImageElement).src = '/assets/logo-goorno-clean.png';
                    }}
                  />
                  <div className="absolute top-3 left-3 right-3 flex items-center justify-between pointer-events-none">
                    {item.badge && (
                      <span
                        className={`text-[10px] font-extrabold px-2.5 py-1 rounded-full shadow-xs backdrop-blur-xs ${
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
                    <span className="w-7 h-7 rounded-full bg-[#071d19]/80 text-white grid place-items-center opacity-0 group-hover:opacity-100 transition-opacity ml-auto">
                      <ZoomIn className="w-4 h-4" />
                    </span>
                  </div>
                </div>

                {/* CONTEÚDO DO CARD */}
                <div className="p-4 flex-1 flex flex-col justify-between">
                  <div>
                    <span className="text-[10px] font-bold text-[#7a8982] uppercase tracking-wider block mb-1">
                      {item.categoryLabel}
                    </span>
                    <h3 className="font-space font-bold text-sm text-[#071d19] leading-snug mb-2 group-hover:text-emerald-800 transition">
                      {item.title}
                    </h3>
                    <p className="font-dm text-xs text-[#63756b] line-clamp-2 leading-relaxed">
                      {item.summary}
                    </p>
                  </div>

                  <div className="mt-3 pt-3 border-t border-[rgba(7,29,25,0.06)] flex items-center justify-between text-xs font-bold text-[#071d19]">
                    <span>Toque para ampliar</span>
                    <ArrowRight className="w-4 h-4 text-[#7a8982] group-hover:translate-x-1 transition" />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </section>

        {/* SEÇÃO DE INSTALAÇÃO & APK */}
        <section className="install-section section-shell" id="instalacao">
          <div className="install-copy">
            <div className="eyebrow">Comece em menos de 1 minuto</div>
            <h2>
              Baixe. Ative.
              <br />
              <em>Rode tranquilo.</em>
            </h2>
            <p>
              O GoOrNo é distribuído por APK oficial direto porque precisa abrir sobre a Uber e a 99. Sem anúncios irritantes e sem mensalidade para testar.
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
                Por que o celular avisa sobre “aplicativo nocivo”? <span>{openFaq === 2 ? '−' : '+'}</span>
              </summary>
              <p>
                Esse é um aviso padrão que o Google Play Protect exibe para qualquer aplicativo baixado em formato APK fora da Play Store. Baixe sempre pelo botão oficial desta página para ter 100% de garantia de segurança e autenticidade.
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
          <button
            onClick={handleDownloadApk}
            className="button button-light cursor-pointer border-0"
          >
            Baixar GoOrNo grátis <span>↗</span>
          </button>
        </section>
      </main>

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
            GoOrNo <small>Copiloto de ganhos</small>
          </span>
        </div>

        <p>© 2026 GoOrNo. Feito para quem trabalha na rua.</p>

        <div className="flex items-center gap-4">
          <button
            onClick={() => setIsContactOpen(true)}
            className="bg-transparent border-0 text-[#071d19] font-bold text-xs hover:underline cursor-pointer flex items-center gap-1"
          >
            <Mail className="w-3.5 h-3.5 text-[#071d19]" />
            Fale conosco ↗
          </button>

          {onOpenAdmin && (
            <button
              onClick={onOpenAdmin}
              className="bg-transparent border-0 text-[#7a8982] hover:text-[#071d19] text-xs transition cursor-pointer flex items-center gap-1"
              title="Acesso Administrativo"
            >
              <Lock className="w-3 h-3" />
              <span>Painel</span>
            </button>
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
          className="fixed inset-0 z-50 bg-black/80 backdrop-blur-xs flex items-center justify-center p-3 sm:p-6"
          onClick={() => setSelectedScreenshot(null)}
        >
          <div
            className="bg-[#071d19] text-white border border-white/15 rounded-3xl max-w-4xl w-full max-h-[92vh] overflow-hidden flex flex-col md:flex-row shadow-2xl relative"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              onClick={() => setSelectedScreenshot(null)}
              className="absolute top-3 right-3 z-20 w-9 h-9 rounded-full bg-white/20 hover:bg-white/30 text-white grid place-items-center transition cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>

            <div className="md:w-1/2 bg-black/50 flex items-center justify-center p-4 relative min-h-[350px] md:min-h-[550px]">
              <img
                src={`/telas/${selectedScreenshot.filename}`}
                alt={selectedScreenshot.title}
                className="max-h-[75vh] w-auto max-w-full object-contain rounded-xl shadow-lg"
              />
              <button
                onClick={handlePrevScreenshot}
                className="absolute left-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/60 hover:bg-black/90 text-white grid place-items-center transition cursor-pointer"
                title="Imagem anterior"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button
                onClick={handleNextScreenshot}
                className="absolute right-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/60 hover:bg-black/90 text-white grid place-items-center transition cursor-pointer"
                title="Próxima imagem"
              >
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>

            <div className="md:w-1/2 p-6 sm:p-8 flex flex-col justify-between overflow-y-auto">
              <div>
                <div className="flex items-center gap-2 mb-3">
                  <span className="text-xs font-bold text-[#c2d0ca] uppercase tracking-wider font-dm">
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

                <h2 className="font-space text-xl sm:text-2xl font-bold text-white leading-tight mb-3">
                  {selectedScreenshot.title}
                </h2>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4 mb-4">
                  <h4 className="text-xs font-bold text-[#d9ff4f] uppercase mb-1 font-space">
                    O que esta tela mostra:
                  </h4>
                  <p className="text-sm text-[#c2d0ca] leading-relaxed font-dm">
                    {selectedScreenshot.summary}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <h4 className="text-xs font-bold text-white uppercase mb-1 flex items-center gap-1.5 font-space">
                    <Sparkles className="w-3.5 h-3.5 text-[#d9ff4f]" />
                    Como isso protege o seu bolso:
                  </h4>
                  <p className="text-xs sm:text-sm text-[#c2d0ca] leading-relaxed font-dm">
                    {selectedScreenshot.highlightText}
                  </p>
                </div>
              </div>

              <div className="pt-6 mt-6 border-t border-white/10 flex items-center justify-between">
                <span className="text-xs text-[#7a8982]">
                  {selectedScreenshot.filename}
                </span>
                <button
                  onClick={handleDownloadApk}
                  className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-bold text-xs px-4 py-2.5 rounded-full transition shadow-xs cursor-pointer border-0"
                >
                  Baixar APK Oficial ↗
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

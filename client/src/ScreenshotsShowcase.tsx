import React, { useState, useEffect } from 'react';
import {
  Car,
  MapPin,
  Sliders,
  ShieldCheck,
  Zap,
  X,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  CheckCircle2,
  XCircle,
  ZoomIn,
  Maximize2
} from 'lucide-react';

export interface ScreenshotItem {
  id: string;
  filename: string;
  title: string;
  category: 'cards_flutuantes' | 'streetview' | 'atalhos' | 'safety' | 'config';
  categoryLabel: string;
  badge?: string;
  badgeType?: 'green' | 'red' | 'blue' | 'purple' | 'amber';
  summary: string;
  highlightText: string;
}

export const SCREENSHOTS: ScreenshotItem[] = [
  // --- 1. CARDS FLUTUANTES & PRÁTICA ---
  {
    id: 'card-tipo-1',
    filename: 'CarTipo1.jpeg',
    title: 'Card Impacto Pro (Recomendado)',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '✨ MODELO 1: IMPACTO PRO',
    badgeType: 'green',
    summary: 'Design moderno com valor gigante no centro, km e tempo no topo e faixa sólida com o veredito instantâneo (✓ COMPENSA / ● ANALISAR / ✕ RECUSAR).',
    highlightText: 'Permite ao motorista decidir em frações de segundo com contraste impecável e leitura visual imediata no trânsito.'
  },
  {
    id: 'card-tipo-2',
    filename: 'CarTipo2.jpeg',
    title: 'Card 3 Pilares (R$/Km, R$/Hora e Nota)',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '📊 MODELO 2: 3 PILARES',
    badgeType: 'blue',
    summary: 'Resumo superior em linha única e 3 colunas verticais com barras coloridas destacando os 3 fatores decisivos de rentabilidade e nota do passageiro.',
    highlightText: 'Equilíbrio perfeito de métricas em colunas limpas, com suporte a tema claro ou escuro e transparência ajustável.'
  },
  {
    id: 'card-tipo-3',
    filename: 'CarTipo3.jpeg',
    title: 'Card Clássico (Tradicional)',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '🏷️ MODELO 3: CLÁSSICO',
    badgeType: 'purple',
    summary: 'Layout consagrado com etiquetas completas de km, hora, contagem regressiva e classificação do passageiro.',
    highlightText: 'Ideal para motoristas que preferem tags individuais e visualização detalhada de cada indicador financeiro.'
  },
  {
    id: 'card-tipo-4',
    filename: 'CarTipo4.jpeg',
    title: 'Card Discreto (Bolinha Semáforo)',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '🟢 MODELO 4: ULTRA COMPACTO',
    badgeType: 'amber',
    summary: 'Pílula horizontal ultrafina que não tapa o GPS nem o mapa de navegação, com sinalizador semáforo e métricas limpas sem cortes.',
    highlightText: 'Design minimalista para quem quer máxima discrição na tela sem abrir mão dos números essenciais da corrida.'
  },
  {
    id: 'viagem-compensa',
    filename: 'ViagemCompensa.jpeg',
    title: 'Chamada Lucrativa: ✓ COMPENSA',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '🟢 CORRIDA APROVADA',
    badgeType: 'green',
    summary: 'Card verde indicando corrida com excelente margem financeira (R$ 2,42/km, R$ 33,56/h e passageiro nota 4,87).',
    highlightText: 'Dinheiro no bolso garantido: cálculo automático descontando combustível e desgaste real do veículo.'
  },
  {
    id: 'viagem-avaliar',
    filename: 'ViagemAvaliar.jpeg',
    title: 'Chamada Intermediária: ● AVALIAR',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '🟡 MERECE ANÁLISE',
    badgeType: 'amber',
    summary: 'Card com alerta amarelo orientando o motorista a ponderar se a rota e o destino compensam no seu plano do dia.',
    highlightText: 'Flexibilidade inteligente: você define os limites de valor por km e por hora para cada faixa do semáforo.'
  },
  {
    id: 'viagem-nao-compensa',
    filename: 'ViagemNaoCompensa.jpeg',
    title: 'Chamada no Prejuízo: ✕ RECUSAR',
    category: 'cards_flutuantes',
    categoryLabel: 'Cards Flutuantes & Prática',
    badge: '🔴 NÃO COMPENSA',
    badgeType: 'red',
    summary: 'Card com contorno vermelho destacando corrida com remuneração insuficiente para cobrir seus custos reais.',
    highlightText: 'Proteja seu patrimônio e evite pagar para trabalhar! Recuse ofertas que geram prejuízo disfarçado.'
  },

  // --- 2. NOTIFICAÇÃO & STREET VIEW 360° ---
  {
    id: 'notificacao-streetview',
    filename: 'Notificacao_Origem_Destino.jpeg',
    title: 'Barra de Notificações com Street View 360°',
    category: 'streetview',
    categoryLabel: 'Notificação & Street View',
    badge: '🔔 AÇÕES NA NOTIFICAÇÃO',
    badgeType: 'purple',
    summary: 'Puxe a barra de notificações do Android e encontre botões de 1 toque: [📍 Origem] e [🏁 Destino] para ver a foto 360° da rua antes de aceitar ou chegar.',
    highlightText: 'Inovação exclusiva de segurança: veja o local real da chamada sem sair da rota e sem alternar aplicativos.'
  },
  {
    id: 'streetview-origem',
    filename: 'StreetViewOrigem.jpeg',
    title: 'Street View 360° do Ponto de Embarque',
    category: 'streetview',
    categoryLabel: 'Notificação & Street View',
    badge: '📍 FACHADA DO EMBARQUE',
    badgeType: 'blue',
    summary: 'Visualização fotográfica panorâmica da porta do passageiro (casas, condomínios, estabelecimentos e iluminação pública).',
    highlightText: 'Saiba exatamente onde o passageiro está esperando e evite paradas arriscadas em locais escuros ou becos.'
  },
  {
    id: 'streetview-destino',
    filename: 'StreetViewDestino.jpeg',
    title: 'Street View 360° do Ponto de Desembarque',
    category: 'streetview',
    categoryLabel: 'Notificação & Street View',
    badge: '🏁 FACHADA DO DESEMBARQUE',
    badgeType: 'blue',
    summary: 'Inspeção visual da fachada e rua de destino antes de iniciar a corrida ou durante o deslocamento.',
    highlightText: 'Chegue no endereço com total tranquilidade visualizando portarias, fachadas de prédios e características da rua.'
  },

  // --- 3. ATALHOS RÁPIDOS & TRÂNSITO ---
  {
    id: 'atalho-rapido',
    filename: 'AtalhoRapido.jpeg',
    title: 'Menu Flutuante de Atalhos Rápidos',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '⚡ ATALHOS SOBRE O UBER/99',
    badgeType: 'green',
    summary: 'Botão flutuante discreto com acesso em 1 toque a Semáforo, Metas, Áreas de Risco, Histórico e Painel Completo.',
    highlightText: 'Opere 100% das funções do GoOrNo sem precisar sair da tela do Uber ou da 99.'
  },
  {
    id: 'semaforo-rapido',
    filename: 'SemaforoRapido.jpeg',
    title: 'Ajuste Rápido do Semáforo no Trânsito',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '⏱️ AJUSTE EM 2 SEGUNDOS',
    badgeType: 'amber',
    summary: 'Popup flutuante para aumentar ou diminuir R$/km mín, R$/hora mín e nota mínima do passageiro com botões [+] e [-] instantâneos.',
    highlightText: 'Ajuste suas metas rapidamente na chuva, no horário de pico ou na dinâmica sem tirar a atenção da rua.'
  },
  {
    id: 'painel-inicial',
    filename: 'TelaInicioUltimaCorridaLida.jpeg',
    title: 'Painel Inicial & Última Corrida Capturada',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '📱 MONITORAMENTO EM TEMPO REAL',
    badgeType: 'blue',
    summary: 'Tela inicial do app exibindo o status dos serviços, detector de chamadas ativo e os dados completos da última corrida analisada.',
    highlightText: 'Controle total da operação com diagnóstico em tempo real e verificação dos motores de leitura OCR.'
  },
  {
    id: 'historico-auditoria',
    filename: 'DetalhesViagemOrigemDestino.jpeg',
    title: 'Histórico de Corridas & Auditoria com Lucro Real',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '📋 AUDITORIA COM LUCRO REAL',
    badgeType: 'purple',
    summary: 'Histórico auditado com centenas de chamadas salvas, indicando km bruto, hora bruta, lucro líquido estimado e botões rápidos (+Favorito, +Risco, 360° Maps).',
    highlightText: 'Guarde o registro de todas as ofertas lidas na tela com conferência de ganhos reais e rotas.'
  },

  // --- 4. SEGURANÇA & ÁREAS DE RISCO ---
  {
    id: 'seguranca-area-risco',
    filename: 'SegurancaAreaDeRisco.jpeg',
    title: 'Central de Segurança & Áreas de Risco',
    category: 'safety',
    categoryLabel: 'Segurança & Áreas de Risco',
    badge: '🛡️ BLOQUEIO DE LOCAIS PERIGOSOS',
    badgeType: 'red',
    summary: 'Ative alertas sonoros e force semáforo VERMELHO automático para ruas ou bairros perigosos, mesmo se pagarem bem por km.',
    highlightText: 'Sua vida e seu carro em primeiro lugar: o GoOrNo não deixa você entrar em furadas por engano.'
  },
  {
    id: 'seguranca-regiao-favorita',
    filename: 'SegurancaRegiaoFavorita.jpeg',
    title: 'Regiões Favoritas & Bairros Preferidos',
    category: 'safety',
    categoryLabel: 'Segurança & Áreas de Risco',
    badge: '⭐ ROTAS FAVORITAS',
    badgeType: 'green',
    summary: 'Cadastre seus bairros seguros e destinos prioritários com identificação imediata na janela flutuante.',
    highlightText: 'Priorize corridas que levam para onde você gosta de trabalhar, com aviso sonoro e visual diferenciado.'
  },
  {
    id: 'adicionar-area-risco',
    filename: 'AdicionarAreaDeRiscoManual.jpeg',
    title: 'Busca e Bloqueio Geográfico no Mapa',
    category: 'safety',
    categoryLabel: 'Segurança & Áreas de Risco',
    badge: '🗺️ PESQUISA E BLOQUEIO NO MAPA',
    badgeType: 'red',
    summary: 'Pesquise qualquer bairro ou rua pelo nome e adicione à sua lista de bloqueio com apenas um clique.',
    highlightText: 'Base flexível: integre com o banco nacional de zonas críticas ou monte sua própria lista de segurança da sua cidade.'
  },
  {
    id: 'adicionar-regiao-favorita',
    filename: 'AdicionarRegiaoFavoritaManual.jpeg',
    title: 'Cadastro de Locais e Destinos Favoritos',
    category: 'safety',
    categoryLabel: 'Segurança & Áreas de Risco',
    badge: '⭐ CADASTRO DE BAIRROS VIP',
    badgeType: 'green',
    summary: 'Localize áreas residenciais nobres, shoppings, centros comerciais ou aeroportos para receber destaque especial.',
    highlightText: 'Aumente seu faturamento direcionando suas corridas para as melhores regiões da sua cidade.'
  },
  {
    id: 'exemplo-area-risco-card',
    filename: 'ExemploAreaDeRiscoNoCard.jpeg',
    title: 'Alerta Ativo de Área de Risco no Card',
    category: 'safety',
    categoryLabel: 'Segurança & Áreas de Risco',
    badge: '⚠️ AVISO VISUAL NO CARD',
    badgeType: 'red',
    summary: 'Quando a chamada toca para um local bloqueado, o card exibe uma faixa de alto contraste alertando "ÁREA DE RISCO".',
    highlightText: 'Evite aceitar corridas por distração ou correria: o alerta surge no topo da tela antes de você clicar.'
  },
  {
    id: 'exemplo-regiao-favorita-card',
    filename: 'ExemploRegiaoFavoritaNoCard.jpeg',
    title: 'Destaque de Região Favorita no Card',
    category: 'safety',
    categoryLabel: 'Segurança & Áreas de Risco',
    badge: '⭐ AVISO DE REGIÃO FAVORITA',
    badgeType: 'amber',
    summary: 'Indicação dourada imediata no card avisando que a corrida vai em direção ao seu destino ou bairro preferido.',
    highlightText: 'Aceite rapidamente as melhores corridas do dia com a segurança de estar indo para sua área favorita.'
  },

  // --- 5. METAS & CUSTOS DO VEÍCULO ---
  {
    id: 'tela-semaforo',
    filename: 'TelaSemaforo.jpeg',
    title: 'Configuração de Metas & Faixas do Semáforo',
    category: 'config',
    categoryLabel: 'Metas & Custos',
    badge: '🚦 REGRAS DO SEMÁFORO FINANCEIRO',
    badgeType: 'green',
    summary: 'Defina seus parâmetros mínimos de R$/km, R$/hora e nota mínima para os vereditos Verde, Amarelo e Vermelho.',
    highlightText: 'Personalização absoluta: você no controle total dos seus critérios de rentabilidade.'
  },
  {
    id: 'tela-custo-carro',
    filename: 'TelaCustoCarro.jpeg',
    title: 'Calculadora de Custos Reais do Veículo',
    category: 'config',
    categoryLabel: 'Metas & Custos',
    badge: '⛽ GASOLINA & MANUTENÇÃO',
    badgeType: 'amber',
    summary: 'Insira o consumo de combustível (km/l), valor do litro e custo estimado de pneus, óleo e manutenção por km rodado.',
    highlightText: 'Cálculo 100% honesto e transparente do seu custo real de rodagem por quilômetro.'
  }
];

interface ScreenshotsShowcaseProps {
  onBackToLanding?: () => void;
}

export function ScreenshotsShowcase({ onBackToLanding }: ScreenshotsShowcaseProps) {
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [selectedImage, setSelectedImage] = useState<ScreenshotItem | null>(null);

  useEffect(() => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' as any });
  }, []);

  const handleBack = () => {
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' as any });
    if (onBackToLanding) {
      onBackToLanding();
    }
  };

  const filteredScreenshots = activeCategory === 'all'
    ? SCREENSHOTS
    : SCREENSHOTS.filter((s) => s.category === activeCategory);

  const openLightbox = (item: ScreenshotItem) => {
    setSelectedImage(item);
  };

  const closeLightbox = () => {
    setSelectedImage(null);
  };

  const handleNext = () => {
    if (!selectedImage) return;
    const currentIndex = filteredScreenshots.findIndex((s) => s.id === selectedImage.id);
    const nextIndex = (currentIndex + 1) % filteredScreenshots.length;
    setSelectedImage(filteredScreenshots[nextIndex]);
  };

  const handlePrev = () => {
    if (!selectedImage) return;
    const currentIndex = filteredScreenshots.findIndex((s) => s.id === selectedImage.id);
    const prevIndex = (currentIndex - 1 + filteredScreenshots.length) % filteredScreenshots.length;
    setSelectedImage(filteredScreenshots[prevIndex]);
  };

  return (
    <div className="min-h-screen bg-[#f3f5ed] text-[#071d19] font-dm pb-24">
      {/* HEADER FIXO SUPERIOR HARMONIZADO */}
      <header className="sticky top-0 z-40 bg-[#071d19] text-white border-b border-white/10 h-[76px] px-4 sm:px-8">
        <div className="max-w-6xl mx-auto h-full flex items-center justify-between">
          <div className="flex items-center gap-3">
            <a
              href="#top"
              onClick={(e) => {
                e.preventDefault();
                handleBack();
              }}
              className="flex items-center gap-2.5 text-white no-underline group cursor-pointer"
            >
              <img
                src="/assets/logo-goorno-clean.png"
                alt="GoOrNo"
                className="w-10 h-10 rounded-xl bg-white p-1 object-contain shadow-xs group-hover:scale-105 transition"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = '/logo-goorno-clean.png';
                }}
              />
              <span className="font-space font-bold text-lg text-white tracking-tight flex items-center gap-2">
                GoOrNo
                <small className="font-dm text-[10px] font-bold px-2 py-0.5 rounded bg-[#d9ff4f] text-[#071d19]">
                  22 Telas Oficiais
                </small>
              </span>
            </a>
          </div>

          <div className="flex items-center gap-3">
            {onBackToLanding && (
              <button
                onClick={handleBack}
                className="text-xs font-semibold text-[#c2d0ca] hover:text-[#d9ff4f] px-3.5 py-2 rounded-xl transition cursor-pointer border border-white/10 hover:border-white/30"
              >
                ← Voltar para Início
              </button>
            )}
            <a
              href="/GoOrNo.apk"
              className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-bold text-xs px-4 py-2.5 rounded-full transition flex items-center gap-1.5 shadow-xs no-underline"
            >
              Baixar APK Oficial <span>↗</span>
            </a>
          </div>
        </div>
      </header>

      {/* HERO SECTION DE APRESENTAÇÃO */}
      <section className="pt-8 pb-6 px-4 max-w-6xl mx-auto">
        <div className="bg-[#071d19] text-white rounded-[28px] p-6 sm:p-8 shadow-md border border-white/10 relative overflow-hidden">
          <div className="relative z-10 max-w-3xl">
            <div className="inline-flex items-center gap-1.5 bg-[#d9ff4f]/15 text-[#d9ff4f] px-3 py-1 rounded-full text-xs font-bold mb-3 border border-[#d9ff4f]/30">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Sem simulações falsas · Telas 100% reais de motoristas no trânsito</span>
            </div>
            <h1 className="font-space text-2xl sm:text-4xl font-bold text-white tracking-tight mb-3">
              Como o GoOrNo Funciona na Prática
            </h1>
            <p className="font-dm text-sm sm:text-base text-[#c2d0ca] leading-relaxed mb-6">
              Explore abaixo as capturas oficiais do GoOrNo: os <strong>4 modelos de cards flutuantes</strong>, comparação de <strong>Viagem Boa vs Viagem Ruim</strong>, <strong>Street View da origem e destino</strong>, menu de atalhos e calculadora de custos.
            </p>

            {/* COMPARATIVO DESTAQUE: COMPENSA VS NÃO COMPENSA */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
              <div
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'viagem-compensa');
                  if (item) openLightbox(item);
                }}
                className="bg-[#dff4af]/15 border border-[#dff4af]/30 hover:border-[#dff4af] p-4 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-[#d9ff4f] flex items-center gap-1.5 font-space">
                    <CheckCircle2 className="w-4 h-4 text-[#d9ff4f]" />
                    ✓ COMPENSA (CARD VERDE)
                  </span>
                  <Maximize2 className="w-4 h-4 text-[#d9ff4f]/60 group-hover:text-[#d9ff4f] transition" />
                </div>
                <p className="text-xs text-white/90 leading-relaxed font-dm">
                  R$ 17,90 por 7,4 km (R$ 2,42/km · R$ 33,56/h). O GoOrNo aprova na hora com card verde destacando alta rentabilidade.
                </p>
                <div className="mt-3 text-[11px] font-bold text-[#d9ff4f] underline">
                  Toque para ver o card Compensa →
                </div>
              </div>

              <div
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'viagem-nao-compensa');
                  if (item) openLightbox(item);
                }}
                className="bg-[#ffe1d9]/15 border border-[#ff6e58]/30 hover:border-[#ff6e58] p-4 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-[#ff6e58] flex items-center gap-1.5 font-space">
                    <XCircle className="w-4 h-4 text-[#ff6e58]" />
                    ✕ NÃO COMPENSA (CARD VERMELHO)
                  </span>
                  <Maximize2 className="w-4 h-4 text-[#ff6e58]/60 group-hover:text-[#ff6e58] transition" />
                </div>
                <p className="text-xs text-white/90 leading-relaxed font-dm">
                  R$ 11,25 por 9,0 km (R$ 1,25/km · R$ 56,25/h). O GoOrNo avisa em vermelho que a busca longa (4,4 km) gera prejuízo real.
                </p>
                <div className="mt-3 text-[11px] font-bold text-[#ff6e58] underline">
                  Toque para ver o card Não Compensa →
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* FILTROS DE CATEGORIA */}
      <section className="px-4 max-w-6xl mx-auto mb-8">
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
                className={`px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition flex items-center gap-2 cursor-pointer border ${
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
      </section>

      {/* GRID DE SCREENSHOTS COM MOLDURA DE SMARTPHONE */}
      <section className="px-4 max-w-6xl mx-auto">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {filteredScreenshots.map((item) => (
            <div
              key={item.id}
              onClick={() => openLightbox(item)}
              className="bg-white border border-[rgba(7,29,25,0.12)] rounded-3xl p-3.5 shadow-xs hover:shadow-2xl transition-all duration-300 cursor-pointer flex flex-col justify-between group hover:-translate-y-1.5"
            >
              {/* MOLDURA DE SMARTPHONE ULTRA REALISTA */}
              <div className="relative mx-auto w-full pt-1 pb-2">
                <div className="relative mx-auto p-1.5 bg-[#0a110f] rounded-[28px] border-[2.5px] border-[#20342f] shadow-lg max-w-[210px] group-hover:border-[#799900]/80 transition-colors">
                  {/* Dynamic Island / Front Camera */}
                  <div className="absolute top-2.5 left-1/2 -translate-x-1/2 w-12 h-2.5 bg-black rounded-full z-10 flex items-center justify-end pr-1 pointer-events-none">
                    <div className="w-1.5 h-1.5 rounded-full bg-[#1b2220]"></div>
                  </div>
                  {/* Tela interna do smartphone */}
                  <div className="relative aspect-[9/18.5] rounded-[22px] overflow-hidden bg-black flex items-center justify-center">
                    <img
                      src={`/telas/${item.filename}`}
                      alt={item.title}
                      className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                      loading="lazy"
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

              {/* CONTEÚDO DESCRITIVO */}
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
                  <ChevronRight className="w-4 h-4 text-[#7a8982] group-hover:translate-x-1 transition" />
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* LIGHTBOX MODAL COM SMARTPHONE EM TAMANHO GRANDE */}
      {selectedImage && (
        <div
          className="fixed inset-0 z-50 bg-black/85 backdrop-blur-xs flex items-center justify-center p-2 sm:p-4 md:p-6 animate-fadeIn"
          onClick={closeLightbox}
        >
          <div
            className="bg-[#071d19] text-white border border-white/15 rounded-3xl max-w-4xl w-full max-h-[92vh] flex flex-col md:flex-row shadow-2xl relative overflow-hidden no-scrollbar"
            onClick={(e) => e.stopPropagation()}
          >
            {/* BOTÃO FECHAR */}
            <button
              onClick={closeLightbox}
              className="absolute top-3 right-3 z-30 w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-black/75 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 shadow-md"
              title="Fechar"
              aria-label="Fechar"
            >
              <X className="w-4 h-4 sm:w-5 sm:h-5" />
            </button>

            {/* ÁREA DO SMARTPHONE */}
            <div className="w-full md:w-1/2 bg-black/60 flex items-center justify-center p-4 relative shrink-0 min-h-[300px] md:min-h-[520px]">
              {/* MOLDURA DE SMARTPHONE AMPLIADA */}
              <div className="relative p-2.5 bg-[#09110e] rounded-[38px] border-[3.5px] border-[#223530] shadow-2xl max-w-[260px] sm:max-w-[280px]">
                {/* Dynamic Island */}
                <div className="absolute top-3.5 left-1/2 -translate-x-1/2 w-16 h-3 bg-black rounded-full z-20 flex items-center justify-end pr-1 pointer-events-none">
                  <div className="w-1.5 h-1.5 rounded-full bg-[#1b2220]"></div>
                </div>
                <div className="relative aspect-[9/18.5] rounded-[30px] overflow-hidden bg-black flex items-center justify-center">
                  <img
                    src={`/telas/${selectedImage.filename}`}
                    alt={selectedImage.title}
                    className="w-full h-full object-contain"
                  />
                </div>
              </div>

              {/* NAVEGAÇÃO ANTERIOR / PRÓXIMO */}
              <button
                onClick={handlePrev}
                className="absolute left-2.5 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/70 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 z-10 shadow-md"
                title="Imagem anterior"
                aria-label="Imagem anterior"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button
                onClick={handleNext}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/70 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 z-10 shadow-md"
                title="Próxima imagem"
                aria-label="Próxima imagem"
              >
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>

            {/* DETALHES DA TELA */}
            <div className="w-full md:w-1/2 p-5 sm:p-6 flex flex-col justify-between overflow-y-auto no-scrollbar">
              <div className="space-y-3">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className="text-[11px] sm:text-xs font-bold text-[#c2d0ca] uppercase tracking-wider font-dm">
                    {selectedImage.categoryLabel}
                  </span>
                  {selectedImage.badge && (
                    <span
                      className={`text-[10px] font-extrabold px-2.5 py-0.5 rounded-full ${
                        selectedImage.badgeType === 'green'
                          ? 'bg-emerald-600 text-white'
                          : selectedImage.badgeType === 'red'
                          ? 'bg-rose-600 text-white'
                          : selectedImage.badgeType === 'amber'
                          ? 'bg-amber-600 text-white'
                          : selectedImage.badgeType === 'purple'
                          ? 'bg-purple-700 text-white'
                          : 'bg-[#d9ff4f] text-[#071d19]'
                      }`}
                    >
                      {selectedImage.badge}
                    </span>
                  )}
                </div>

                <h2 className="font-space text-xl sm:text-2xl font-bold text-white leading-tight">
                  {selectedImage.title}
                </h2>

                <div className="bg-white/5 border border-white/10 rounded-xl p-3.5">
                  <h4 className="text-[10px] sm:text-[11px] font-bold text-[#d9ff4f] uppercase mb-1 font-space">
                    O que esta tela mostra:
                  </h4>
                  <p className="text-xs sm:text-[13px] text-[#c2d0ca] leading-relaxed font-dm m-0">
                    {selectedImage.summary}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-xl p-3.5">
                  <h4 className="text-[10px] sm:text-[11px] font-bold text-white uppercase mb-1 flex items-center gap-1.5 font-space">
                    <Sparkles className="w-3.5 h-3.5 text-[#d9ff4f]" />
                    Como isso protege o seu bolso:
                  </h4>
                  <p className="text-xs sm:text-[13px] text-[#c2d0ca] leading-relaxed font-dm m-0">
                    {selectedImage.highlightText}
                  </p>
                </div>
              </div>

              <div className="pt-4 mt-4 border-t border-white/10 flex items-center justify-between gap-3">
                <span className="text-xs text-[#7a8982] truncate">
                  {selectedImage.filename}
                </span>
                <a
                  href="/GoOrNo.apk"
                  className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-bold text-xs px-4 py-2.5 rounded-full transition shadow-xs no-underline shrink-0"
                >
                  Baixar APK Oficial ↗
                </a>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

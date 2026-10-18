import React, { useState, useEffect } from 'react';
import {
  Car,
  MapPin,
  Sliders,
  ShieldCheck,
  Eye,
  X,
  ChevronLeft,
  ChevronRight,
  Sparkles,
  CheckCircle2,
  XCircle,
  AlertTriangle,
  ZoomIn,
  DollarSign,
  Maximize2
} from 'lucide-react';

export interface ScreenshotItem {
  id: string;
  filename: string;
  title: string;
  category: 'uber_99' | 'streetview' | 'config' | 'safety';
  categoryLabel: string;
  badge?: string;
  badgeType?: 'green' | 'red' | 'blue' | 'purple' | 'amber';
  summary: string;
  highlightText: string;
}

export const SCREENSHOTS: ScreenshotItem[] = [
  // --- UBER & 99 ---
  {
    id: 'uber-boa',
    filename: 'UberBoa.jpeg',
    title: 'Uber: Chamada Lucrativa (R$ 2,17/km)',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🟢 VIAGEM BOA',
    badgeType: 'green',
    summary: 'Card flutuante GoOrNo em VERDE alertando que a corrida paga R$ 10,83 para 5,0 km totais (1,0 km busca + 4,0 km viagem em 15 min - R$ 162,45/h).',
    highlightText: 'Em menos de 1 segundo você sabe que essa corrida coloca dinheiro no bolso com excelente ganho por km e por hora.'
  },
  {
    id: 'uber-ruim',
    filename: 'UberRuim.jpeg',
    title: 'Uber: Chamada com Prejuízo (R$ 1,25/km)',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🔴 VIAGEM RUIM',
    badgeType: 'red',
    summary: 'Card flutuante GoOrNo em VERMELHO indicando valor baixo por km (R$ 11,25 por 9,0 km totais - 4,4 km busca + 4,6 km viagem em 23 min - R$ 56,25/h).',
    highlightText: 'Evita que o motorista pague para trabalhar! O GoOrNo calcula custo de gasolina + desgaste e avisa na hora.'
  },
  {
    id: '99-boa',
    filename: '99Boa.jpeg',
    title: '99 Pop: Chamada Aprovada com Alto Rendimento',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🟢 99 APROVADA',
    badgeType: 'green',
    summary: 'Leitura instantânea da janela da 99 Pop sobreposta com dados mastigados de faturamento por hora.',
    highlightText: 'Compatível tanto com a janela de chamada comum quanto com o modo de aceitação rápida da 99.'
  },
  {
    id: 'uber-radar-1',
    filename: 'UberRadar1.jpeg',
    title: 'Uber Radar de Viagens: Análise Instantânea',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '⚡ MODO RADAR',
    badgeType: 'blue',
    summary: 'O Radar de Viagens da Uber desaparece em poucos segundos. O GoOrNo lê e calcula antes que acabe.',
    highlightText: 'Não perca tempo fazendo conta de cabeça no Radar. O card mostra direto se vale a pena clicar ou ignorar.'
  },
  {
    id: 'uber-radar-2',
    filename: 'UberRadar2.jpeg',
    title: 'Uber Radar: Corrida Longa com Cálculo de Volta',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '⚡ RADAR DINÂMICO',
    badgeType: 'blue',
    summary: 'Avaliação precisa de quilometragem de busca até o embarque somada com a rota final.',
    highlightText: 'Soma automaticamente os km de busca + km da corrida para você nunca ser enganado pela distância real.'
  },
  {
    id: '99-solicitacoes-1',
    filename: '99Solicitacoes1.jpeg',
    title: '99: Lista de Solicitações Simultâneas',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🚕 99 MOTORISTA',
    badgeType: 'amber',
    summary: 'A 99 lista chamadas em sequência. O GoOrNo lê a chamada ativa em foco na tela.',
    highlightText: 'Precisão milimétrica que ignora ofertas secundárias de cupom e foca estritamente na chamada do passageiro.'
  },
  {
    id: '99-solicitacoes-2',
    filename: '99Solicitacoes2.jpeg',
    title: '99: Detalhamento de Rota e Embarque',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🚕 99 TARIFA',
    badgeType: 'amber',
    summary: 'Exibição dos endereços de embarque e desembarque capturados com precisão.',
    highlightText: 'Verificação em tempo real da rentabilidade e do tempo estimado de deslocamento no trânsito.'
  },

  // --- NOTIFICAÇÃO & STREET VIEW ---
  {
    id: 'notificacoes-barra',
    filename: 'Notificacoes.jpeg',
    title: 'Barra de Notificações com Ícone & Botão Desativar',
    category: 'streetview',
    categoryLabel: 'Notificações & Street View',
    badge: '🔔 STATUS BAR OFICIAL',
    badgeType: 'purple',
    summary: 'Notificação silenciosa com o ícone do GoOrNo ao lado do relógio e botão direto de "⏹️ Desativar".',
    highlightText: 'Fica sempre disponível na bandeja do Android sem fazer barulho nem vibrar, e desliga em 1 toque quando você encerra o dia.'
  },
  {
    id: 'notificacoes-origem',
    filename: 'NotificacoesOrigem.jpeg',
    title: 'Street View 360° da Origem (Embarque)',
    category: 'streetview',
    categoryLabel: 'Notificações & Street View',
    badge: '📷 FOTO DA RUA (ORIGEM)',
    badgeType: 'blue',
    summary: 'Abra a foto real da rua do passageiro antes de se deslocar para conferir segurança e comércio.',
    highlightText: 'Veja se o local é condomínio fechado, bar, ponto movimentado ou rua escura com 1 toque no card.'
  },
  {
    id: 'notificacoes-destino',
    filename: 'NotificacoesDestino.jpeg',
    title: 'Street View 360° do Destino (Desembarque)',
    category: 'streetview',
    categoryLabel: 'Notificações & Street View',
    badge: '📷 FOTO DA RUA (DESTINO)',
    badgeType: 'blue',
    summary: 'Inspeção visual do destino final para evitar vielas sem saída ou locais ermos à noite.',
    highlightText: 'Segurança em primeiro lugar: saiba exatamente como é a fachada do destino antes de iniciar a viagem.'
  },

  // --- CONFIGURAÇÕES & METAS ---
  {
    id: 'tela-principal',
    filename: 'TelaGoOrNoPrincipal.jpeg',
    title: 'Painel Principal do GoOrNo',
    category: 'config',
    categoryLabel: 'Configurações & Metas',
    badge: '⚙️ PAINEL COPILOTO',
    badgeType: 'purple',
    summary: 'Botão principal de Ativação, indicador de status dos serviços e atalhos rápidos.',
    highlightText: 'Interface moderna e limpa no padrão MeuAutônomo com consumo levíssimo de bateria e memória.'
  },
  {
    id: 'config-metas',
    filename: 'TelaConfigMetas.jpeg',
    title: 'Personalização de Metas Financeiras (R$/km e R$/h)',
    category: 'config',
    categoryLabel: 'Configurações & Metas',
    badge: '🎯 SUAS REGRAS',
    badgeType: 'green',
    summary: 'Você escolhe quanto vale o seu km rodado e qual é o mínimo aceitável por hora trabalhada.',
    highlightText: 'Configure por exemplo: Verde acima de R$ 2,20/km, Amarelo entre R$ 1,70 e R$ 2,20, Vermelho abaixo de R$ 1,70.'
  },
  {
    id: 'config-custos',
    filename: 'TelaConfigCustos.jpeg',
    title: 'Calculadora de Custos Reais do Carro / Moto',
    category: 'config',
    categoryLabel: 'Configurações & Metas',
    badge: '⛽ CUSTO REAL',
    badgeType: 'amber',
    summary: 'Informe o preço do litro de combustível, o consumo médio (km/l) e o custo de manutenção preventiva.',
    highlightText: 'O GoOrNo desconta automaticamente os custos de cada corrida e exibe o Lucro Líquido Real que sobra no bolso.'
  },
  {
    id: 'config-flutuante',
    filename: 'TelaConfigFlutuante.jpeg',
    title: 'Ajuste Fino do Card Flutuante',
    category: 'config',
    categoryLabel: 'Configurações & Metas',
    badge: '🎛️ CUSTOMIZAÇÃO',
    badgeType: 'blue',
    summary: 'Personalize a opacidade do card, tamanho da fonte e quantos segundos ele permanece na tela.',
    highlightText: 'Você pode deixar o card semitransparente para não cobrir informações importantes do mapa de navegação.'
  },
  {
    id: 'apps-compativeis',
    filename: 'TelaAppsCompativeis.jpeg',
    title: 'Seletor de Apps Compatíveis',
    category: 'config',
    categoryLabel: 'Configurações & Metas',
    badge: '📱 MULTI-PLATAFORMA',
    badgeType: 'purple',
    summary: 'Ative ou desative o monitoramento para Uber Driver, 99 Motorista ou InDrive individualmente.',
    highlightText: 'Flexibilidade total: se você roda apenas na Uber ou apenas na 99, ative só o que precisa.'
  },

  // --- SEGURANÇA & AUDITORIA ---
  {
    id: 'seguranca-moto-1',
    filename: 'TelaSeguracaMoto1.jpeg',
    title: 'Módulo de Segurança, Áreas de Risco & Bairros Favoritos',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '🛡️ ÁREAS DE RISCO & FAVORITOS',
    badgeType: 'red',
    summary: 'Cadastre suas Áreas de Risco para alerta vermelho imediato e seus Bairros Favoritos para rodar com tranquilidade na sua zona de conforto.',
    highlightText: 'Controle total da sua operação: você decide onde quer trabalhar e recebe alerta visual na hora em que o passageiro chama.'
  },
  {
    id: 'seguranca-moto-2',
    filename: 'TelaSeguracaMoto2.jpeg',
    title: 'Radar de Bairros & Alerta em Tempo Real',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '⭐ ZONA DE CONFORTO',
    badgeType: 'green',
    summary: 'Sinalização visual imediata no card: estrela dourada para seus bairros preferidos e alerta vermelho para regiões perigosas.',
    highlightText: 'Não seja pego de surpresa em bairros desconhecidos. O copiloto checa origem e destino instantaneamente antes do aceite.'
  },
  {
    id: 'metas-pax',
    filename: 'TelaMetasAvaliacaoPax.jpeg',
    title: 'Filtro por Nota Mínima do Passageiro',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '⭐ NOTA DO PASSAGEIRO',
    badgeType: 'amber',
    summary: 'Defina a nota de corte (ex: passageiro com menos de 4.75 estrelas gera alerta amarelo/vermelho).',
    highlightText: 'Evite passageiros problemáticos, calotes e situações desconfortáveis filtrando por histórico de pontuação.'
  },
  {
    id: 'historico-chamados',
    filename: 'TelaHistoricoChamados.jpeg',
    title: 'Histórico de Chamadas com 1 Toque (+Fav / +Risco)',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '📋 AUDITORIA & 1 TOQUE',
    badgeType: 'purple',
    summary: 'Registro automático de todas as corridas que tocaram no celular com atalhos de 1 toque para adicionar aos Favoritos (+Fav) ou Áreas de Risco (+Risco).',
    highlightText: 'Gostou do bairro ou achou a rua perigosa? Com um simples toque no histórico o local é salvo nas suas regras personalizadas.'
  },
  {
    id: 'seg-unico-usuario',
    filename: 'TelaSegUnicoUsuario.jpeg',
    title: 'Proteção de Dispositivo e Licença Vinculada',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '🔒 SEGURANÇA DA CONTA',
    badgeType: 'blue',
    summary: 'Sua assinatura fica vinculada ao seu WhatsApp com proteção criptográfica contra clonagem.',
    highlightText: 'Se trocar de aparelho, você pode transferir sua chave de acesso pelo próprio aplicativo em 1 clique.'
  },
  {
    id: 'freemium-7-dias',
    filename: 'TelaFreemium7Dias.jpeg',
    title: 'Acesso Grátis de 7 Dias para Todos os Motoristas',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '🎁 7 DIAS GRÁTIS',
    badgeType: 'green',
    summary: 'Basta digitar o WhatsApp com DDD para liberar 7 dias completos com todas as funções desbloqueadas.',
    highlightText: 'Teste na rua, compare seus ganhos e veja se o GoOrNo se paga no primeiro dia de trabalho.'
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
                  21 Telas Reais
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
              href="/download/apk"
              className="bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] font-space font-bold text-xs px-4 py-2.5 rounded-full transition flex items-center gap-1.5 shadow-xs no-underline"
            >
              Baixar APK v3.3.22 <span>↗</span>
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
              Confira abaixo capturas reais do GoOrNo em ação: leitura de chamadas da <strong>Uber</strong> e <strong>99</strong>, comparação direta de <strong>Viagem Boa vs Viagem Ruim</strong>, <strong>Street View da origem e destino</strong>, notificação na barra e custos configuráveis.
            </p>

            {/* COMPARATIVO DESTAQUE: BOA VS RUIM */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
              <div
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'uber-boa');
                  if (item) openLightbox(item);
                }}
                className="bg-[#dff4af]/15 border border-[#dff4af]/30 hover:border-[#dff4af] p-4 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-[#d9ff4f] flex items-center gap-1.5 font-space">
                    <CheckCircle2 className="w-4 h-4 text-[#d9ff4f]" />
                    VIAGEM BOA (CARD VERDE)
                  </span>
                  <Maximize2 className="w-4 h-4 text-[#d9ff4f]/60 group-hover:text-[#d9ff4f] transition" />
                </div>
                <p className="text-xs text-white/90 leading-relaxed font-dm">
                  R$ 10,83 por 5,0 km (R$ 2,17/km · R$ 162,45/h). O GoOrNo aprova na hora com card verde destacando alta rentabilidade.
                </p>
                <div className="mt-3 text-[11px] font-bold text-[#d9ff4f] underline">
                  Toque para ver a tela do UberBoa →
                </div>
              </div>

              <div
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'uber-ruim');
                  if (item) openLightbox(item);
                }}
                className="bg-[#ffe1d9]/15 border border-[#ff6e58]/30 hover:border-[#ff6e58] p-4 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-bold text-[#ff6e58] flex items-center gap-1.5 font-space">
                    <XCircle className="w-4 h-4 text-[#ff6e58]" />
                    VIAGEM RUIM (CARD VERMELHO)
                  </span>
                  <Maximize2 className="w-4 h-4 text-[#ff6e58]/60 group-hover:text-[#ff6e58] transition" />
                </div>
                <p className="text-xs text-white/90 leading-relaxed font-dm">
                  R$ 11,25 por 9,0 km (R$ 1,25/km · R$ 56,25/h). O GoOrNo avisa em vermelho que a busca longa (4,4 km) gera prejuízo real.
                </p>
                <div className="mt-3 text-[11px] font-bold text-[#ff6e58] underline">
                  Toque para ver a tela do UberRuim →
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

      {/* GRID DE SCREENSHOTS */}
      <section className="px-4 max-w-6xl mx-auto">
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-5">
          {filteredScreenshots.map((item) => (
            <div
              key={item.id}
              onClick={() => openLightbox(item)}
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

              {/* CONTEÚDO */}
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
                  <ChevronRight className="w-4 h-4 text-[#7a8982] group-hover:translate-x-1 transition" />
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* LIGHTBOX MODAL */}
      {selectedImage && (
        <div
          className="fixed inset-0 z-50 bg-black/85 backdrop-blur-xs flex items-center justify-center p-2 sm:p-4 md:p-6 animate-fadeIn"
          onClick={closeLightbox}
        >
          <div
            className="bg-[#071d19] text-white border border-white/15 rounded-2xl sm:rounded-3xl max-w-4xl w-full max-h-[92vh] flex flex-col md:flex-row shadow-2xl relative overflow-hidden no-scrollbar"
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

            {/* ÁREA DA IMAGEM */}
            <div className="w-full md:w-1/2 bg-black/50 flex items-center justify-center p-3 sm:p-4 md:p-5 relative shrink-0 min-h-[220px] sm:min-h-[280px] md:min-h-[460px] max-h-[40vh] md:max-h-[80vh]">
              <img
                src={`/telas/${selectedImage.filename}`}
                alt={selectedImage.title}
                className="max-h-[34vh] sm:max-h-[36vh] md:max-h-[68vh] w-auto max-w-full object-contain rounded-xl shadow-lg"
              />

              {/* NAVEGAÇÃO ANTERIOR / PRÓXIMO */}
              <button
                onClick={handlePrev}
                className="absolute left-2.5 top-1/2 -translate-y-1/2 w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-black/70 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 z-10 shadow-md"
                title="Imagem anterior"
                aria-label="Imagem anterior"
              >
                <ChevronLeft className="w-4 h-4 sm:w-5 sm:h-5" />
              </button>
              <button
                onClick={handleNext}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 w-8 h-8 sm:w-9 sm:h-9 rounded-full bg-black/70 hover:bg-black/95 text-white grid place-items-center transition cursor-pointer border border-white/20 z-10 shadow-md"
                title="Próxima imagem"
                aria-label="Próxima imagem"
              >
                <ChevronRight className="w-4 h-4 sm:w-5 sm:h-5" />
              </button>
            </div>

            {/* DETALHES DA TELA */}
            <div className="w-full md:w-1/2 p-4 sm:p-5 md:p-6 flex flex-col justify-between overflow-y-auto no-scrollbar">
              <div className="space-y-2.5 sm:space-y-3">
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

                <h2 className="font-space text-lg sm:text-xl md:text-2xl font-bold text-white leading-tight">
                  {selectedImage.title}
                </h2>

                <div className="bg-white/5 border border-white/10 rounded-xl p-3 sm:p-3.5">
                  <h4 className="text-[10px] sm:text-[11px] font-bold text-[#d9ff4f] uppercase mb-1 font-space">
                    O que esta tela mostra:
                  </h4>
                  <p className="text-xs sm:text-[13px] text-[#c2d0ca] leading-relaxed font-dm m-0">
                    {selectedImage.summary}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-xl p-3 sm:p-3.5">
                  <h4 className="text-[10px] sm:text-[11px] font-bold text-white uppercase mb-1 flex items-center gap-1.5 font-space">
                    <Sparkles className="w-3.5 h-3.5 text-[#d9ff4f]" />
                    Como isso protege o seu bolso:
                  </h4>
                  <p className="text-xs sm:text-[13px] text-[#c2d0ca] leading-relaxed font-dm m-0">
                    {selectedImage.highlightText}
                  </p>
                </div>
              </div>

              <div className="pt-3 sm:pt-4 mt-3 sm:mt-4 border-t border-white/10 flex items-center justify-between gap-3">
                <span className="text-[11px] sm:text-xs text-[#7a8982] truncate">
                  {selectedImage.filename}
                </span>
                <a
                  href="/download/apk"
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

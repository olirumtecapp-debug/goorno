import React, { useState } from 'react';
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
    title: 'Uber: Chamada Lucrativa (R$ 3,35/km)',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🟢 VIAGEM BOA',
    badgeType: 'green',
    summary: 'Card flutuante GoOrNo em VERDE alertando que a corrida paga R$ 22,46 para 6.7 km totais.',
    highlightText: 'Em menos de 1 segundo você sabe que essa corrida coloca dinheiro no bolso com excelente ganho por km e por hora.'
  },
  {
    id: 'uber-ruim',
    filename: 'UberRuim.jpeg',
    title: 'Uber: Chamada com Prejuízo (R$ 1,23/km)',
    category: 'uber_99',
    categoryLabel: 'Uber & 99 na Prática',
    badge: '🔴 VIAGEM RUIM',
    badgeType: 'red',
    summary: 'Card flutuante GoOrNo em VERMELHO indicando valor baixo por km (R$ 13,85 por 11.2 km).',
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
    title: 'Módulo de Segurança e Zonas de Risco (Parte 1)',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '🛡️ ÁREAS DE RISCO',
    badgeType: 'red',
    summary: 'Configuração de alertas antecipados para áreas com histórico elevado de ocorrências.',
    highlightText: 'Proteção essencial especialmente à noite e na madrugada, tanto para motoristas de carro quanto para entregadores/mototaxistas.'
  },
  {
    id: 'seguranca-moto-2',
    filename: 'TelaSeguracaMoto2.jpeg',
    title: 'Módulo de Segurança e Zonas de Risco (Parte 2)',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '🛡️ ALERTA EM TEMPO REAL',
    badgeType: 'red',
    summary: 'Sinalização visual imediata caso a rota passe por regiões com restrições de segurança.',
    highlightText: 'Não seja pego de surpresa em bairros desconhecidos. O copiloto checa o itinerário instantaneamente.'
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
    title: 'Histórico Completo & Auditoria de Chamadas',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '📋 AUDITORIA',
    badgeType: 'purple',
    summary: 'Registro automático de todas as corridas que tocaram no celular com data, hora, km e valor ofertado.',
    highlightText: 'Confira se a Uber ou 99 pagou exatamente o valor prometido na chamada original ou se houve diferença de repasse.'
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
  },
  {
    id: 'plano-vitalicio-ajustado',
    filename: 'TelaCORRIGIRquebradelinha.jpeg',
    title: 'Planos Sem Mensalidade: Vitalício por R$ 39,90',
    category: 'safety',
    categoryLabel: 'Segurança & Auditoria',
    badge: '💎 VERSÃO 3.3.4 AJUSTADA',
    badgeType: 'green',
    summary: 'Tela de assinatura com o selo "RECOMENDADO" e títulos perfeitamente alinhados na versão 3.3.4.',
    highlightText: 'Pague uma única vez e use para sempre! Sem assinaturas mensais recorrentes ou cobranças automáticas no cartão.'
  }
];

interface ScreenshotsShowcaseProps {
  onBackToLanding?: () => void;
}

export function ScreenshotsShowcase({ onBackToLanding }: ScreenshotsShowcaseProps) {
  const [activeCategory, setActiveCategory] = useState<string>('all');
  const [selectedImage, setSelectedImage] = useState<ScreenshotItem | null>(null);

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
    <div className="min-h-screen bg-[#f5f7f2] text-[#173a34] font-sans antialiased pb-20">
      {/* HEADER */}
      <header className="sticky top-0 z-40 bg-[#173a34] text-white border-b border-[#122e29] shadow-md">
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <img
              src="/logo-goorno-clean.png"
              alt="GoOrNo"
              className="w-10 h-10 rounded-xl bg-white/10 p-1 object-contain"
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/logo-goorno.png';
              }}
            />
            <div>
              <h1 className="font-black text-lg text-white tracking-tight flex items-center gap-2">
                Galeria Real do GoOrNo
                <span className="text-[10px] font-extrabold px-2 py-0.5 rounded-full bg-[#d9f56a] text-[#173a34] uppercase">
                  22 Telas Oficiais
                </span>
              </h1>
              <p className="text-[11px] text-[#dce5dc]/70 hidden sm:block">
                Veja o aplicativo rodando exatamente como ele aparece no celular dos motoristas
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            {onBackToLanding && (
              <button
                onClick={onBackToLanding}
                className="text-xs font-semibold text-[#dce5dc] hover:text-[#d9f56a] px-3 py-1.5 rounded-lg transition cursor-pointer"
              >
                ← Voltar para Download
              </button>
            )}
            <a
              href="/GoOrNo.apk"
              className="bg-[#d9f56a] hover:bg-[#cbf052] text-[#173a34] font-bold text-xs px-3.5 py-2 rounded-xl transition flex items-center gap-1.5 shadow-sm"
            >
              Baixar APK v3.3.4
            </a>
          </div>
        </div>
      </header>

      {/* HERO SECTION DE APRESENTAÇÃO */}
      <section className="pt-8 pb-6 px-4 max-w-6xl mx-auto">
        <div className="bg-[#173a34] text-white rounded-3xl p-6 sm:p-8 shadow-md border border-[#122e29] relative overflow-hidden">
          <div className="relative z-10 max-w-3xl">
            <div className="inline-flex items-center gap-1.5 bg-[#d9f56a]/15 text-[#d9f56a] px-3 py-1 rounded-full text-xs font-bold mb-3 border border-[#d9f56a]/30">
              <Sparkles className="w-3.5 h-3.5" />
              <span>Sem simulações falsas · Telas 100% reais de motoristas na rua</span>
            </div>
            <h2 className="text-2xl sm:text-4xl font-black text-white tracking-tight mb-3">
              Como o GoOrNo Funciona na Vida Real
            </h2>
            <p className="text-sm sm:text-base text-[#dce5dc]/90 leading-relaxed mb-6">
              Confira abaixo capturas reais do GoOrNo em ação: leitura de chamadas da <strong>Uber</strong> e <strong>99</strong>, comparação de <strong>Viagem Boa vs Viagem Ruim</strong>, <strong>Street View da origem e destino</strong>, notificação na barra e configurações personalizadas de custos.
            </p>

            {/* COMPARATIVO DESTAQUE: BOA VS RUIM */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 mt-4">
              <div
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'uber-boa');
                  if (item) openLightbox(item);
                }}
                className="bg-emerald-950/60 border border-emerald-500/40 hover:border-emerald-400 p-4 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-black text-emerald-300 flex items-center gap-1.5">
                    <CheckCircle2 className="w-4 h-4 text-emerald-400" />
                    VIAGEM BOA (CARD VERDE)
                  </span>
                  <Maximize2 className="w-4 h-4 text-emerald-400/60 group-hover:text-emerald-300 transition" />
                </div>
                <p className="text-xs text-emerald-100/90 leading-relaxed">
                  R$ 22,46 por 6.7 km (~R$ 3,35/km). O GoOrNo aprova na hora e mostra seu ganho projetado.
                </p>
                <div className="mt-3 text-[11px] font-bold text-emerald-300 underline">
                  Toque para ver a tela do UberBoa →
                </div>
              </div>

              <div
                onClick={() => {
                  const item = SCREENSHOTS.find((s) => s.id === 'uber-ruim');
                  if (item) openLightbox(item);
                }}
                className="bg-rose-950/60 border border-rose-500/40 hover:border-rose-400 p-4 rounded-2xl cursor-pointer transition group"
              >
                <div className="flex items-center justify-between mb-2">
                  <span className="text-xs font-black text-rose-300 flex items-center gap-1.5">
                    <XCircle className="w-4 h-4 text-rose-400" />
                    VIAGEM RUIM (CARD VERMELHO)
                  </span>
                  <Maximize2 className="w-4 h-4 text-rose-400/60 group-hover:text-rose-300 transition" />
                </div>
                <p className="text-xs text-rose-100/90 leading-relaxed">
                  R$ 13,85 por 11.2 km (~R$ 1,23/km). O GoOrNo avisa que dá prejuízo considerando combustível.
                </p>
                <div className="mt-3 text-[11px] font-bold text-rose-300 underline">
                  Toque para ver a tela do UberRuim →
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* FILTROS DE CATEGORIA */}
      <section className="px-4 max-w-6xl mx-auto mb-6">
        <div className="flex items-center gap-2 overflow-x-auto pb-2 scrollbar-none">
          {[
            { key: 'all', label: 'Todas as Telas (22)', icon: Sparkles },
            { key: 'uber_99', label: '🚗 Uber & 99 na Prática (7)', icon: Car },
            { key: 'streetview', label: '📍 Notificação & Street View (3)', icon: MapPin },
            { key: 'config', label: '⚙️ Metas & Calculadora (5)', icon: Sliders },
            { key: 'safety', label: '🛡️ Segurança & Auditoria (7)', icon: ShieldCheck }
          ].map((cat) => {
            const Icon = cat.icon;
            const isSelected = activeCategory === cat.key;
            return (
              <button
                key={cat.key}
                onClick={() => setActiveCategory(cat.key)}
                className={`whitespace-nowrap px-4 py-2.5 rounded-xl text-xs sm:text-sm font-bold transition flex items-center gap-2 cursor-pointer ${
                  isSelected
                    ? 'bg-[#173a34] text-[#d9f56a] shadow-sm'
                    : 'bg-white border border-[#dce5dc] text-[#71867f] hover:text-[#173a34] hover:bg-[#edf2eb]'
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
              className="bg-white border border-[#dce5dc] rounded-2xl overflow-hidden shadow-xs hover:shadow-lg transition-all duration-200 cursor-pointer flex flex-col group hover:-translate-y-1"
            >
              {/* IMAGEM COM BADGE */}
              <div className="relative aspect-[9/16] bg-[#173a34]/5 overflow-hidden flex items-center justify-center p-2">
                <img
                  src={`/telas/${item.filename}`}
                  alt={item.title}
                  className="w-full h-full object-contain rounded-lg group-hover:scale-[1.02] transition-transform duration-300"
                  loading="lazy"
                  onError={(e) => {
                    // Fallback se a imagem não carregar
                    (e.target as HTMLImageElement).src = '/logo-goorno.png';
                  }}
                />
                <div className="absolute top-3 left-3 right-3 flex items-center justify-between pointer-events-none">
                  {item.badge && (
                    <span
                      className={`text-[10px] font-extrabold px-2.5 py-1 rounded-full shadow-sm backdrop-blur-sm ${
                        item.badgeType === 'green'
                          ? 'bg-emerald-600 text-white'
                          : item.badgeType === 'red'
                          ? 'bg-rose-600 text-white'
                          : item.badgeType === 'amber'
                          ? 'bg-amber-600 text-white'
                          : item.badgeType === 'purple'
                          ? 'bg-purple-700 text-white'
                          : 'bg-[#173a34] text-[#d9f56a]'
                      }`}
                    >
                      {item.badge}
                    </span>
                  )}
                  <span className="w-7 h-7 rounded-full bg-[#173a34]/80 text-white grid place-items-center opacity-0 group-hover:opacity-100 transition-opacity ml-auto">
                    <ZoomIn className="w-4 h-4" />
                  </span>
                </div>
              </div>

              {/* CONTEÚDO */}
              <div className="p-4 flex-1 flex flex-col justify-between">
                <div>
                  <span className="text-[10px] font-bold text-[#71867f] uppercase tracking-wider block mb-1">
                    {item.categoryLabel}
                  </span>
                  <h3 className="font-bold text-sm text-[#173a34] leading-snug mb-2 group-hover:text-emerald-800 transition">
                    {item.title}
                  </h3>
                  <p className="text-xs text-[#71867f] line-clamp-2 leading-relaxed">
                    {item.summary}
                  </p>
                </div>

                <div className="mt-3 pt-3 border-t border-[#f5f7f2] flex items-center justify-between text-xs font-semibold text-[#173a34]">
                  <span>Toque para ampliar</span>
                  <ChevronRight className="w-4 h-4 text-[#71867f] group-hover:translate-x-1 transition" />
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* LIGHTBOX MODAL */}
      {selectedImage && (
        <div
          className="fixed inset-0 z-50 bg-black/85 backdrop-blur-sm flex items-center justify-center p-3 sm:p-6"
          onClick={closeLightbox}
        >
          <div
            className="bg-[#173a34] text-white border border-[#234e45] rounded-3xl max-w-4xl w-full max-h-[92vh] overflow-hidden flex flex-col md:flex-row shadow-2xl relative"
            onClick={(e) => e.stopPropagation()}
          >
            {/* BOTÃO FECHAR */}
            <button
              onClick={closeLightbox}
              className="absolute top-3 right-3 z-20 w-9 h-9 rounded-full bg-white/20 hover:bg-white/30 text-white grid place-items-center transition cursor-pointer"
            >
              <X className="w-5 h-5" />
            </button>

            {/* ÁREA DA IMAGEM */}
            <div className="md:w-1/2 bg-black/40 flex items-center justify-center p-4 relative min-h-[350px] md:min-h-[550px]">
              <img
                src={`/telas/${selectedImage.filename}`}
                alt={selectedImage.title}
                className="max-h-[75vh] w-auto max-w-full object-contain rounded-xl shadow-lg"
              />

              {/* NAVEGAÇÃO ANTERIOR / PRÓXIMO */}
              <button
                onClick={handlePrev}
                className="absolute left-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/60 hover:bg-black/90 text-white grid place-items-center transition cursor-pointer"
                title="Imagem anterior"
              >
                <ChevronLeft className="w-5 h-5" />
              </button>
              <button
                onClick={handleNext}
                className="absolute right-2 top-1/2 -translate-y-1/2 w-9 h-9 rounded-full bg-black/60 hover:bg-black/90 text-white grid place-items-center transition cursor-pointer"
                title="Próxima imagem"
              >
                <ChevronRight className="w-5 h-5" />
              </button>
            </div>

            {/* DETALHES DA TELA */}
            <div className="md:w-1/2 p-6 sm:p-8 flex flex-col justify-between overflow-y-auto">
              <div>
                <div className="flex items-center gap-2 mb-3">
                  <span className="text-xs font-bold text-[#dce5dc]/70 uppercase tracking-wider">
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
                          : 'bg-[#d9f56a] text-[#173a34]'
                      }`}
                    >
                      {selectedImage.badge}
                    </span>
                  )}
                </div>

                <h2 className="text-xl sm:text-2xl font-black text-white leading-tight mb-3">
                  {selectedImage.title}
                </h2>

                <div className="bg-[#122e29] border border-[#234e45] rounded-2xl p-4 mb-4">
                  <h4 className="text-xs font-bold text-[#d9f56a] uppercase mb-1">
                    O que esta tela mostra:
                  </h4>
                  <p className="text-sm text-[#dce5dc] leading-relaxed">
                    {selectedImage.summary}
                  </p>
                </div>

                <div className="bg-white/5 border border-white/10 rounded-2xl p-4">
                  <h4 className="text-xs font-bold text-white uppercase mb-1 flex items-center gap-1.5">
                    <Sparkles className="w-3.5 h-3.5 text-[#d9f56a]" />
                    Como isso protege o seu bolso:
                  </h4>
                  <p className="text-xs sm:text-sm text-[#dce5dc]/90 leading-relaxed">
                    {selectedImage.highlightText}
                  </p>
                </div>
              </div>

              <div className="pt-6 mt-6 border-t border-[#234e45] flex items-center justify-between">
                <span className="text-xs text-[#71867f]">
                  Arquivo: {selectedImage.filename}
                </span>
                <a
                  href="/GoOrNo.apk"
                  className="bg-[#d9f56a] hover:bg-[#cbf052] text-[#173a34] font-bold text-xs px-4 py-2.5 rounded-xl transition shadow-sm"
                >
                  Baixar GoOrNo APK
                </a>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

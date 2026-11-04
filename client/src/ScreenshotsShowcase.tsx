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
  category: 'streetview' | 'atalhos' | 'safety';
  categoryLabel: string;
  badge?: string;
  badgeType?: 'green' | 'red' | 'blue' | 'purple' | 'amber';
  summary: string;
  highlightText: string;
}

export const SCREENSHOTS: ScreenshotItem[] = [
  // ==========================================
  // 1. NOTIFICAÇÃO & STREET VIEW 360° (3 telas)
  // ==========================================
  {
    id: 'notificacao-streetview',
    filename: 'Notificacao_Origem_DestinoNew.jpeg',
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
    filename: 'StreetViewOrigemNew.jpeg',
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
    filename: 'StreetViewDestinoNew.jpeg',
    title: 'Street View 360° do Ponto de Desembarque',
    category: 'streetview',
    categoryLabel: 'Notificação & Street View',
    badge: '🏁 FACHADA DO DESEMBARQUE',
    badgeType: 'green',
    summary: 'Foto real da fachada do destino final para conferir segurança da região, portaria ou acesso antes de iniciar a viagem.',
    highlightText: 'Chegue com precisão cirúrgica no destino sem errar o lado da rua ou ficar procurando endereço.'
  },

  // ==========================================
  // 2. ATALHOS & TRÂNSITO (9 telas)
  // ==========================================
  {
    id: 'tela-atalho-1',
    filename: 'TelaAtalho1.jpeg',
    title: 'Menu Flutuante de Atalhos Rápidos',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '⚡ ATALHOS RÁPIDOS',
    badgeType: 'green',
    summary: 'Menu de acesso rápido que abre sobre a Uber ou 99 ao tocar na bolinha flutuante: abra Semáforo, Metas Financeiras, Áreas de Risco ou Histórico sem fechar o app de viagens.',
    highlightText: 'Agilidade total ao dirigir: troque de ferramenta ou consulte dados vitais com apenas 1 clique.'
  },
  {
    id: 'tela-atalho-2',
    filename: 'TelaAtalho2.jpeg',
    title: 'Ajuste Rápido do Semáforo no Trânsito',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '⚡ SEMÁFORO RÁPIDO',
    badgeType: 'blue',
    summary: 'Ajuste instantâneo de parâmetros de rentabilidade via botões [+] e [-]: altere R$/Km Mínimo, R$/Hora Mínimo, Nota Mínima e Embarque Longe Máximo durante o plantão.',
    highlightText: 'Adapte seus critérios de aprovação de chamadas em tempo real conforme a demanda ou horário de pico.'
  },
  {
    id: 'tela-config-card-1',
    filename: 'TelaConfigCard1.jpeg',
    title: 'Estilo do Cartão & Janela Arrastável',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '🎛️ ESTILO DO CARTÃO',
    badgeType: 'purple',
    summary: 'Escolha seu formato preferido (Card Impacto Pro ou 3 Pilares) e utilize a Janela Livremente Arrastável, que salva automaticamente a posição onde você posicionar na tela.',
    highlightText: 'Nunca mais tampe botões vitais da Uber ou 99: posicione a janelinha onde for mais ergonômico no seu smartphone.'
  },
  {
    id: 'tela-config-card-2',
    filename: 'TelaConfigCard2.jpeg',
    title: 'Tema Claro/Escuro & Botão Permanente',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '🌙 TEMA & BOTÃO FLUTUANTE',
    badgeType: 'green',
    summary: 'Alterne entre fundo Claro (Branco) e Escuro (Preto), ligue a bolinha flutuante permanente na borda da tela e ative a exibição opcional de Lucro Líquido na janela.',
    highlightText: 'Conforto visual completo para rodar tanto de dia sob sol forte quanto à noite sem ofuscar a visão.'
  },
  {
    id: 'tela-config-card-3',
    filename: 'TelaConfigCard3.jpeg',
    title: 'Tempo de Exibição, Veículo & Alerta Rodízio SP',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '🚗 VEÍCULO & RODÍZIO SP',
    badgeType: 'amber',
    summary: 'Defina o tempo de tela do card (3s a 7s), cadastre modelo e placa do veículo com detecção automática do dia de Rodízio em SP (Centro Expandido) e alerta sonoro preventivo.',
    highlightText: 'Evite multas pesadas de rodízio na capital paulista com aviso preventivo antes de aceitar chamadas na zona restrita.'
  },
  {
    id: 'tela-ganhos-1',
    filename: 'TelaGanhos1.jpeg',
    title: 'Critérios de Ganho por Km e Ganho por Hora',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '🚦 SEMÁFORO & METAS',
    badgeType: 'green',
    summary: 'Calibre os limites exatos do Semáforo: Ganho por Km (Ruim até R$ 1,80/km, Bom a partir de R$ 2,00/km) e Ganho por Hora (Ruim até R$ 35,00/h, Bom a partir de R$ 40,00/h) com régua colorida e botões [+] e [-].',
    highlightText: 'O cérebro financeiro do GoOrNo: você decide sua margem e o app aprova apenas corridas lucrativas.'
  },
  {
    id: 'tela-ganhos-2',
    filename: 'TelaGanhos2.jpeg',
    title: 'Limites de Embarque Distante & Corrida Curta',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '⚠️ EMBARQUE & CORRIDA CURTA',
    badgeType: 'amber',
    summary: 'Configure com controle deslizante o Limite de Embarque Distante (máximo tolerado de 2,0 km com alerta ⚠️ Emb. Longe) e o Limite de Corrida Curta (até 5,0 km com selo ⚡ CORRIDA CURTA).',
    highlightText: 'Evite deslocamentos longos não remunerados e priorize corridas rápidas para bater suas metas diárias.'
  },
  {
    id: 'tela-ganhos-3',
    filename: 'TelaGanhos3.jpeg',
    title: 'Avaliação do Passageiro & Bloqueio por Nota',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '★ NOTA DO PASSAGEIRO',
    badgeType: 'red',
    summary: 'Defina notas de corte para passageiros (Verde Boa >= 4,85, Amarela Média e Vermelha Ruim < 4,70) com trava para forçar Semáforo Vermelho (Recusar) para clientes mal avaliados.',
    highlightText: 'Proteção preventiva contra passageiros problemáticos ou com histórico negativo nas plataformas.'
  },
  {
    id: 'tela-historico',
    filename: 'TelaHistorico.jpeg',
    title: 'Histórico de Corridas & Auditoria de Lucro',
    category: 'atalhos',
    categoryLabel: 'Atalhos & Trânsito',
    badge: '📋 AUDITORIA COM LUCRO REAL',
    badgeType: 'blue',
    summary: 'Registro completo de chamadas capturadas: plataforma, KM bruto, hora bruta, lucro líquido descontado combustível, nota do passageiro e botões para favoritar, marcar risco ou abrir Street View.',
    highlightText: 'Audite seus ganhos reais corrida a corrida com histórico detalhado e conferência de faturamento.'
  },

  // ==========================================
  // 3. ÁREAS DE RISCO E ÁREAS FAVORITAS (6 telas)
  // ==========================================
  {
    id: 'seguranca-area-risco-1',
    filename: 'AreaRisco1.jpeg',
    title: 'Cadastro Imediato de Área de Risco',
    category: 'safety',
    categoryLabel: 'Áreas de Risco e Áreas Favoritas',
    badge: '⚠️ BLOQUEIO DE RUA OU BAIRRO',
    badgeType: 'red',
    summary: 'Modal rápido para cadastrar a rua exata ou o bairro inteiro como área perigosa diretamente a partir do histórico ou durante o trabalho.',
    highlightText: 'Bloqueie endereços perigosos em 1 toque: qualquer chamada para esse local forçará semáforo vermelho imediato.'
  },
  {
    id: 'seguranca-area-risco-2',
    filename: 'AreaRisco2.jpeg',
    title: 'Central de Segurança & Bloqueio de Risco',
    category: 'safety',
    categoryLabel: 'Áreas de Risco e Áreas Favoritas',
    badge: '🛡️ PROTEÇÃO ATIVA',
    badgeType: 'red',
    summary: 'Ajuste de proteção ativa: ligue alertas na janela flutuante, force semáforo VERMELHO automático para locais perigosos e ative a Base Nacional de Zonas Críticas (favelas, vielas e comunidades).',
    highlightText: 'Proteção inteligente que barra corridas perigosas mesmo que a tarifa oferecida pareça alta.'
  },
  {
    id: 'seguranca-area-risco-3',
    filename: 'AreaRisco3.jpeg',
    title: 'Pesquisa e Gestão de Locais Bloqueados',
    category: 'safety',
    categoryLabel: 'Áreas de Risco e Áreas Favoritas',
    badge: '🛡️ LOCAIS BLOQUEADOS (3)',
    badgeType: 'red',
    summary: 'Pesquise e valide ruas no mapa oficial e visualize sua lista de Seus Locais Bloqueados (Rua Valdomiro Rodrigues, Vila São José, Jardim Santa Cristina) com botão para testar alerta na janela.',
    highlightText: 'Controle total dos seus pontos de bloqueio com pesquisa geográfica e remoção a qualquer momento.'
  },
  {
    id: 'seguranca-regiao-favorita-1',
    filename: 'Favorita1.jpeg',
    title: 'Cadastro de Regiões e Bairros Favoritos',
    category: 'safety',
    categoryLabel: 'Áreas de Risco e Áreas Favoritas',
    badge: '⭐ ADICIONAR FAVORITA',
    badgeType: 'green',
    summary: 'Cadastre seus bairros preferidos, shoppings, aeroportos ou regiões seguras com apenas um toque a partir do histórico de chamadas.',
    highlightText: 'Personalize seu mapa de trabalho: priorize destinos lucrativos e bairros que você mais gosta de atender.'
  },
  {
    id: 'seguranca-regiao-favorita-2',
    filename: 'Favorita2.jpeg',
    title: 'Bairros Favoritos & Zona de Conforto',
    category: 'safety',
    categoryLabel: 'Áreas de Risco e Áreas Favoritas',
    badge: '⭐ ZONA DE CONFORTO',
    badgeType: 'green',
    summary: 'Ative o destaque de bairros favoritos com selo verde e estrela no card flutuante e utilize a busca integrada no mapa para encontrar bairros nobres e regiões preferidas.',
    highlightText: 'Trabalhe onde você gosta: destaque imediato no card quando a corrida tem origem ou destino na sua lista.'
  },
  {
    id: 'seguranca-regiao-favorita-3',
    filename: 'Favorita3.jpeg',
    title: 'Lista de Bairros Favoritos Cadastrados',
    category: 'safety',
    categoryLabel: 'Áreas de Risco e Áreas Favoritas',
    badge: '⭐ BAIRROS FAVORITOS (7)',
    badgeType: 'green',
    summary: 'Painel com a lista dos Seus Bairros Favoritos (Rua Doná Júlia César Ferreira, Rua Atílio Néris, Rua dos Vianas, etc.) com busca direta no mapa para adicionar novos locais com 1 clique.',
    highlightText: 'Cadastre seus bairros seguros e destinos prioritários para direcionar seu dia de faturamento.'
  }
];

export const EXTRA_SCREENSHOTS: ScreenshotItem[] = [
  {
    id: 'card-tipo-1',
    filename: 'CardImpactoNew.jpeg',
    title: 'Card Impacto Pro (Recomendado)',
    category: 'atalhos',
    categoryLabel: '3 Modelos de Janela Flutuante',
    badge: '✨ MODELO 1: IMPACTO PRO',
    badgeType: 'green',
    summary: 'Design moderno com valor gigante no centro, km e tempo no topo e faixa sólida com veredito instantâneo (✓ COMPENSA: R$ 5,62 · 1,1 km em 8 min · R$ 5,11/km · R$ 42,15/h · ★ 4,93) com alerta de desembarque em área de risco.',
    highlightText: 'Decisão em frações de segundo: leitura ultra nítida de R$/km e R$/hora com alerta preventivo de segurança.'
  },
  {
    id: 'card-tipo-2',
    filename: 'Card3PilaresNew.jpeg',
    title: 'Card 3 Pilares (R$/Km, R$/Hora e Nota)',
    category: 'atalhos',
    categoryLabel: '3 Modelos de Janela Flutuante',
    badge: '📊 MODELO 2: 3 PILARES',
    badgeType: 'blue',
    summary: 'Resumo no topo em linha única e 3 colunas com barras coloridas: R$ 5,62 · 2,6 km em 8 min (R$ 2,16/km · R$ 42/h · ★ 4,96) com identificação automática de ⚡ CORRIDA CURTA.',
    highlightText: 'Equilíbrio perfeito de métricas em colunas verticais com barras dinâmicas do semáforo financeiro.'
  },
  {
    id: 'card-tipo-3',
    filename: 'CardCompactoNew.jpeg',
    title: 'Card Compacto (Discreto)',
    category: 'atalhos',
    categoryLabel: '3 Modelos de Janela Flutuante',
    badge: '✨ MODELO 3: COMPACTO',
    badgeType: 'purple',
    summary: 'Formato compacto e objetivo que ocupa o mínimo de tela: R$ 39,60 por 20,9 km em 48 min (R$ 1,89/km · R$ 49,50/h · ★ 4,89) com alertas de ⚠️ EMBARQUE DISTANTE e ⏱️ VIAGEM LONGA.',
    highlightText: 'Design minimalista e discreto que preserva a visão do mapa sem esconder nenhuma métrica financeira.'
  },
  {
    id: 'uber-ruim',
    filename: 'UberRuimNew.jpeg',
    title: 'Chamada no Prejuízo Detectada na Uber',
    category: 'atalhos',
    categoryLabel: 'Decisão em um Relance',
    badge: '🔴 NÃO COMPENSA',
    badgeType: 'red',
    summary: 'Chamada no prejuízo: R$ 9,76 por 6,5 km em 18 min (apenas R$ 1,50/km · R$ 33/h · ★ 4,95). O GoOrNo calcula combustível e tempo, emitindo alerta vermelho imediato no card flutuante para você não pagar para trabalhar.',
    highlightText: 'Proteja seu tempo e combustível recusando corridas deficitárias com apenas um olhar.'
  },
  {
    id: 'uber-boa',
    filename: 'UberBoaNew.jpeg',
    title: 'Chamada Altamente Lucrativa Aprovada',
    category: 'atalhos',
    categoryLabel: 'Decisão em um Relance',
    badge: '🟢 COMPENSA',
    badgeType: 'green',
    summary: 'Corrida excelente aprovada pelo semáforo: R$ 9,28 por 3,9 km em 12 min (R$ 2,38/km · R$ 46/h · ★ 4,95). O GoOrNo aprova com card verde e identifica automaticamente como ⚡ CORRIDA CURTA com margem limpa no bolso.',
    highlightText: 'Aceite com segurança chamadas que realmente colocam dinheiro limpo no seu bolso.'
  },
  {
    id: 'viagem-compensa',
    filename: 'ViagemCompensa.png',
    title: 'Chamada Lucrativa: ✓ COMPENSA',
    category: 'atalhos',
    categoryLabel: 'Decisão em um Relance',
    badge: '🟢 CORRIDA APROVADA',
    badgeType: 'green',
    summary: 'Card verde com veredito instantâneo aprovando a corrida (✓ COMPENSA: R$ 6,62 · 2,3 km em 10 min · R$ 2,88/km · R$ 39,72/h · passageiro nota 4,88).',
    highlightText: 'Dinheiro no bolso garantido: cálculo automático descontando combustível e desgaste real do veículo.'
  },
  {
    id: 'viagem-nao-compensa',
    filename: 'ViagemNaoCompensa.png',
    title: 'Chamada no Prejuízo: ✕ RECUSAR',
    category: 'atalhos',
    categoryLabel: 'Decisão em um Relance',
    badge: '🔴 NÃO COMPENSA',
    badgeType: 'red',
    summary: 'Card com contorno vermelho alertando corrida no prejuízo com embarque longe (✕ NÃO COMPENSA: R$ 33,82 · 20,8 km em 44 min · R$ 1,63/km · R$ 46,12/h · ★ 4,97 · Emb. Longe 2,8 km).',
    highlightText: 'Proteja seu patrimônio e evite pagar para trabalhar! O GoOrNo avisa embarque distante e tarifa insuficiente para cobrir seus custos.'
  }
];

export const ALL_SCREENSHOTS = [...SCREENSHOTS, ...EXTRA_SCREENSHOTS];

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
                  18 Telas Oficiais
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
                  R$ 6,62 por 2,3 km (R$ 2,88/km · R$ 39,72/h). O GoOrNo aprova na hora com card verde destacando alta margem.
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
                  R$ 56,47 por 34,9 km (R$ 1,62/km · R$ 57,43/h). O GoOrNo avisa em vermelho para não pagar para trabalhar em viagens longas com km insuficiente.
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
            { key: 'all', label: 'Todas as Telas (18)', icon: Sparkles },
            { key: 'streetview', label: '📍 Notificação & Street View (3)', icon: MapPin },
            { key: 'atalhos', label: '⚡ Atalhos & Trânsito (9)', icon: Zap },
            { key: 'safety', label: '🛡️ Áreas de Risco e Áreas Favoritas (6)', icon: ShieldCheck }
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
                <div className="relative mx-auto p-1.5 bg-[#0a110f] rounded-[22px] border-[2px] border-[#20342f] shadow-lg max-w-[135px] group-hover:border-[#799900]/80 transition-colors">
                  {/* Dynamic Island / Front Camera */}
                  <div className="absolute top-2 left-1/2 -translate-x-1/2 w-8 h-1.5 bg-black rounded-full z-10 flex items-center justify-end pr-1 pointer-events-none">
                    <div className="w-1.5 h-1.5 rounded-full bg-[#1b2220]"></div>
                  </div>
                  {/* Tela interna do smartphone */}
                  <div className="relative aspect-[1/2.22] rounded-[16px] overflow-hidden bg-black flex items-center justify-center">
                    <img
                      src={`/telas/${item.filename}`}
                      alt={item.title}
                      className="w-full h-full object-contain group-hover:scale-105 transition-transform duration-300"
                      loading="lazy"
                    />
                  </div>
                </div>

                {/* Badge Flutuante no Topo */}
                <div className="absolute top-3 left-2 right-2 flex items-center justify-between pointer-events-none z-10">
                  {item.badge && (
                    <span
                      className={`text-[9.5px] font-extrabold px-2 py-0.5 rounded-full shadow-md backdrop-blur-xs whitespace-nowrap shrink-0 ${
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
            <div className="w-full md:w-1/2 bg-black/60 flex items-center justify-center p-4 sm:p-6 relative shrink-0 min-h-[320px] md:min-h-[460px]">
              {/* MOLDURA DE SMARTPHONE ELEGANTE E COMPLETA */}
              <div className="relative p-1.5 bg-[#09110e] rounded-[24px] border-[2.5px] border-[#223530] shadow-2xl max-w-[170px] sm:max-w-[180px] w-full my-auto">
                {/* Dynamic Island */}
                <div className="absolute top-2.5 left-1/2 -translate-x-1/2 w-10 h-2 bg-black rounded-full z-20 flex items-center justify-end pr-1 pointer-events-none">
                  <div className="w-1.5 h-1.5 rounded-full bg-[#1b2220]"></div>
                </div>
                <div className="relative aspect-[1/2.22] rounded-[18px] overflow-hidden bg-black flex items-center justify-center">
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
                      className={`text-[10px] font-extrabold px-2.5 py-0.5 rounded-full whitespace-nowrap shrink-0 ${
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

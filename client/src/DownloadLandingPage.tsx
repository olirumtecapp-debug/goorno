import React, { useState } from 'react';
import {
  Download,
  ShieldCheck,
  Smartphone,
  AlertTriangle,
  CheckCircle2,
  HelpCircle,
  ExternalLink,
  ChevronDown,
  ChevronUp,
  Sparkles,
  Zap,
  Clock,
  DollarSign,
  Layers,
  ArrowRight,
  Mail,
  Bell,
  RefreshCw
} from 'lucide-react';
import { ContactModal } from './ContactModal';

interface DownloadLandingPageProps {
  onOpenSimulator?: () => void;
  onOpenAdmin?: () => void;
}

export function DownloadLandingPage({ onOpenSimulator, onOpenAdmin }: DownloadLandingPageProps) {
  const [openFaq, setOpenFaq] = useState<number | null>(null);
  const [isContactOpen, setIsContactOpen] = useState(false);

  const toggleFaq = (index: number) => {
    setOpenFaq(openFaq === index ? null : index);
  };

  const handleDownloadApk = () => {
    // Redireciona para o download do APK oficial
    window.location.href = '/GoOrNo.apk';
  };

  return (
    <div className="min-h-screen bg-[#f5f7f2] text-[#173a34] font-sans antialiased selection:bg-[#d9f56a] selection:text-[#173a34]">
      {/* TOP HEADER */}
      <header className="sticky top-0 z-50 bg-[#173a34] border-b border-[#122e29] shadow-sm">
        <div className="max-w-5xl mx-auto px-4 sm:px-6 h-18 flex items-center justify-between">
          <div className="flex items-center gap-3.5">
            <div className="w-11 h-11 sm:w-12 sm:h-12 rounded-2xl bg-gradient-to-br from-[#234e45] to-[#122e29] border border-[#d9f56a]/40 shadow-md p-1.5 flex items-center justify-center">
              <img
                src="/logo-goorno-clean.png"
                alt="GoOrNo Copiloto Oficial"
                className="w-full h-full object-contain filter drop-shadow-sm"
                onError={(e) => {
                  (e.target as HTMLImageElement).src = '/logo-goorno.png';
                }}
              />
            </div>
            <div>
              <span className="text-xl sm:text-2xl font-black text-white tracking-tight flex items-center gap-2">
                GoOrNo
                <span className="text-[10px] font-extrabold px-2 py-0.5 rounded-full bg-[#d9f56a] text-[#173a34] uppercase tracking-normal">
                  v3.3.4
                </span>
              </span>
              <p className="text-[10px] text-[#dce5dc]/70 hidden sm:block">
                Copiloto de Ganhos para Motoristas de App
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 sm:gap-3">
            <button
              onClick={() => setIsContactOpen(true)}
              className="text-xs font-semibold text-[#dce5dc] hover:text-[#d9f56a] px-2.5 py-1.5 rounded-lg transition cursor-pointer flex items-center gap-1.5"
            >
              <Mail className="w-4 h-4 text-[#d9f56a]" />
              <span className="hidden sm:inline">Fale Conosco</span>
            </button>

            {onOpenSimulator && (
              <button
                onClick={onOpenSimulator}
                className="text-xs font-semibold text-[#dce5dc] hover:text-[#d9f56a] px-2.5 py-1.5 rounded-lg transition cursor-pointer flex items-center gap-1.5"
              >
                <span>📸</span>
                <span className="hidden sm:inline">Ver 21 Telas Reais</span>
              </button>
            )}

            <button
              onClick={handleDownloadApk}
              className="bg-[#d9f56a] hover:bg-[#cbf052] text-[#173a34] font-black text-xs sm:text-sm px-3.5 sm:px-4 py-2 rounded-xl shadow-sm transition flex items-center gap-1.5 cursor-pointer"
            >
              <Download className="w-4 h-4" />
              <span>Baixar APK</span>
            </button>
          </div>
        </div>
      </header>

      {/* HERO SECTION COM DESTAQUE MÁXIMO PARA O LOGO */}
      <section className="relative overflow-hidden pt-10 pb-16 px-4 sm:px-6">
        <div className="max-w-4xl mx-auto text-center">
          
          {/* DESTAQUE PRINCIPAL DO LOGO */}
          <div className="flex flex-col items-center justify-center mb-6">
            <div className="relative group cursor-pointer" onClick={handleDownloadApk}>
              <div className="absolute -inset-1.5 bg-gradient-to-r from-[#d9f56a] via-emerald-400 to-[#d9f56a] rounded-[32px] blur-md opacity-40 group-hover:opacity-75 transition duration-500"></div>
              <div className="relative w-28 h-28 sm:w-36 sm:h-36 rounded-3xl bg-[#173a34] border-2 border-[#d9f56a] p-3 shadow-2xl flex items-center justify-center">
                <img
                  src="/logo-goorno-clean.png"
                  alt="GoOrNo Copiloto"
                  className="w-full h-full object-contain filter drop-shadow-md group-hover:scale-105 transition-transform duration-300"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = '/logo-goorno.png';
                  }}
                />
              </div>
            </div>
            <div className="mt-3.5 flex items-center gap-2">
              <span className="text-[11px] font-black uppercase tracking-[0.25em] text-[#173a34] bg-[#173a34]/10 px-3 py-1 rounded-full">
                Copiloto Oficial do Motorista
              </span>
            </div>
          </div>

          <div className="inline-flex items-center gap-2 bg-[#173a34]/10 border border-[#173a34]/20 rounded-full px-4 py-1.5 text-xs font-bold text-[#173a34] mb-4 shadow-xs">
            <Sparkles className="w-3.5 h-3.5 text-[#173a34]" />
            <span>7 DIAS DE TESTE GRÁTIS · SEM CARTÃO DE CRÉDITO</span>
          </div>

          <h1 className="text-3xl sm:text-5xl font-black text-[#173a34] tracking-tight leading-tight mb-4">
            Descubra se a corrida compensa em menos de 1 segundo!
          </h1>

          <p className="text-sm sm:text-lg text-[#71867f] max-w-2xl mx-auto mb-8 leading-relaxed">
            O GoOrNo lê a tela da <strong>Uber</strong> e da <strong>99</strong> automaticamente e exibe um card flutuante com cálculo de <strong>R$/km</strong>, <strong>R$/hora</strong> e veredito em cores para você não perder dinheiro na rua.
          </p>

          {/* MAIN DOWNLOAD ACTION CARD */}
          <div className="bg-white border-2 border-[#173a34] rounded-[28px] p-6 sm:p-8 max-w-md mx-auto shadow-[0_20px_50px_rgba(23,58,52,0.12)]">
            <div className="flex items-center justify-center gap-2 mb-3">
              <span className="text-xs font-bold px-3 py-1 bg-[#173a34] text-[#d9f56a] rounded-full">
                OFICIAL ANDROID
              </span>
              <span className="text-xs text-[#71867f] font-semibold">
                60.7 MB · Versão 3.3.4
              </span>
            </div>

            <button
              onClick={handleDownloadApk}
              className="w-full bg-[#173a34] hover:bg-[#122e29] text-[#d9f56a] font-extrabold text-lg py-4 px-6 rounded-2xl shadow-md transition flex items-center justify-center gap-3 cursor-pointer group"
            >
              <Download className="w-6 h-6 group-hover:scale-110 transition-transform" />
              <span>Baixar GoOrNo APK Grátis</span>
            </button>

            {onOpenSimulator && (
              <button
                onClick={onOpenSimulator}
                className="w-full mt-3 bg-[#f5f7f2] hover:bg-[#edf2eb] text-[#173a34] font-bold text-xs py-2.5 px-4 rounded-xl transition flex items-center justify-center gap-2 cursor-pointer border border-[#dce5dc]"
              >
                <span>📸 Ver as 21 Telas do App na Prática</span>
                <ArrowRight className="w-3.5 h-3.5 text-[#173a34]" />
              </button>
            )}

            <p className="text-[11px] text-[#71867f] mt-3">
              Compatível com Android 8.0 até Android 14+ (Samsung, Motorola, Xiaomi, etc.)
            </p>
          </div>

          {/* PROCESSO DE ÚLTIMA VERSÃO & NOTIFICAÇÕES DE ATUALIZAÇÃO NO CELULAR */}
          <div className="mt-10 bg-[#173a34] text-white border border-[#234e45] rounded-3xl p-6 sm:p-7 max-w-2xl mx-auto text-left shadow-lg">
            <div className="flex items-center justify-between gap-3 mb-4 flex-wrap">
              <div className="flex items-center gap-2.5">
                <div className="w-9 h-9 rounded-xl bg-[#d9f56a]/20 text-[#d9f56a] grid place-items-center">
                  <Sparkles className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-black text-sm sm:text-base text-white">
                    Última Versão Disponível: v3.3.4 (Build 21)
                  </h3>
                  <p className="text-[11px] text-[#dce5dc]/70">
                    Lançamento oficial atualizado em 14/10/2026
                  </p>
                </div>
              </div>
              <span className="text-[10px] font-black uppercase px-2.5 py-1 rounded-full bg-[#d9f56a] text-[#173a34]">
                🟢 VERSÃO ESTÁVEL
              </span>
            </div>

            {/* COMO O MOTORISTA FICA SABENDO DAS ATUALIZAÇÕES */}
            <div className="bg-[#122e29] border border-[#234e45] rounded-2xl p-4 mb-4">
              <h4 className="text-xs font-bold text-[#d9f56a] flex items-center gap-1.5 mb-1.5">
                <Bell className="w-4 h-4 text-[#d9f56a]" />
                Como você fica sabendo de novas atualizações?
              </h4>
              <p className="text-xs text-[#dce5dc]/90 leading-relaxed">
                O GoOrNo possui <strong>verificação automática integrada ao iniciar</strong>. Sempre que uma nova versão ou melhoria for disponibilizada, você receberá um <strong>aviso direto na tela do aplicativo</strong> com o resumo das novidades e o botão para atualizar em 1 clique sem perder nenhuma de suas configurações.
              </p>
            </div>

            <div className="space-y-2 text-xs text-[#dce5dc]">
              <div className="font-bold text-[11px] uppercase tracking-wider text-[#d9f56a] mb-1">
                Novidades da Versão 3.3.4:
              </div>
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#d9f56a] shrink-0 mt-0.5" />
                <span>Ícone oficial do GoOrNo exibido na barra superior ao lado do relógio.</span>
              </div>
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#d9f56a] shrink-0 mt-0.5" />
                <span>Botão '⏹️ Desativar' na notificação para desligar o copiloto em 1 toque.</span>
              </div>
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#d9f56a] shrink-0 mt-0.5" />
                <span>Layout do Plano Vitalício 100% alinhado (sem letras espremidas).</span>
              </div>
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#d9f56a] shrink-0 mt-0.5" />
                <span>Demonstração oficial com 21 chamadas reais da Uber e 99 na rua.</span>
              </div>
              <div className="flex items-start gap-2">
                <CheckCircle2 className="w-4 h-4 text-[#d9f56a] shrink-0 mt-0.5" />
                <span>Fale Conosco integrado (Caixa Postal) para contato direto com a equipe.</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* POR QUE O GOORNO NÃO ESTÁ NA PLAY STORE? (TRANSPARÊNCIA TOTAL) */}
      <section className="py-12 px-4 sm:px-6 bg-white border-y border-[#dce5dc]">
        <div className="max-w-4xl mx-auto">
          <div className="bg-[#f8faf7] border border-[#dce5dc] rounded-2xl p-6 sm:p-8">
            <div className="flex items-start gap-4">
              <div className="w-12 h-12 rounded-xl bg-[#173a34] text-[#d9f56a] grid place-items-center shrink-0">
                <ShieldCheck className="w-6 h-6" />
              </div>
              <div>
                <h2 className="text-xl sm:text-2xl font-bold text-[#173a34] mb-2">
                  Por que o GoOrNo é baixado em formato APK e não na Google Play Store?
                </h2>
                <p className="text-sm text-[#71867f] leading-relaxed mb-4">
                  Para conseguir calcular os ganhos dos motoristas em tempo real, o GoOrNo precisa de duas permissões especiais do Android:
                </p>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mb-4">
                  <div className="bg-white p-3.5 rounded-xl border border-[#dce5dc]">
                    <h3 className="font-bold text-xs text-[#173a34] flex items-center gap-1.5 mb-1">
                      <Layers className="w-4 h-4 text-[#173a34]" />
                      Janela Flutuante (Sobreposição)
                    </h3>
                    <p className="text-xs text-[#71867f]">
                      Para exibir o card colorido por cima da Uber ou da 99 sem fechar seu app de corrida.
                    </p>
                  </div>
                  <div className="bg-white p-3.5 rounded-xl border border-[#dce5dc]">
                    <h3 className="font-bold text-xs text-[#173a34] flex items-center gap-1.5 mb-1">
                      <Zap className="w-4 h-4 text-[#173a34]" />
                      Acessibilidade (Leitura de Tela)
                    </h3>
                    <p className="text-xs text-[#71867f]">
                      Para ler o preço, a quilometragem e o tempo da chamada sem você precisar digitar nada.
                    </p>
                  </div>
                </div>
                <div className="p-3.5 bg-amber-50 border border-amber-200 rounded-xl text-amber-900 text-xs leading-relaxed flex items-start gap-2.5">
                  <AlertTriangle className="w-5 h-5 shrink-0 text-amber-600 mt-0.5" />
                  <div>
                    <strong>Aviso importante:</strong> O Google restringe na Play Store aplicativos que interagem por cima de outros apps. Por essa razão, os maiores copilotos do Brasil são distribuídos via APK oficial direto aos motoristas. O GoOrNo é <strong>100% seguro, verificado, livre de anúncios de terceiros e sem vírus</strong>.
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      {/* GUIA PASSO A PASSO: COMO INSTALAR NO SEU ANDROID */}
      <section className="py-16 px-4 sm:px-6">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-12">
            <span className="text-xs font-bold text-[#173a34] uppercase tracking-wider bg-[#173a34]/10 px-3 py-1 rounded-full">
              Tutorial Rápido
            </span>
            <h2 className="text-2xl sm:text-4xl font-extrabold text-[#173a34] mt-3">
              Como Instalar o APK no seu Celular em 3 Passos
            </h2>
            <p className="text-sm text-[#71867f] mt-1">
              Leva menos de 1 minuto para deixar o GoOrNo rodando no seu aparelho
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* PASSO 1 */}
            <div className="bg-white border border-[#dce5dc] rounded-2xl p-6 shadow-xs relative flex flex-col justify-between">
              <div>
                <div className="w-10 h-10 rounded-xl bg-[#173a34] text-[#d9f56a] font-black text-lg grid place-items-center mb-4">
                  1
                </div>
                <h3 className="font-bold text-base text-[#173a34] mb-2">
                  Baixe o Arquivo APK
                </h3>
                <p className="text-xs text-[#71867f] leading-relaxed mb-4">
                  Clique no botão de download. Se o navegador Google Chrome exibir a mensagem:
                </p>
                <div className="p-3 bg-[#f8faf7] border border-[#dce5dc] rounded-xl text-[11px] font-mono text-[#173a34] mb-3">
                  ⚠️ "O arquivo pode ser nocivo. Fazer o download mesmo assim?"
                </div>
                <p className="text-xs text-[#173a34] font-semibold">
                  👉 Toque em <strong>"Fazer o download mesmo assim"</strong> (aviso padrão do Android para qualquer app baixado fora da Play Store).
                </p>
              </div>
            </div>

            {/* PASSO 2 */}
            <div className="bg-white border border-[#dce5dc] rounded-2xl p-6 shadow-xs relative flex flex-col justify-between">
              <div>
                <div className="w-10 h-10 rounded-xl bg-[#173a34] text-[#d9f56a] font-black text-lg grid place-items-center mb-4">
                  2
                </div>
                <h3 className="font-bold text-base text-[#173a34] mb-2">
                  Permitir Fontes Desconhecidas
                </h3>
                <p className="text-xs text-[#71867f] leading-relaxed mb-4">
                  Abra a notificação de download concluído e clique em <strong>Instalar</strong>. Se o celular pedir permissão:
                </p>
                <div className="p-3 bg-[#f8faf7] border border-[#dce5dc] rounded-xl text-[11px] text-[#173a34] mb-3">
                  📱 Toque em <strong>"Configurações"</strong> e ative a chavinha <strong>"Permitir desta fonte"</strong> (do Chrome ou Gerenciador de Arquivos).
                </div>
                <p className="text-xs text-[#173a34] font-semibold">
                  🛡️ Se o Play Protect avisar <em>"App não reconhecido"</em>, clique em <strong>"Mais detalhes"</strong> e depois em <strong>"Instalar assim mesmo"</strong>.
                </p>
              </div>
            </div>

            {/* PASSO 3 */}
            <div className="bg-white border border-[#dce5dc] rounded-2xl p-6 shadow-xs relative flex flex-col justify-between">
              <div>
                <div className="w-10 h-10 rounded-xl bg-[#173a34] text-[#d9f56a] font-black text-lg grid place-items-center mb-4">
                  3
                </div>
                <h3 className="font-bold text-base text-[#173a34] mb-2">
                  Ative seus 7 Dias Grátis
                </h3>
                <p className="text-xs text-[#71867f] leading-relaxed mb-4">
                  Abra o GoOrNo, digite seu WhatsApp com DDD e toque em <strong>"Verificar / Ativar Teste"</strong>.
                </p>
                <div className="p-3 bg-[#f8faf7] border border-[#dce5dc] rounded-xl text-[11px] text-[#173a34] mb-3">
                  ✅ Conceda a permissão de <strong>Sobreposição de Tela</strong> e de <strong>Acessibilidade</strong> quando o app solicitar.
                </div>
                <p className="text-xs text-[#173a34] font-semibold">
                  🎉 Pronto! Pode abrir a Uber ou a 99 e rodar tranquilo na rua com seu copiloto ativado!
                </p>
              </div>
            </div>
          </div>

          <div className="mt-10 text-center">
            <button
              onClick={handleDownloadApk}
              className="inline-flex items-center gap-3 bg-[#173a34] hover:bg-[#122e29] text-[#d9f56a] font-bold px-8 py-3.5 rounded-xl shadow-md transition cursor-pointer"
            >
              <Download className="w-5 h-5" />
              <span>Clique aqui para Baixar o APK Agora</span>
            </button>
          </div>
        </div>
      </section>

      {/* RECURSOS PRINCIPAIS */}
      <section className="py-14 px-4 sm:px-6 bg-white border-t border-[#dce5dc]">
        <div className="max-w-4xl mx-auto">
          <div className="text-center mb-10">
            <h2 className="text-2xl sm:text-3xl font-bold text-[#173a34]">
              Por que milhares de motoristas usam o GoOrNo?
            </h2>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5">
            <div className="p-5 rounded-2xl border border-[#dce5dc] bg-[#f8faf7]">
              <div className="w-10 h-10 rounded-xl bg-emerald-100 text-emerald-800 grid place-items-center mb-3">
                <DollarSign className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-sm text-[#173a34] mb-1">Cálculo Instantâneo de R$/km</h3>
              <p className="text-xs text-[#71867f]">
                Veja na hora se a corrida paga acima de R$ 2,00, R$ 2,50 ou se está te dando prejuízo.
              </p>
            </div>

            <div className="p-5 rounded-2xl border border-[#dce5dc] bg-[#f8faf7]">
              <div className="w-10 h-10 rounded-xl bg-blue-100 text-blue-800 grid place-items-center mb-3">
                <Clock className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-sm text-[#173a34] mb-1">Cálculo de R$/Hora</h3>
              <p className="text-xs text-[#71867f]">
                Descubra quanto você vai faturar por hora trabalhada considerando o tempo até o passageiro.
              </p>
            </div>

            <div className="p-5 rounded-2xl border border-[#dce5dc] bg-[#f8faf7]">
              <div className="w-10 h-10 rounded-xl bg-lime-100 text-[#173a34] grid place-items-center mb-3">
                <CheckCircle2 className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-sm text-[#173a34] mb-1">Veredito em Cores</h3>
              <p className="text-xs text-[#71867f]">
                🟢 Aceitar (Ótima), 🟡 Atenção (Média) ou 🔴 Recusar (Ruim) num relance de olho.
              </p>
            </div>

            <div className="p-5 rounded-2xl border border-[#dce5dc] bg-[#f8faf7]">
              <div className="w-10 h-10 rounded-xl bg-purple-100 text-purple-800 grid place-items-center mb-3">
                <ShieldCheck className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-sm text-[#173a34] mb-1">Filtro Anti-Propaganda</h3>
              <p className="text-xs text-[#71867f]">
                Não se confunde com anúncios de postos de gasolina, 99 Abastece ou ofertas de cartões.
              </p>
            </div>

            <div className="p-5 rounded-2xl border border-[#dce5dc] bg-[#f8faf7]">
              <div className="w-10 h-10 rounded-xl bg-amber-100 text-amber-800 grid place-items-center mb-3">
                <Smartphone className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-sm text-[#173a34] mb-1">Iconezinho Discreto</h3>
              <p className="text-xs text-[#71867f]">
                Não polui a barra de notificações e só abre na tela quando realmente toca uma chamada.
              </p>
            </div>

            <div className="p-5 rounded-2xl border border-[#dce5dc] bg-[#f8faf7]">
              <div className="w-10 h-10 rounded-xl bg-teal-100 text-teal-800 grid place-items-center mb-3">
                <Zap className="w-5 h-5" />
              </div>
              <h3 className="font-bold text-sm text-[#173a34] mb-1">Sem Travamentos</h3>
              <p className="text-xs text-[#71867f]">
                App nativo ultra rápido desenvolvido em Kotlin, sem gastar sua bateria nem travar o GPS.
              </p>
            </div>
          </div>
        </div>
      </section>

      {/* SEÇÃO GALERIA DE TELAS REAIS DO APP */}
      <section className="py-16 px-4 sm:px-6 bg-[#edf2eb] border-t border-[#dce5dc]">
        <div className="max-w-5xl mx-auto">
          <div className="text-center mb-10">
            <span className="text-xs font-bold text-[#173a34] uppercase tracking-wider bg-[#173a34]/10 px-3.5 py-1.5 rounded-full inline-flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-[#173a34]" />
              Galeria Oficial · 22 Telas Reais
            </span>
            <h2 className="text-2xl sm:text-4xl font-black text-[#173a34] mt-3">
              Veja Como o GoOrNo Funciona na Prática
            </h2>
            <p className="text-sm sm:text-base text-[#71867f] max-w-2xl mx-auto mt-2">
              Capturas reais de chamadas na Uber e na 99, cálculo de ganho por km, Street View e configurações.
            </p>
          </div>

          {/* DESTAQUE PRINCIPAL: VIAGEM BOA VS VIAGEM RUIM */}
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
            {/* CARD VIAGEM BOA */}
            <div className="bg-white border-2 border-emerald-500 rounded-3xl p-5 shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-black px-3 py-1 bg-emerald-600 text-white rounded-full flex items-center gap-1.5">
                    <CheckCircle2 className="w-3.5 h-3.5" />
                    VIAGEM BOA (CARD VERDE)
                  </span>
                  <span className="text-xs font-bold text-emerald-700">R$ 2,17/km</span>
                </div>
                <h3 className="text-base font-extrabold text-[#173a34] mb-1">
                  Uber: R$ 10,83 por 5,0 km totais
                </h3>
                <p className="text-xs text-[#71867f] mb-4">
                  1,0 km busca + 4,0 km viagem (15 min totais). Ganho calculado de R$ 2,17/km e projeção de R$ 162,45/h. O GoOrNo aprova em verde para aceitar com lucro real.
                </p>
                <div className="bg-[#173a34]/5 rounded-2xl overflow-hidden aspect-[9/14] flex items-center justify-center p-2">
                  <img
                    src="/telas/UberBoa.jpeg"
                    alt="Chamada Uber Boa: R$ 10,83 por 5,0 km (R$ 2,17/km)"
                    className="w-full h-full object-contain rounded-xl"
                    loading="lazy"
                  />
                </div>
              </div>
              <div className="mt-4 pt-3 border-t border-[#f5f7f2] flex items-center justify-between">
                <span className="text-xs font-semibold text-emerald-700">🟢 R$ 2,17/km · Aceitar</span>
                {onOpenSimulator && (
                  <button
                    onClick={onOpenSimulator}
                    className="text-xs font-bold text-[#173a34] hover:underline cursor-pointer"
                  >
                    Ver detalhes →
                  </button>
                )}
              </div>
            </div>

            {/* CARD VIAGEM RUIM */}
            <div className="bg-white border-2 border-rose-400 rounded-3xl p-5 shadow-sm flex flex-col justify-between">
              <div>
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-black px-3 py-1 bg-rose-600 text-white rounded-full flex items-center gap-1.5">
                    <AlertTriangle className="w-3.5 h-3.5" />
                    VIAGEM RUIM (CARD VERMELHO)
                  </span>
                  <span className="text-xs font-bold text-rose-700">R$ 1,25/km</span>
                </div>
                <h3 className="text-base font-extrabold text-[#173a34] mb-1">
                  Uber: R$ 11,25 por 9,0 km totais
                </h3>
                <p className="text-xs text-[#71867f] mb-4">
                  4,4 km busca + 4,6 km viagem (23 min totais). Paga apenas R$ 1,25/km (abaixo do custo do veículo). O GoOrNo avisa em vermelho para você não pagar para trabalhar!
                </p>
                <div className="bg-[#173a34]/5 rounded-2xl overflow-hidden aspect-[9/14] flex items-center justify-center p-2">
                  <img
                    src="/telas/UberRuim.jpeg"
                    alt="Chamada Uber Ruim: R$ 11,25 por 9,0 km (R$ 1,25/km)"
                    className="w-full h-full object-contain rounded-xl"
                    loading="lazy"
                  />
                </div>
              </div>
              <div className="mt-4 pt-3 border-t border-[#f5f7f2] flex items-center justify-between">
                <span className="text-xs font-semibold text-rose-700">🔴 R$ 1,25/km · Recusar</span>
                {onOpenSimulator && (
                  <button
                    onClick={onOpenSimulator}
                    className="text-xs font-bold text-[#173a34] hover:underline cursor-pointer"
                  >
                    Ver detalhes →
                  </button>
                )}
              </div>
            </div>
          </div>

          {/* CARDS DE OUTRAS FUNCIONALIDADES */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-8">
            <div
              onClick={onOpenSimulator}
              className="bg-white border border-[#dce5dc] p-4 rounded-2xl cursor-pointer hover:border-[#173a34] transition group shadow-xs"
            >
              <div className="aspect-[9/12] bg-[#173a34]/5 rounded-xl overflow-hidden mb-3 p-1">
                <img
                  src="/telas/NotificacoesOrigem.jpeg"
                  alt="Street View da Origem"
                  className="w-full h-full object-contain rounded-lg group-hover:scale-105 transition"
                />
              </div>
              <span className="text-[10px] font-bold text-blue-700 uppercase bg-blue-50 px-2 py-0.5 rounded-md">
                Street View 360°
              </span>
              <h4 className="font-bold text-xs sm:text-sm text-[#173a34] mt-1.5">
                Foto Real da Rua do Passageiro
              </h4>
              <p className="text-[11px] text-[#71867f] mt-1">
                Veja a fachada do embarque e do destino em 1 clique antes de se deslocar.
              </p>
            </div>

            <div
              onClick={onOpenSimulator}
              className="bg-white border border-[#dce5dc] p-4 rounded-2xl cursor-pointer hover:border-[#173a34] transition group shadow-xs"
            >
              <div className="aspect-[9/12] bg-[#173a34]/5 rounded-xl overflow-hidden mb-3 p-1">
                <img
                  src="/telas/Notificacoes.jpeg"
                  alt="Notificação na barra com ícone e botão desativar"
                  className="w-full h-full object-contain rounded-lg group-hover:scale-105 transition"
                />
              </div>
              <span className="text-[10px] font-bold text-purple-700 uppercase bg-purple-50 px-2 py-0.5 rounded-md">
                Barra de Status Android
              </span>
              <h4 className="font-bold text-xs sm:text-sm text-[#173a34] mt-1.5">
                Ícone no Relógio & Botão Desativar
              </h4>
              <p className="text-[11px] text-[#71867f] mt-1">
                Fica na barra de notificações sem barulho e permite desligar o copiloto em 1 toque.
              </p>
            </div>

            <div
              onClick={onOpenSimulator}
              className="bg-white border border-[#dce5dc] p-4 rounded-2xl cursor-pointer hover:border-[#173a34] transition group shadow-xs"
            >
              <div className="aspect-[9/12] bg-[#173a34]/5 rounded-xl overflow-hidden mb-3 p-1">
                <img
                  src="/telas/TelaConfigCustos.jpeg"
                  alt="Calculadora de Custos"
                  className="w-full h-full object-contain rounded-lg group-hover:scale-105 transition"
                />
              </div>
              <span className="text-[10px] font-bold text-amber-700 uppercase bg-amber-50 px-2 py-0.5 rounded-md">
                Financeiro
              </span>
              <h4 className="font-bold text-xs sm:text-sm text-[#173a34] mt-1.5">
                Calculadora de Custo Real
              </h4>
              <p className="text-[11px] text-[#71867f] mt-1">
                Ajuste o valor do combustível e consumo do seu veículo para ver o lucro líquido real.
              </p>
            </div>
          </div>

          {/* CTA PARA ABRIR A GALERIA COMPLETA */}
          <div className="text-center">
            {onOpenSimulator ? (
              <button
                onClick={onOpenSimulator}
                className="inline-flex items-center gap-3 bg-[#173a34] hover:bg-[#122e29] text-[#d9f56a] font-bold text-sm sm:text-base px-8 py-4 rounded-2xl shadow-md transition cursor-pointer"
              >
                <span>📸 Abrir Galeria Completa com Todas as 21 Telas Reais</span>
                <ArrowRight className="w-5 h-5 text-[#d9f56a]" />
              </button>
            ) : (
              <a
                href="#simulator"
                className="inline-flex items-center gap-3 bg-[#173a34] hover:bg-[#122e29] text-[#d9f56a] font-bold text-sm sm:text-base px-8 py-4 rounded-2xl shadow-md transition cursor-pointer"
              >
                <span>📸 Abrir Galeria Completa com Todas as 21 Telas Reais</span>
                <ArrowRight className="w-5 h-5 text-[#d9f56a]" />
              </a>
            )}
          </div>
        </div>
      </section>

      {/* PERGUNTAS FREQUENTES (FAQ) */}
      <section className="py-16 px-4 sm:px-6">
        <div className="max-w-3xl mx-auto">
          <div className="text-center mb-10">
            <h2 className="text-2xl sm:text-3xl font-bold text-[#173a34]">
              Dúvidas Frequentes dos Motoristas
            </h2>
          </div>

          <div className="space-y-3">
            {[
              {
                q: 'O GoOrNo realmente é gratuito para testar?',
                a: 'Sim! Todo motorista ganha 7 dias de acesso completo gratuito para testar na rua. Não pedimos cartão de crédito, você só precisa digitar seu WhatsApp para ativar.'
              },
              {
                q: 'O aplicativo dá banimento na Uber ou 99?',
                a: 'Não! O GoOrNo não modifica o aplicativo da Uber ou da 99, não altera seu GPS e não aceita corridas automaticamente. Ele funciona exclusivamente como um leitor visual e calculadora de tela para ajudar você a decidir.'
              },
              {
                q: 'Por que o celular avisa sobre "aplicativo nocivo"?',
                a: 'Esse é um aviso de segurança obrigatório que o Android e o Chrome mostram sempre que você baixa qualquer arquivo fora da Google Play Store. O GoOrNo é 100% limpo, verificado e não contém nenhum vírus ou propaganda.'
              },
              {
                q: 'Se eu trocar de aparelho ou formatar, perco meu acesso?',
                a: 'Não! Sua licença fica vinculada ao seu número de WhatsApp. Pelo próprio aplicativo você pode transferir sua assinatura para seu novo celular.'
              }
            ].map((faq, idx) => (
              <div
                key={idx}
                className="bg-white border border-[#dce5dc] rounded-2xl overflow-hidden shadow-xs"
              >
                <button
                  onClick={() => toggleFaq(idx)}
                  className="w-full p-4 sm:p-5 text-left font-bold text-sm sm:text-base text-[#173a34] flex items-center justify-between cursor-pointer"
                >
                  <span>{faq.q}</span>
                  {openFaq === idx ? (
                    <ChevronUp className="w-5 h-5 text-[#71867f] shrink-0" />
                  ) : (
                    <ChevronDown className="w-5 h-5 text-[#71867f] shrink-0" />
                  )}
                </button>
                {openFaq === idx && (
                  <div className="px-4 sm:px-5 pb-5 text-xs sm:text-sm text-[#71867f] leading-relaxed border-t border-[#f5f7f2] pt-3">
                    {faq.a}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* FOOTER */}
      <footer className="bg-[#173a34] text-[#dce5dc] py-10 px-4 sm:px-6 border-t border-[#122e29]">
        <div className="max-w-4xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-6">
          <div className="flex items-center gap-3">
            <img
              src="/logo-goorno-clean.png"
              alt="GoOrNo"
              className="w-8 h-8 rounded-lg bg-white/10 p-0.5"
              onError={(e) => {
                (e.target as HTMLImageElement).src = '/logo-goorno.png';
              }}
            />
            <div>
              <span className="font-bold text-white text-sm">GoOrNo Copiloto</span>
              <p className="text-[11px] text-[#71867f]">© 2026 Todos os direitos reservados.</p>
            </div>
          </div>

          <div className="flex items-center gap-4 text-xs">
            {onOpenAdmin && (
              <button
                onClick={onOpenAdmin}
                className="text-[#71867f] hover:text-[#d9f56a] transition cursor-pointer"
              >
                Acesso Administrativo
              </button>
            )}
            <button
              onClick={() => setIsContactOpen(true)}
              className="text-[#d9f56a] hover:underline flex items-center gap-1.5 font-semibold cursor-pointer"
            >
              <Mail className="w-3.5 h-3.5" />
              <span>Fale Conosco (Caixa Postal)</span>
            </button>
          </div>
        </div>
      </footer>

      {/* MODAL DE CAIXA POSTAL / FALE CONOSCO */}
      <ContactModal isOpen={isContactOpen} onClose={() => setIsContactOpen(false)} />
    </div>
  );
}

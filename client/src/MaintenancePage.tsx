import React from 'react';
import { Wrench, ShieldCheck, Clock, Zap, CheckCircle2 } from 'lucide-react';

export function MaintenancePage() {
  return (
    <div className="min-h-screen bg-[#031B17] text-white flex flex-col justify-between selection:bg-[#22c55e] selection:text-black font-sans">
      {/* Top Banner / Navbar */}
      <header className="border-b border-white/10 bg-[#04241E]/80 backdrop-blur-md sticky top-0 z-50">
        <div className="max-w-5xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-[#166534] to-[#22c55e] flex items-center justify-center shadow-lg shadow-[#22c55e]/20 font-black text-xl text-black">
              🚦
            </div>
            <div>
              <span className="font-black text-xl tracking-tight text-white font-space">
                Go<span className="text-[#22c55e]">Or</span>No
              </span>
              <span className="block text-[11px] font-semibold text-white/50 -mt-1 tracking-wider uppercase">
                Copiloto do Motorista
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-[#22c55e]/10 text-[#22c55e] border border-[#22c55e]/30">
              <span className="w-2 h-2 rounded-full bg-[#22c55e] animate-pulse"></span>
              Servidores Ativos
            </span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-3xl mx-auto px-4 py-16 sm:py-24 flex-1 flex flex-col items-center justify-center text-center">
        {/* Animated Badge */}
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-[#eab308]/15 border border-[#eab308]/40 text-[#fef08a] text-xs font-bold uppercase tracking-wider mb-8 shadow-sm">
          <Wrench className="w-4 h-4 text-[#eab308] animate-spin" style={{ animationDuration: '6s' }} />
          Manutenção Programada
        </div>

        {/* Big Headline */}
        <h1 className="text-3xl sm:text-5xl font-black font-space tracking-tight text-white mb-6 leading-tight">
          Sistema em Atualização e <br />
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#22c55e] via-[#4ade80] to-[#a3e635]">
            Calibragem Técnica
          </span>
        </h1>

        {/* Subtitle */}
        <p className="text-base sm:text-lg text-white/75 max-w-xl mx-auto leading-relaxed mb-10">
          Estamos realizando ajustes internos e calibração de rotinas do sistema. Novos downloads e acessos ao portal estão temporariamente pausados.
        </p>

        {/* Status Card Box */}
        <div className="w-full max-w-xl bg-[#04241E] border border-white/10 rounded-2xl p-6 sm:p-8 shadow-2xl mb-8 text-left">
          <h2 className="text-xs font-bold text-white/50 uppercase tracking-widest mb-4 flex items-center gap-2">
            <Clock className="w-4 h-4 text-[#22c55e]" /> Status Operacional
          </h2>

          <div className="space-y-3.5">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#22c55e] shrink-0 mt-0.5" />
              <div>
                <span className="text-sm font-bold text-white">Serviço de Licenças e Autenticação</span>
                <p className="text-xs text-white/60">Operando normalmente para motoristas cadastrados.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Clock className="w-5 h-5 text-[#eab308] shrink-0 mt-0.5" />
              <div>
                <span className="text-sm font-bold text-[#fef08a]">Downloads Públicos Suspensos</span>
                <p className="text-xs text-white/60">Novos downloads temporariamente pausados até o término dos testes.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Zap className="w-5 h-5 text-[#eab308] shrink-0 mt-0.5" />
              <div>
                <span className="text-sm font-bold text-[#fef08a]">Calibração em Andamento</span>
                <p className="text-xs text-white/60">Ajustes de precisão em execução pela equipe técnica.</p>
              </div>
            </div>
          </div>
        </div>

        <p className="text-xs text-white/40">
          O portal retornará ao ar assim que a rodada de calibração for homologada.
        </p>
      </main>

      {/* Footer */}
      <footer className="border-t border-white/10 py-6 text-center text-xs text-white/40">
        <p>© 2026 GoOrNo Copilot — Todos os direitos reservados.</p>
      </footer>
    </div>
  );
}

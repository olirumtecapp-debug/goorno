import React from 'react';
import { Wrench, ShieldCheck, Download, MessageCircle, Clock, Zap, CheckCircle2 } from 'lucide-react';

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
              API & Licenças Ativas
            </span>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-3xl mx-auto px-4 py-16 sm:py-24 flex-1 flex flex-col items-center justify-center text-center">
        {/* Animated Badge */}
        <div className="inline-flex items-center gap-2 px-4 py-1.5 rounded-full bg-[#eab308]/15 border border-[#eab308]/40 text-[#fef08a] text-xs font-bold uppercase tracking-wider mb-8 shadow-sm">
          <Wrench className="w-4 h-4 text-[#eab308] animate-spin" style={{ animationDuration: '6s' }} />
          Atualização Programada do Sistema
        </div>

        {/* Big Headline */}
        <h1 className="text-3xl sm:text-5xl font-black font-space tracking-tight text-white mb-6 leading-tight">
          Estamos aprimorando o <br />
          <span className="text-transparent bg-clip-text bg-gradient-to-r from-[#22c55e] via-[#4ade80] to-[#a3e635]">
            GoOrNo para você
          </span>
        </h1>

        {/* Subtitle */}
        <p className="text-base sm:text-lg text-white/75 max-w-xl mx-auto leading-relaxed mb-10">
          Nossa equipe de engenharia está aplicando melhorias na calibração do motor financeiro, leitor inteligente de telas e no catálogo de demonstrações.
        </p>

        {/* Status Card Box */}
        <div className="w-full max-w-xl bg-[#04241E] border border-white/10 rounded-2xl p-6 sm:p-8 shadow-2xl mb-10 text-left">
          <h2 className="text-xs font-bold text-white/50 uppercase tracking-widest mb-4 flex items-center gap-2">
            <Clock className="w-4 h-4 text-[#22c55e]" /> Status da Operação
          </h2>

          <div className="space-y-3.5">
            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#22c55e] shrink-0 mt-0.5" />
              <div>
                <span className="text-sm font-bold text-white">Serviço de Licenças e Assinaturas</span>
                <p className="text-xs text-white/60">Funcionando normalmente para todos os motoristas com o app instalado.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <CheckCircle2 className="w-5 h-5 text-[#22c55e] shrink-0 mt-0.5" />
              <div>
                <span className="text-sm font-bold text-white">Atualizações de Aplicativo (APK)</span>
                <p className="text-xs text-white/60">Disponíveis normalmente para download e instalação direta.</p>
              </div>
            </div>

            <div className="flex items-start gap-3">
              <Zap className="w-5 h-5 text-[#eab308] shrink-0 mt-0.5" />
              <div>
                <span className="text-sm font-bold text-[#fef08a]">Portal Web em Revisão de Métricas</span>
                <p className="text-xs text-white/60">Calibrando os gráficos e dados de corridas para exibição 100% realista.</p>
              </div>
            </div>
          </div>
        </div>

        {/* Action Buttons */}
        <div className="flex flex-col sm:flex-row items-center justify-center gap-4 w-full max-w-md">
          <a
            href="/GoOrNo.apk"
            className="w-full sm:w-auto flex-1 inline-flex items-center justify-center gap-2 px-6 py-3.5 rounded-xl bg-gradient-to-r from-[#166534] to-[#22c55e] text-black font-black text-sm tracking-wide shadow-lg shadow-[#22c55e]/25 hover:brightness-110 transition active:scale-95"
          >
            <Download className="w-4 h-4" />
            Baixar APK Atualizado
          </a>

          <a
            href="https://wa.me/5511999999999?text=Ol%C3%A1%2C%20tenho%20d%C3%BAvidas%20sobre%20o%20GoOrNo"
            target="_blank"
            rel="noopener noreferrer"
            className="w-full sm:w-auto flex-1 inline-flex items-center justify-center gap-2 px-6 py-3.5 rounded-xl bg-white/10 hover:bg-white/15 border border-white/20 text-white font-bold text-sm tracking-wide transition active:scale-95"
          >
            <MessageCircle className="w-4 h-4 text-[#22c55e]" />
            Suporte WhatsApp
          </a>
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-white/10 py-6 text-center text-xs text-white/40">
        <p>© 2026 GoOrNo Copilot — Todos os direitos reservados.</p>
      </footer>
    </div>
  );
}

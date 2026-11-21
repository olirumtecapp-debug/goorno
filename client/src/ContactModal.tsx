import React, { useState } from 'react';
import {
  Mail,
  Send,
  X,
  CheckCircle2,
  AlertTriangle,
  Phone,
  User,
  ShieldCheck,
  Lock,
  ArrowRight,
  LogOut,
  Download
} from 'lucide-react';

interface ContactModalProps {
  isOpen: boolean;
  onClose: () => void;
  source?: 'web' | 'app';
}

export function ContactModal({ isOpen, onClose, source = 'web' }: ContactModalProps) {
  // Motorista Verificado (obrigatório no site)
  const [verifiedDriver, setVerifiedDriver] = useState<{ name: string; phone: string } | null>(null);
  const [loginPhone, setLoginPhone] = useState('');
  const [isVerifying, setIsVerifying] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);

  // Formulário de Mensagem
  const [category, setCategory] = useState<'duvida' | 'sugestao' | 'problema' | 'elogio' | 'outro'>('duvida');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successTicket, setSuccessTicket] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  // Verificação de Motorista no Site
  const handleVerifyDriver = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanDigits = loginPhone.replace(/\D/g, '');
    if (cleanDigits.length < 8) {
      setAuthError('Por favor, informe seu WhatsApp ou celular com DDD.');
      return;
    }

    setIsVerifying(true);
    setAuthError(null);

    try {
      const res = await fetch('/api/driver/verify-contact-access', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ phone: cleanDigits })
      });
      const data = await res.json();
      if (!res.ok || !data.ok) {
        throw new Error(data.message || 'Cadastro não localizado no GoOrNo.');
      }
      setVerifiedDriver({
        name: data.name || 'Motorista GoOrNo',
        phone: data.phone || cleanDigits
      });
    } catch (err: any) {
      setAuthError(err.message || 'Falha ao validar cadastro.');
    } finally {
      setIsVerifying(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) {
      setErrorMessage('Por favor, escreva sua mensagem antes de enviar.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    const driverName = verifiedDriver?.name || 'Motorista GoOrNo';
    const driverPhone = verifiedDriver?.phone || loginPhone.replace(/\D/g, '');

    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: driverName,
          contact: driverPhone,
          email: '',
          preferredReply: 'app',
          category,
          subject: subject.trim() || 'Contato via Caixa Postal',
          message: message.trim(),
          source
        })
      });

      const data = await res.json();
      if (!res.ok || !data.ok) {
        throw new Error(data.error || 'Erro ao enviar mensagem.');
      }

      setSuccessTicket(data.ticketId || `TKT-${Date.now().toString().slice(-6)}`);
      setSubject('');
      setMessage('');
    } catch (err: any) {
      setErrorMessage(err.message || 'Falha ao conectar com o servidor.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleReset = () => {
    setSuccessTicket(null);
    setErrorMessage(null);
    setAuthError(null);
    onClose();
  };

  const isWebUnauthenticated = source === 'web' && !verifiedDriver;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-md flex items-center justify-center p-3 sm:p-5 animate-fadeIn"
      onClick={handleReset}
    >
      <div
        className="bg-[#071d19] text-white border border-[#1b3830] rounded-2xl sm:rounded-3xl max-w-lg w-full shadow-[0_25px_60px_-15px_rgba(0,0,0,0.9),0_0_35px_rgba(217,255,79,0.08)] relative overflow-hidden font-sans my-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* LINHA DECORATIVA NEON VOLT NO TOPO */}
        <div className="h-1 w-full bg-gradient-to-r from-transparent via-[#d9ff4f] to-transparent opacity-80" />

        {/* CABEÇALHO ELEGANTE DARK */}
        <div className="px-5 py-3.5 sm:py-4 bg-[#051915] border-b border-[#132c25] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-xl bg-[#092b24] border border-[#1b3830] text-[#d9ff4f] grid place-items-center shrink-0 shadow-inner">
              <Mail className="w-4 h-4 text-[#d9ff4f]" />
            </div>
            <div>
              <h3 className="font-space font-bold text-base sm:text-lg text-white tracking-tight flex items-center gap-2 leading-none">
                Fale Conosco
                <span className="text-[10px] font-black uppercase px-2 py-0.5 rounded-full bg-[#d9ff4f] text-[#071d19] shadow-[0_0_10px_rgba(217,255,79,0.3)]">
                  Caixa Postal
                </span>
              </h3>
              <p className="text-[11.5px] text-[#94a89d] mt-1 leading-none font-medium">
                Canal exclusivo para motoristas cadastrados
              </p>
            </div>
          </div>

          <button
            onClick={handleReset}
            className="w-8 h-8 rounded-full bg-white/5 hover:bg-white/15 text-[#94a89d] hover:text-white grid place-items-center transition cursor-pointer border border-white/10"
            aria-label="Fechar"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* CORPO DO MODAL */}
        <div className="p-5 sm:p-6 bg-[#071d19]">
          {/* SUCESSO */}
          {successTicket ? (
            <div className="text-center py-6 sm:py-8 space-y-3">
              <div className="w-14 h-14 rounded-full bg-[#092b24] text-[#d9ff4f] grid place-items-center mx-auto border border-[#1b3830] shadow-[0_0_25px_rgba(217,255,79,0.25)]">
                <CheckCircle2 className="w-8 h-8 text-[#d9ff4f]" />
              </div>
              <h4 className="font-space text-xl font-bold text-white tracking-tight">
                Mensagem Entregue com Sucesso!
              </h4>
              <p className="text-xs sm:text-sm text-[#94a89d] max-w-sm mx-auto leading-relaxed">
                Sua solicitação foi registrada com o protocolo{' '}
                <span className="font-mono font-bold text-[#d9ff4f] bg-[#051915] px-2 py-0.5 rounded border border-[#1b3830]">
                  #{successTicket}
                </span>
                . A resposta será enviada diretamente para a <strong>Caixa Postal</strong> do seu aplicativo GoOrNo!
              </p>
              <div className="pt-2">
                <button
                  onClick={handleReset}
                  className="bg-[#d9ff4f] hover:bg-[#c8f038] text-[#071d19] font-space font-bold text-xs sm:text-sm px-6 py-2.5 rounded-xl transition cursor-pointer shadow-[0_4px_16px_rgba(217,255,79,0.3)] hover:shadow-[0_6px_20px_rgba(217,255,79,0.45)]"
                >
                  Concluir e Fechar
                </button>
              </div>
            </div>
          ) : isWebUnauthenticated ? (
            /* TRAVA DE LOGIN EXCLUSIVA PARA MOTORISTAS CADASTRADOS NO SITE */
            <form onSubmit={handleVerifyDriver} className="space-y-4">
              <div className="text-center space-y-2 pb-2 border-b border-[#132c25]">
                <div className="w-12 h-12 rounded-2xl bg-[#092b24] text-[#d9ff4f] grid place-items-center mx-auto border border-[#1b3830]">
                  <Lock className="w-6 h-6 text-[#d9ff4f]" />
                </div>
                <h4 className="font-space text-base sm:text-lg font-bold text-white">
                  Identificação do Motorista
                </h4>
                <p className="text-xs text-[#94a89d] max-w-sm mx-auto leading-relaxed">
                  Para garantir que sua resposta seja entregue na sua Caixa Postal interna, confirme o telefone cadastrado no seu app.
                </p>
              </div>

              {authError && (
                <div className="p-3 bg-rose-950/60 border border-rose-800/80 rounded-xl text-rose-300 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
                  <span>{authError}</span>
                </div>
              )}

              <div>
                <label className="block text-[11px] font-bold text-[#94a89d] uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                  <Phone className="w-3.5 h-3.5 text-[#d9ff4f]" />
                  WhatsApp / Celular Cadastrado no App
                </label>
                <input
                  type="text"
                  value={loginPhone}
                  onChange={(e) => setLoginPhone(e.target.value)}
                  placeholder="DDD + Número (ex: 11999998888)"
                  className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3.5 py-2.5 text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner font-mono"
                  autoFocus
                />
              </div>

              <button
                type="submit"
                disabled={isVerifying}
                className="w-full bg-[#d9ff4f] hover:bg-[#c8f038] text-[#071d19] font-space font-black text-xs sm:text-sm py-3 rounded-xl transition cursor-pointer flex items-center justify-center gap-2 shadow-[0_4px_16px_rgba(217,255,79,0.3)] disabled:opacity-50"
              >
                <span>{isVerifying ? 'Verificando...' : 'Acessar Fale Conosco'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>

              <div className="pt-2 text-center text-[11px] text-[#71857c] border-t border-[#132c25]">
                Ainda não tem o aplicativo instalado?{' '}
                <a
                  href="/GoOrNo.apk"
                  className="text-[#d9ff4f] font-bold hover:underline inline-flex items-center gap-1"
                >
                  Baixar GoOrNo
                  <Download className="w-3 h-3" />
                </a>
              </div>
            </form>
          ) : (
            /* FORMULÁRIO DE ENVIO PARA MOTORISTA IDENTIFICADO */
            <form onSubmit={handleSubmit} className="space-y-3">
              {/* BANNER DE MOTORISTA IDENTIFICADO */}
              {verifiedDriver && (
                <div className="p-2.5 bg-[#092b24] border border-[#1b3830] rounded-xl flex items-center justify-between text-xs">
                  <div className="flex items-center gap-2">
                    <ShieldCheck className="w-4 h-4 text-[#d9ff4f]" />
                    <span className="text-white font-medium">
                      Conectado como: <strong>{verifiedDriver.name}</strong> ({verifiedDriver.phone})
                    </span>
                  </div>
                  <button
                    type="button"
                    onClick={() => setVerifiedDriver(null)}
                    className="text-[#94a89d] hover:text-white text-[11px] flex items-center gap-1 border-0 bg-transparent cursor-pointer"
                  >
                    <LogOut className="w-3 h-3" />
                    Sair
                  </button>
                </div>
              )}

              {errorMessage && (
                <div className="p-3 bg-rose-950/60 border border-rose-800/80 rounded-xl text-rose-300 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Categoria */}
              <div>
                <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1">
                  Motivo do Contato
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-1.5">
                  {[
                    { id: 'duvida', label: 'Dúvida' },
                    { id: 'sugestao', label: 'Sugestão' },
                    { id: 'problema', label: 'Problema' },
                    { id: 'elogio', label: 'Elogio' }
                  ].map((cat) => (
                    <button
                      key={cat.id}
                      type="button"
                      onClick={() => setCategory(cat.id as any)}
                      className={`py-1.5 px-2 rounded-xl text-xs font-space font-bold border transition cursor-pointer text-center ${
                        category === cat.id
                          ? 'bg-[#d9ff4f] text-[#071d19] border-[#d9ff4f]'
                          : 'bg-[#051915] text-[#94a89d] border-[#1b3830] hover:text-white'
                      }`}
                    >
                      {cat.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Assunto */}
              <div>
                <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1">
                  Assunto
                </label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="Ex: Sugestão para o card ou dúvida sobre corrida"
                  className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner"
                />
              </div>

              {/* Mensagem */}
              <div>
                <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1">
                  Mensagem
                </label>
                <textarea
                  rows={4}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Escreva sua mensagem aqui com todos os detalhes..."
                  className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner resize-none"
                />
              </div>

              {/* BOTÃO ENVIAR */}
              <button
                type="submit"
                disabled={isSubmitting}
                className="w-full bg-[#d9ff4f] hover:bg-[#c8f038] text-[#071d19] font-space font-black text-xs sm:text-sm py-2.5 sm:py-3 rounded-xl transition cursor-pointer flex items-center justify-center gap-2 shadow-[0_4px_16px_rgba(217,255,79,0.3)] hover:shadow-[0_6px_20px_rgba(217,255,79,0.45)] disabled:opacity-50"
              >
                <Send className="w-4 h-4 text-[#071d19]" />
                <span>{isSubmitting ? 'Entregando na Caixa Postal...' : 'Enviar para Caixa Postal'}</span>
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

import React, { useState } from 'react';
import {
  Mail,
  Send,
  X,
  CheckCircle2,
  AlertTriangle,
  Phone,
  User,
  MessageSquare,
  HelpCircle,
  Lightbulb,
  Bug,
  ThumbsUp,
  FileQuestion
} from 'lucide-react';

interface ContactModalProps {
  isOpen: boolean;
  onClose: () => void;
  source?: 'web' | 'app';
}

export function ContactModal({ isOpen, onClose, source = 'web' }: ContactModalProps) {
  const [name, setName] = useState('');
  const [contact, setContact] = useState('');
  const [email, setEmail] = useState('');
  const [preferredReply, setPreferredReply] = useState<'whatsapp' | 'email'>('whatsapp');
  const [category, setCategory] = useState<'duvida' | 'sugestao' | 'problema' | 'elogio' | 'outro'>('duvida');
  const [subject, setSubject] = useState('');
  const [message, setMessage] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [successTicket, setSuccessTicket] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!message.trim()) {
      setErrorMessage('Por favor, escreva sua mensagem antes de enviar.');
      return;
    }

    setIsSubmitting(true);
    setErrorMessage(null);

    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: name.trim() || 'Motorista Autônomo',
          contact: contact.trim(),
          email: email.trim(),
          preferredReply,
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
      setName('');
      setContact('');
      setEmail('');
      setPreferredReply('whatsapp');
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
    onClose();
  };

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
                Canal direto com o desenvolvedor do GoOrNo
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

        {/* CORPO DO FORMULÁRIO */}
        <div className="p-5 sm:p-6 bg-[#071d19]">
          {successTicket ? (
            <div className="text-center py-6 sm:py-8 space-y-3">
              <div className="w-14 h-14 rounded-full bg-[#092b24] text-[#d9ff4f] grid place-items-center mx-auto border border-[#1b3830] shadow-[0_0_25px_rgba(217,255,79,0.25)]">
                <CheckCircle2 className="w-8 h-8 text-[#d9ff4f]" />
              </div>
              <h4 className="font-space text-xl font-bold text-white tracking-tight">
                Mensagem Entregue com Sucesso!
              </h4>
              <p className="text-xs sm:text-sm text-[#94a89d] max-w-sm mx-auto leading-relaxed">
                Sua solicitação foi registrada na Caixa Postal com o protocolo{' '}
                <span className="font-mono font-bold text-[#d9ff4f] bg-[#051915] px-2 py-0.5 rounded border border-[#1b3830]">
                  #{successTicket}
                </span>
                . O desenvolvedor analisa cada mensagem recebida e responderá conforme sua preferência.
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
          ) : (
            <form onSubmit={handleSubmit} className="space-y-3">
              {errorMessage && (
                <div className="p-3 bg-rose-950/60 border border-rose-800/80 rounded-xl text-rose-300 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Linha 1: Nome e WhatsApp lado a lado */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                    <User className="w-3 h-3 text-[#d9ff4f]" />
                    Seu Nome (Opcional)
                  </label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Ex: Carlos Motorista"
                    className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner"
                  />
                </div>

                <div>
                  <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                    <Phone className="w-3 h-3 text-[#d9ff4f]" />
                    WhatsApp / Celular (Opcional)
                  </label>
                  <input
                    type="text"
                    value={contact}
                    onChange={(e) => setContact(e.target.value)}
                    placeholder="DDD + Número (ex: 11999998888)"
                    className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner"
                  />
                </div>
              </div>

              {/* Linha 2: E-mail e Preferência de Retorno */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1 flex items-center gap-1.5">
                    <Mail className="w-3 h-3 text-[#d9ff4f]" />
                    E-mail (Opcional)
                  </label>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="seuemail@exemplo.com"
                    className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner"
                  />
                </div>

                <div>
                  <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1">
                    Prefere Retorno por:
                  </label>
                  <div className="grid grid-cols-2 gap-1.5">
                    <button
                      type="button"
                      onClick={() => setPreferredReply('whatsapp')}
                      className={`text-xs py-1.5 px-2 rounded-xl font-bold transition flex items-center justify-center gap-1.5 cursor-pointer border ${
                        preferredReply === 'whatsapp'
                          ? 'bg-[#092b24] text-[#d9ff4f] border-[#d9ff4f] shadow-[0_0_12px_rgba(217,255,79,0.25)]'
                          : 'bg-[#051915] text-[#94a89d] border-[#1b3830] hover:border-white/20 hover:text-white'
                      }`}
                    >
                      <span>💬 WhatsApp</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setPreferredReply('email')}
                      className={`text-xs py-1.5 px-2 rounded-xl font-bold transition flex items-center justify-center gap-1.5 cursor-pointer border ${
                        preferredReply === 'email'
                          ? 'bg-[#092b24] text-[#d9ff4f] border-[#d9ff4f] shadow-[0_0_12px_rgba(217,255,79,0.25)]'
                          : 'bg-[#051915] text-[#94a89d] border-[#1b3830] hover:border-white/20 hover:text-white'
                      }`}
                    >
                      <span>✉️ E-mail</span>
                    </button>
                  </div>
                </div>
              </div>

              {/* Linha 3: Motivo do Contato em pílulas com glow */}
              <div>
                <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1.5">
                  Motivo do Contato
                </label>
                <div className="flex flex-wrap items-center gap-1.5">
                  {[
                    { id: 'duvida', label: 'Dúvida', icon: '❓' },
                    { id: 'sugestao', label: 'Sugestão', icon: '💡' },
                    { id: 'problema', label: 'Problema', icon: '⚠️' },
                    { id: 'elogio', label: 'Elogio', icon: '👏' },
                    { id: 'outro', label: 'Outro', icon: '📌' }
                  ].map((cat) => (
                    <button
                      type="button"
                      key={cat.id}
                      onClick={() => setCategory(cat.id as any)}
                      className={`text-xs py-1 px-3 rounded-lg font-bold transition flex items-center gap-1 cursor-pointer border ${
                        category === cat.id
                          ? 'bg-[#d9ff4f] text-[#071d19] border-[#d9ff4f] shadow-[0_0_14px_rgba(217,255,79,0.35)] scale-[1.02]'
                          : 'bg-[#051915] text-[#94a89d] border-[#1b3830] hover:border-[#d9ff4f]/40 hover:text-white'
                      }`}
                    >
                      <span>{cat.icon}</span>
                      <span>{cat.label}</span>
                    </button>
                  ))}
                </div>
              </div>

              {/* Linha 4: Assunto */}
              <div>
                <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1">
                  Assunto
                </label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="Ex: Sugestão para o leitor de chamadas da 99"
                  className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl px-3 py-2 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 transition shadow-inner"
                />
              </div>

              {/* Linha 5: Mensagem */}
              <div>
                <label className="block text-[10.5px] font-bold text-[#94a89d] uppercase tracking-wider mb-1 flex items-center justify-between">
                  <span className="flex items-center gap-1">
                    <MessageSquare className="w-3 h-3 text-[#d9ff4f]" />
                    Mensagem *
                  </span>
                  <span className="text-[10px] text-[#4e6359] font-mono">
                    {message.length}/1000
                  </span>
                </label>
                <textarea
                  rows={3}
                  maxLength={1000}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Conte com detalhes o que você precisa ou deixe sua sugestão..."
                  required
                  className="w-full bg-[#051915] border border-[#1b3830] focus:border-[#d9ff4f] rounded-xl p-3 text-xs sm:text-sm text-white placeholder-[#4e6359] focus:outline-none focus:ring-1 focus:ring-[#d9ff4f]/30 resize-none transition shadow-inner"
                />
              </div>

              {/* Linha 6: Botões de Ação */}
              <div className="pt-2 flex items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={handleReset}
                  className="px-4 py-2 rounded-xl text-xs font-semibold text-[#94a89d] hover:text-white hover:bg-white/5 transition cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || !message.trim()}
                  className="bg-[#d9ff4f] hover:bg-[#c8f038] disabled:opacity-40 text-[#071d19] font-space font-bold text-xs sm:text-sm px-6 py-2.5 rounded-xl shadow-[0_4px_16px_rgba(217,255,79,0.35)] hover:shadow-[0_6px_22px_rgba(217,255,79,0.5)] transition flex items-center gap-2 cursor-pointer disabled:cursor-not-allowed"
                >
                  <Send className="w-3.5 h-3.5 text-[#071d19]" />
                  <span>{isSubmitting ? 'Enviando...' : 'Enviar Mensagem'}</span>
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}

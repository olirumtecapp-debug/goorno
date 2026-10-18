import React, { useState } from 'react';
import {
  Mail,
  Send,
  X,
  CheckCircle2,
  AlertTriangle,
  Phone,
  User
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
      className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 sm:p-6 animate-fadeIn"
      onClick={handleReset}
    >
      <div
        className="bg-white text-[#071d19] border border-[rgba(7,29,25,0.12)] rounded-2xl sm:rounded-3xl max-w-lg w-full shadow-[0_24px_60px_rgba(7,29,25,0.18)] relative font-sans my-auto"
        onClick={(e) => e.stopPropagation()}
      >
        {/* HEADER LIMPO E TRANSPARENTE */}
        <div className="px-5 py-3 sm:py-3.5 bg-[#fbfcf7] border-b border-[rgba(7,29,25,0.08)] flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-xl bg-[#f3f5ed] border border-[rgba(7,29,25,0.08)] text-[#071d19] grid place-items-center shrink-0">
              <Mail className="w-4 h-4 text-[#071d19]" />
            </div>
            <div>
              <h3 className="font-space font-bold text-base text-[#071d19] tracking-tight flex items-center gap-2 leading-none">
                Fale Conosco
                <span className="text-[9px] font-black uppercase px-2 py-0.5 rounded-full bg-[#d9ff4f] text-[#071d19] border border-[rgba(7,29,25,0.08)]">
                  Caixa Postal
                </span>
              </h3>
              <p className="text-[11px] text-[#63756b] mt-0.5 leading-none">
                Canal direto com o desenvolvedor do GoOrNo
              </p>
            </div>
          </div>

          <button
            onClick={handleReset}
            className="w-8 h-8 rounded-full bg-[#f3f5ed] hover:bg-[#e6ebdf] text-[#071d19] grid place-items-center transition cursor-pointer border border-[rgba(7,29,25,0.08)]"
            aria-label="Fechar"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>

        {/* CORPO DO FORMULÁRIO COMPACTO */}
        <div className="px-5 py-3.5 sm:py-4 bg-white">
          {successTicket ? (
            <div className="text-center py-4">
              <div className="w-12 h-12 rounded-full bg-[#dff4af] text-[#557000] grid place-items-center mx-auto mb-3 border border-[#c4e680]">
                <CheckCircle2 className="w-6 h-6" />
              </div>
              <h4 className="font-space text-lg font-bold text-[#071d19] mb-1">
                Mensagem Entregue com Sucesso!
              </h4>
              <p className="text-xs text-[#63756b] max-w-xs mx-auto leading-relaxed mb-3">
                Sua solicitação foi registrada na Caixa Postal com o protocolo{' '}
                <span className="font-mono font-bold text-[#071d19] bg-[#f3f5ed] px-1.5 py-0.5 rounded border border-[rgba(7,29,25,0.1)]">
                  #{successTicket}
                </span>
                . O desenvolvedor analisa cada mensagem recebida e responderá conforme sua preferência.
              </p>
              <button
                onClick={handleReset}
                className="bg-[#071d19] hover:bg-[#0d302a] text-[#d9ff4f] font-space font-bold text-xs px-5 py-2 rounded-xl transition cursor-pointer shadow-xs"
              >
                Fechar
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-2.5">
              {errorMessage && (
                <div className="p-2.5 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              {/* Linha 1: Nome e WhatsApp lado a lado */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1 flex items-center gap-1">
                    <User className="w-3 h-3 text-[#071d19]" />
                    Seu Nome (Opcional)
                  </label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Ex: Carlos Motorista"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3 py-1.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] transition"
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1 flex items-center gap-1">
                    <Phone className="w-3 h-3 text-[#071d19]" />
                    WhatsApp / Telefone (Opcional)
                  </label>
                  <input
                    type="text"
                    value={contact}
                    onChange={(e) => setContact(e.target.value)}
                    placeholder="DDD + Número (ex: 11999998888)"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3 py-1.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] transition"
                  />
                </div>
              </div>

              {/* Linha 2: E-mail e Preferência de Retorno */}
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                <div>
                  <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1 flex items-center gap-1">
                    <Mail className="w-3 h-3 text-[#071d19]" />
                    E-mail (Opcional)
                  </label>
                  <input
                    type="email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="seuemail@exemplo.com"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3 py-1.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] transition"
                  />
                </div>

                <div>
                  <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1">
                    Prefere retorno por:
                  </label>
                  <div className="grid grid-cols-2 gap-1.5">
                    <button
                      type="button"
                      onClick={() => setPreferredReply('whatsapp')}
                      className={`text-[11px] py-1 px-2 rounded-xl font-bold transition flex items-center justify-center gap-1 cursor-pointer border ${
                        preferredReply === 'whatsapp'
                          ? 'bg-[#071d19] text-[#d9ff4f] border-[#071d19] shadow-xs'
                          : 'bg-[#fbfcf7] text-[#556961] border-[rgba(7,29,25,0.12)] hover:border-[#071d19]'
                      }`}
                    >
                      <span>💬 WhatsApp</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => setPreferredReply('email')}
                      className={`text-[11px] py-1 px-2 rounded-xl font-bold transition flex items-center justify-center gap-1 cursor-pointer border ${
                        preferredReply === 'email'
                          ? 'bg-[#071d19] text-[#d9ff4f] border-[#071d19] shadow-xs'
                          : 'bg-[#fbfcf7] text-[#556961] border-[rgba(7,29,25,0.12)] hover:border-[#071d19]'
                      }`}
                    >
                      <span>✉️ E-mail</span>
                    </button>
                  </div>
                </div>
              </div>

              {/* Linha 2: Motivo do Contato em pílulas compactas */}
              <div>
                <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1">
                  Motivo do Contato
                </label>
                <div className="flex flex-wrap items-center gap-1.5">
                  {[
                    { id: 'duvida', label: 'Dúvida' },
                    { id: 'sugestao', label: 'Sugestão' },
                    { id: 'problema', label: 'Problema' },
                    { id: 'elogio', label: 'Elogio' },
                    { id: 'outro', label: 'Outro' }
                  ].map((cat) => (
                    <button
                      type="button"
                      key={cat.id}
                      onClick={() => setCategory(cat.id as any)}
                      className={`text-[11px] py-1 px-3 rounded-lg font-bold transition text-center cursor-pointer border ${
                        category === cat.id
                          ? 'bg-[#071d19] text-[#d9ff4f] border-[#071d19] shadow-xs'
                          : 'bg-[#fbfcf7] text-[#556961] border-[rgba(7,29,25,0.12)] hover:border-[#071d19] hover:text-[#071d19]'
                      }`}
                    >
                      {cat.label}
                    </button>
                  ))}
                </div>
              </div>

              {/* Linha 3: Assunto */}
              <div>
                <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1">
                  Assunto
                </label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="Ex: Sugestão para o leitor de chamadas da 99"
                  className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3 py-1.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] transition"
                />
              </div>

              {/* Linha 4: Mensagem */}
              <div>
                <label className="block text-[10px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1 flex items-center justify-between">
                  <span>Mensagem *</span>
                  <span className="text-[9px] text-[#7a8982] font-normal">
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
                  className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl p-2.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] resize-none transition"
                />
              </div>

              {/* Linha 5: Botões de Ação */}
              <div className="pt-1 flex items-center justify-end gap-2.5">
                <button
                  type="button"
                  onClick={handleReset}
                  className="px-3.5 py-1.5 rounded-lg text-xs font-semibold text-[#7a8982] hover:text-[#071d19] hover:bg-[#f3f5ed] transition cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || !message.trim()}
                  className="bg-[#071d19] hover:bg-[#0d302a] disabled:opacity-50 text-[#d9ff4f] font-space font-bold text-xs px-5 py-2 rounded-xl shadow-xs transition flex items-center gap-1.5 cursor-pointer"
                >
                  <Send className="w-3 h-3" />
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

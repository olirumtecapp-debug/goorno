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
        className="bg-white text-[#071d19] border border-[rgba(7,29,25,0.12)] rounded-[28px] max-w-lg w-full overflow-hidden shadow-[0_24px_60px_rgba(7,29,25,0.18)] relative font-sans"
        onClick={(e) => e.stopPropagation()}
      >
        {/* HEADER LIMPO E ELEGANTE */}
        <div className="p-6 bg-[#fbfcf7] border-b border-[rgba(7,29,25,0.08)] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-2xl bg-[#f3f5ed] border border-[rgba(7,29,25,0.08)] text-[#071d19] grid place-items-center">
              <Mail className="w-5 h-5 text-[#071d19]" />
            </div>
            <div>
              <h3 className="font-space font-bold text-lg text-[#071d19] tracking-tight flex items-center gap-2">
                Fale Conosco
                <span className="text-[10px] font-black uppercase px-2.5 py-0.5 rounded-full bg-[#d9ff4f] text-[#071d19] border border-[rgba(7,29,25,0.08)]">
                  Caixa Postal
                </span>
              </h3>
              <p className="text-xs text-[#63756b]">
                Canal oficial direto com nossa equipe de suporte e produto
              </p>
            </div>
          </div>

          <button
            onClick={handleReset}
            className="w-9 h-9 rounded-full bg-[#f3f5ed] hover:bg-[#e6ebdf] text-[#071d19] grid place-items-center transition cursor-pointer border border-[rgba(7,29,25,0.08)]"
            aria-label="Fechar"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* CORPO DO FORMULÁRIO */}
        <div className="p-6 sm:p-7 bg-white">
          {successTicket ? (
            <div className="text-center py-6">
              <div className="w-16 h-16 rounded-full bg-[#dff4af] text-[#557000] grid place-items-center mx-auto mb-4 border border-[#c4e680]">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <h4 className="font-space text-xl font-bold text-[#071d19] mb-2">
                Mensagem Entregue com Sucesso!
              </h4>
              <p className="text-xs text-[#63756b] max-w-xs mx-auto leading-relaxed mb-4">
                Sua solicitação foi registrada no nosso sistema com o protocolo{' '}
                <span className="font-mono font-bold text-[#071d19] bg-[#f3f5ed] px-2 py-0.5 rounded border border-[rgba(7,29,25,0.1)]">
                  #{successTicket}
                </span>
                . Nossa equipe analisa todas as mensagens recebidas.
              </p>
              <button
                onClick={handleReset}
                className="bg-[#071d19] hover:bg-[#0d302a] text-[#d9ff4f] font-space font-bold text-xs px-6 py-2.5 rounded-xl transition cursor-pointer shadow-xs"
              >
                Fechar
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {errorMessage && (
                <div className="p-3 bg-rose-50 border border-rose-200 rounded-xl text-rose-700 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-600 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                    <User className="w-3.5 h-3.5 text-[#071d19]" />
                    Seu Nome (Opcional)
                  </label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Ex: Carlos Motorista"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] focus:ring-1 focus:ring-[#071d19] transition"
                  />
                </div>

                <div>
                  <label className="block text-[11px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1.5 flex items-center gap-1.5">
                    <Phone className="w-3.5 h-3.5 text-[#071d19]" />
                    WhatsApp / Telefone (Opcional)
                  </label>
                  <input
                    type="text"
                    value={contact}
                    onChange={(e) => setContact(e.target.value)}
                    placeholder="DDD + Número (ex: 11999998888)"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] focus:ring-1 focus:ring-[#071d19] transition"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1.5">
                  Motivo do Contato
                </label>
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
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
                      className={`text-xs py-2 px-3 rounded-xl font-bold transition text-center cursor-pointer border ${
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

              <div>
                <label className="block text-[11px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1.5">
                  Assunto
                </label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="Ex: Sugestão para o leitor de chamadas da 99"
                  className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] focus:ring-1 focus:ring-[#071d19] transition"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-[#4e5d56] uppercase tracking-wider mb-1.5 flex items-center justify-between">
                  <span>Mensagem *</span>
                  <span className="text-[10px] text-[#7a8982] font-normal">
                    {message.length}/1000
                  </span>
                </label>
                <textarea
                  rows={4}
                  maxLength={1000}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Conte com detalhes o que você precisa ou deixe sua sugestão para nossa equipe..."
                  required
                  className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl p-3.5 text-xs text-[#071d19] placeholder-[#9ca8a1] focus:outline-none focus:border-[#071d19] focus:ring-1 focus:ring-[#071d19] resize-none transition"
                />
              </div>

              <div className="pt-2 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={handleReset}
                  className="px-4 py-2.5 rounded-xl text-xs font-semibold text-[#7a8982] hover:text-[#071d19] hover:bg-[#f3f5ed] transition cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || !message.trim()}
                  className="bg-[#071d19] hover:bg-[#0d302a] disabled:opacity-50 text-[#d9ff4f] font-space font-bold text-xs px-6 py-2.5 rounded-xl shadow-xs transition flex items-center gap-2 cursor-pointer"
                >
                  <Send className="w-3.5 h-3.5" />
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

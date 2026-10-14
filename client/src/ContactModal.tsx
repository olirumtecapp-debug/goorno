import React, { useState } from 'react';
import {
  Mail,
  Send,
  X,
  CheckCircle2,
  AlertTriangle,
  MessageSquare,
  HelpCircle,
  Sparkles,
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
      className="fixed inset-0 z-50 bg-black/80 backdrop-blur-sm flex items-center justify-center p-4 sm:p-6"
      onClick={handleReset}
    >
      <div
        className="bg-[#173a34] text-white border border-[#234e45] rounded-3xl max-w-lg w-full overflow-hidden shadow-2xl relative"
        onClick={(e) => e.stopPropagation()}
      >
        {/* HEADER */}
        <div className="p-6 bg-[#122e29] border-b border-[#234e45] flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-10 h-10 rounded-xl bg-[#d9f56a]/15 text-[#d9f56a] grid place-items-center">
              <Mail className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-extrabold text-lg text-white tracking-tight flex items-center gap-2">
                Fale Conosco
                <span className="text-[10px] font-black uppercase px-2 py-0.5 rounded-full bg-[#d9f56a] text-[#173a34]">
                  Caixa Postal
                </span>
              </h3>
              <p className="text-xs text-[#dce5dc]/70">
                Canal oficial direto com nossa equipe de desenvolvimento
              </p>
            </div>
          </div>

          <button
            onClick={handleReset}
            className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 text-white grid place-items-center transition cursor-pointer"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* BODY */}
        <div className="p-6 sm:p-7">
          {successTicket ? (
            <div className="text-center py-6">
              <div className="w-16 h-16 rounded-full bg-emerald-500/20 text-emerald-400 grid place-items-center mx-auto mb-4 border border-emerald-500/30">
                <CheckCircle2 className="w-8 h-8" />
              </div>
              <h4 className="text-xl font-black text-white mb-2">
                Mensagem Entregue na Caixa Postal!
              </h4>
              <p className="text-xs text-[#dce5dc]/80 max-w-xs mx-auto leading-relaxed mb-4">
                Sua solicitação foi registrada no nosso sistema com o protocolo{' '}
                <span className="font-mono font-bold text-[#d9f56a]">
                  #{successTicket}
                </span>
                . Nossa equipe analisa todas as mensagens recebidas.
              </p>
              <button
                onClick={handleReset}
                className="bg-[#d9f56a] hover:bg-[#cbf052] text-[#173a34] font-bold text-xs px-6 py-2.5 rounded-xl transition cursor-pointer"
              >
                Fechar
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              {errorMessage && (
                <div className="p-3 bg-rose-950/60 border border-rose-500/40 rounded-xl text-rose-200 text-xs flex items-center gap-2">
                  <AlertTriangle className="w-4 h-4 text-rose-400 shrink-0" />
                  <span>{errorMessage}</span>
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-[11px] font-bold text-[#dce5dc]/80 uppercase mb-1 flex items-center gap-1">
                    <User className="w-3 h-3 text-[#d9f56a]" />
                    Seu Nome (Opcional)
                  </label>
                  <input
                    type="text"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Ex: Carlos Motorista"
                    className="w-full bg-[#122e29] border border-[#234e45] rounded-xl px-3.5 py-2 text-xs text-white placeholder-[#71867f] focus:outline-none focus:border-[#d9f56a]"
                  />
                </div>

                <div>
                  <label className="block text-[11px] font-bold text-[#dce5dc]/80 uppercase mb-1 flex items-center gap-1">
                    <Phone className="w-3 h-3 text-[#d9f56a]" />
                    WhatsApp / Telefone (Opcional)
                  </label>
                  <input
                    type="text"
                    value={contact}
                    onChange={(e) => setContact(e.target.value)}
                    placeholder="DDD + Número (ex: 11999998888)"
                    className="w-full bg-[#122e29] border border-[#234e45] rounded-xl px-3.5 py-2 text-xs text-white placeholder-[#71867f] focus:outline-none focus:border-[#d9f56a]"
                  />
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-[#dce5dc]/80 uppercase mb-1">
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
                      className={`text-xs py-1.5 px-3 rounded-lg font-bold transition text-center cursor-pointer border ${
                        category === cat.id
                          ? 'bg-[#d9f56a] text-[#173a34] border-[#d9f56a]'
                          : 'bg-[#122e29] text-[#dce5dc]/70 border-[#234e45] hover:text-white'
                      }`}
                    >
                      {cat.label}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <label className="block text-[11px] font-bold text-[#dce5dc]/80 uppercase mb-1">
                  Assunto
                </label>
                <input
                  type="text"
                  value={subject}
                  onChange={(e) => setSubject(e.target.value)}
                  placeholder="Ex: Sugestão para o leitor de chamadas da 99"
                  className="w-full bg-[#122e29] border border-[#234e45] rounded-xl px-3.5 py-2 text-xs text-white placeholder-[#71867f] focus:outline-none focus:border-[#d9f56a]"
                />
              </div>

              <div>
                <label className="block text-[11px] font-bold text-[#dce5dc]/80 uppercase mb-1 flex items-center justify-between">
                  <span>Mensagem *</span>
                  <span className="text-[10px] text-[#71867f] font-normal">
                    {message.length}/1000
                  </span>
                </label>
                <textarea
                  rows={4}
                  maxLength={1000}
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Conte com detalhes o que aconteceu ou deixe sua sugestão para nossa equipe..."
                  required
                  className="w-full bg-[#122e29] border border-[#234e45] rounded-xl p-3 text-xs text-white placeholder-[#71867f] focus:outline-none focus:border-[#d9f56a] resize-none"
                />
              </div>

              <div className="pt-2 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={handleReset}
                  className="px-4 py-2.5 rounded-xl text-xs font-semibold text-[#dce5dc]/70 hover:text-white transition cursor-pointer"
                >
                  Cancelar
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || !message.trim()}
                  className="bg-[#d9f56a] hover:bg-[#cbf052] disabled:opacity-50 text-[#173a34] font-black text-xs px-5 py-2.5 rounded-xl shadow-sm transition flex items-center gap-2 cursor-pointer"
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

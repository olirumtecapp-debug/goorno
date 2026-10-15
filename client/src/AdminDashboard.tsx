import React, { useState, useEffect } from 'react';
import {
  Users,
  ShieldAlert,
  Calendar,
  CheckCircle,
  XCircle,
  RefreshCw,
  Search,
  Lock,
  DollarSign,
  AlertTriangle,
  Smartphone,
  PlusCircle,
  Ban,
  Award,
  LogOut,
  ArrowRight,
  Clock,
  Sparkles,
  Ticket,
  Plus,
  Copy,
  Check,
  Trash2,
  ToggleLeft,
  ToggleRight,
  ArrowUpRight,
  ShieldCheck,
  TrendingUp,
  Gift,
  Share2,
  ExternalLink,
  Mail,
  MessageSquare,
  Send
} from 'lucide-react';

interface LicenseRecord {
  id: string;
  whatsapp: string;
  deviceId: string;
  deviceModel?: string;
  status: 'trial' | 'active' | 'expired' | 'blocked';
  plano: 'trial' | 'mensal' | 'vitalicio' | 'cortesia';
  trialEndsAt: string;
  validoAte: string;
  createdAt: string;
  lastSeenAt: string;
  deviceTransfersCount: number;
  transfers: Array<{
    date: string;
    fromDevice: string;
    toDevice: string;
    ip?: string;
  }>;
  fraudSuspicion: boolean;
  fraudReason?: string;
  notes?: string;
}

interface VoucherRecord {
  id: string;
  code: string;
  description: string;
  days: number;
  isLifetime: boolean;
  maxUses: number;
  usedCount: number;
  active: boolean;
  createdAt: string;
}

interface ContactMessage {
  id: string;
  name: string;
  whatsapp: string;
  category: 'duvida' | 'sugestao' | 'problema' | 'financeiro' | 'outro';
  message: string;
  createdAt: string;
  status: 'unread' | 'read' | 'replied';
  replyNotes?: string;
}

interface AdminStats {
  totalUsers: number;
  activePaid: number;
  inTrial: number;
  expired: number;
  blocked: number;
  fraudAlerts: number;
  estimatedMonthlyRevenue: number;
}

export const AdminDashboard: React.FC<{ onBackToApp?: () => void }> = ({ onBackToApp }) => {
  const [adminPassword, setAdminPassword] = useState<string>(() => {
    return sessionStorage.getItem('goorno_admin_pwd') || '';
  });
  const [inputPassword, setInputPassword] = useState('');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [authError, setAuthError] = useState('');

  const [loading, setLoading] = useState(false);
  const [stats, setStats] = useState<AdminStats | null>(null);
  const [licenses, setLicenses] = useState<LicenseRecord[]>([]);
  const [vouchers, setVouchers] = useState<VoucherRecord[]>([]);
  const [messages, setMessages] = useState<ContactMessage[]>([]);
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'trial' | 'expired' | 'fraud'>('all');
  const [activeTab, setActiveTab] = useState<'overview' | 'drivers' | 'vouchers' | 'mailbox'>('overview');
  const [messageFilter, setMessageFilter] = useState<'all' | 'unread' | 'read' | 'replied'>('all');
  const [selectedUser, setSelectedUser] = useState<LicenseRecord | null>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);
  const [copiedVoucher, setCopiedVoucher] = useState<string | null>(null);

  // Formulário de Novo Voucher
  const [newVoucherCode, setNewVoucherCode] = useState('');
  const [newVoucherDesc, setNewVoucherDesc] = useState('');
  const [newVoucherDays, setNewVoucherDays] = useState(7);
  const [newVoucherLifetime, setNewVoucherLifetime] = useState(false);
  const [newVoucherMaxUses, setNewVoucherMaxUses] = useState(0);
  const [voucherSubmitting, setVoucherSubmitting] = useState(false);
  const [copiedShareLink, setCopiedShareLink] = useState(false);
  const [copiedShareMsg, setCopiedShareMsg] = useState(false);

  // Auto-login se já autenticado nesta sessão
  useEffect(() => {
    if (adminPassword) {
      loadData(adminPassword);
    }
  }, [adminPassword]);

  const handleLogin = (e: React.FormEvent) => {
    e.preventDefault();
    setAuthError('');
    loadData(inputPassword);
  };

  const handleLogout = () => {
    sessionStorage.removeItem('goorno_admin_pwd');
    setAdminPassword('');
    setIsAuthenticated(false);
    setInputPassword('');
  };

  const loadData = async (password: string) => {
    setLoading(true);
    setAuthError('');
    try {
      const [resStats, resVouchers, resContact] = await Promise.all([
        fetch(`/api/admin/stats-and-licenses?adminPassword=${encodeURIComponent(password)}`),
        fetch(`/api/admin/vouchers?adminPassword=${encodeURIComponent(password)}`),
        fetch(`/api/contact?adminPassword=${encodeURIComponent(password)}`)
      ]);

      if (resStats.status === 401) {
        setAuthError('Senha de administrador incorreta.');
        setIsAuthenticated(false);
        sessionStorage.removeItem('goorno_admin_pwd');
      } else if (resStats.ok) {
        const data = await resStats.json();
        setStats(data.stats);
        setLicenses(data.licenses || []);
        setIsAuthenticated(true);
        setAdminPassword(password);
        sessionStorage.setItem('goorno_admin_pwd', password);

        if (resVouchers.ok) {
          const vData = await resVouchers.json();
          setVouchers(vData.vouchers || []);
        }

        if (resContact.ok) {
          const cData = await resContact.json();
          setMessages(cData.messages || []);
        }
      } else {
        setAuthError('Erro ao carregar dados do servidor.');
      }
    } catch {
      setAuthError('Falha na conexão com o servidor.');
    } finally {
      setLoading(false);
    }
  };

  const handleUpdateMessageStatus = async (id: string, status: 'unread' | 'read' | 'replied') => {
    try {
      const res = await fetch(`/api/contact/${id}`, {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ adminPassword, status })
      });
      if (res.ok) {
        setMessages((prev) => prev.map((m) => (m.id === id ? { ...m, status } : m)));
        setActionFeedback('Status da mensagem atualizado!');
        setTimeout(() => setActionFeedback(null), 3000);
      }
    } catch (e: any) {
      setActionFeedback(`Erro ao atualizar mensagem: ${e.message}`);
    }
  };

  const handleDeleteMessage = async (id: string) => {
    if (!window.confirm('Deseja realmente remover esta mensagem da Caixa Postal?')) return;
    try {
      const res = await fetch(`/api/contact/${id}?adminPassword=${encodeURIComponent(adminPassword)}`, {
        method: 'DELETE'
      });
      if (res.ok) {
        setMessages((prev) => prev.filter((m) => m.id !== id));
        setActionFeedback('Mensagem removida com sucesso!');
        setTimeout(() => setActionFeedback(null), 3000);
      }
    } catch (e: any) {
      setActionFeedback(`Erro ao remover mensagem: ${e.message}`);
    }
  };

  const executeAction = async (whatsapp: string, action: string, days?: number, note?: string) => {
    setLoading(true);
    setActionFeedback(null);
    try {
      const res = await fetch('/api/admin/manage-license', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminPassword,
          whatsapp,
          action,
          days,
          note
        })
      });

      const data = await res.json();
      if (res.ok && data.ok) {
        setActionFeedback(`Ação executada com sucesso para ${whatsapp}!`);
        await loadData(adminPassword);
        if (selectedUser && selectedUser.whatsapp === whatsapp) {
          setSelectedUser(data.license);
        }
      } else {
        setActionFeedback(`Erro: ${data.error || 'Falha ao executar ação.'}`);
      }
    } catch (err: any) {
      setActionFeedback(`Erro de rede: ${err?.message}`);
    } finally {
      setLoading(false);
      setTimeout(() => setActionFeedback(null), 5000);
    }
  };

  const handleCreateVoucher = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newVoucherCode.trim()) return;

    setVoucherSubmitting(true);
    try {
      const res = await fetch('/api/admin/create-voucher', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminPassword,
          code: newVoucherCode.trim().toUpperCase(),
          description: newVoucherDesc.trim() || 'Voucher Promocional GoOrNo',
          days: newVoucherLifetime ? 999 : Number(newVoucherDays),
          isLifetime: newVoucherLifetime,
          maxUses: Number(newVoucherMaxUses) || 0
        })
      });

      const data = await res.json();
      if (res.ok && data.ok) {
        setActionFeedback(`🎉 Voucher "${data.voucher.code}" criado com sucesso!`);
        setNewVoucherCode('');
        setNewVoucherDesc('');
        setNewVoucherDays(7);
        setNewVoucherLifetime(false);
        setNewVoucherMaxUses(0);
        await loadData(adminPassword);
      } else {
        setActionFeedback(`Erro: ${data.error || 'Falha ao criar voucher.'}`);
      }
    } catch (err: any) {
      setActionFeedback(`Erro de rede: ${err?.message}`);
    } finally {
      setVoucherSubmitting(false);
      setTimeout(() => setActionFeedback(null), 5000);
    }
  };

  const handleToggleVoucher = async (code: string, currentActive: boolean) => {
    try {
      const res = await fetch('/api/admin/toggle-voucher', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminPassword,
          code,
          active: !currentActive
        })
      });
      if (res.ok) {
        await loadData(adminPassword);
        setActionFeedback(`Status do voucher "${code}" atualizado!`);
      }
    } catch {}
  };

  const handleDeleteVoucher = async (code: string) => {
    if (!confirm(`Deseja realmente excluir o voucher "${code}"?`)) return;
    try {
      const res = await fetch('/api/admin/delete-voucher', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          adminPassword,
          code
        })
      });
      if (res.ok) {
        await loadData(adminPassword);
        setActionFeedback(`Voucher "${code}" excluído!`);
      }
    } catch {}
  };

  const handleCopyVoucher = (code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedVoucher(code);
    setTimeout(() => setCopiedVoucher(null), 2500);
  };

  // TELA DE LOGIN CLEAN NO ESTILO REDESIGN (SPACE GROTESK + DM SANS + INK & LIME)
  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-[#f3f5ed] text-[#071d19] flex items-center justify-center p-4 sm:p-6 font-dm selection:bg-[#d9ff4f] selection:text-[#071d19]">
        <div className="w-full max-w-md bg-white border border-[rgba(7,29,25,0.12)] rounded-[28px] p-8 sm:p-10 shadow-[0_20px_50px_rgba(7,29,25,0.06)]">
          <div className="text-center mb-7">
            <div className="mx-auto mb-3.5 grid h-14 w-14 place-items-center rounded-2xl bg-[#071d19] text-[#d9ff4f] shadow-xs">
              <Lock className="h-6 w-6" />
            </div>
            <h1 className="font-space text-2xl font-bold text-[#071d19] tracking-tight">Painel Administrativo</h1>
            <p className="mt-1.5 text-xs text-[#7a8982]">
              Controle de motoristas, cupons de cortesia e combate à fraude
            </p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-[11px] font-space font-bold text-[#071d19] uppercase tracking-wider mb-2">
                Senha Master
              </label>
              <input
                type="password"
                value={inputPassword}
                onChange={(e) => setInputPassword(e.target.value)}
                placeholder="Digite a senha administrativa..."
                className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] focus:border-[#071d19] rounded-xl px-4 py-3 text-sm text-[#071d19] placeholder-[#7a8982]/60 focus:outline-none transition"
                required
              />
            </div>

            {authError && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-red-700 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0 text-red-600" />
                <span>{authError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-[#071d19] hover:bg-[#0d302a] text-[#d9ff4f] font-space font-bold py-3.5 px-4 rounded-xl shadow-xs transition hover:-translate-y-0.5 flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
            >
              {loading ? <RefreshCw className="w-5 h-5 animate-spin" /> : 'Acessar Painel Master'}
            </button>
          </form>
        </div>
      </div>
    );
  }

  // Filtragem de motoristas
  const filteredLicenses = licenses.filter((lic) => {
    const matchSearch =
      lic.whatsapp.toLowerCase().includes(searchTerm.toLowerCase()) ||
      lic.id.includes(searchTerm) ||
      (lic.deviceModel || '').toLowerCase().includes(searchTerm.toLowerCase());

    if (!matchSearch) return false;

    if (statusFilter === 'active') return lic.status === 'active';
    if (statusFilter === 'trial') return lic.plano === 'trial' && lic.status !== 'expired';
    if (statusFilter === 'expired') return lic.status === 'expired';
    if (statusFilter === 'fraud') return lic.fraudSuspicion;

    return true;
  });

  return (
    <div className="min-h-screen bg-[#f3f5ed] text-[#071d19] flex flex-col font-dm selection:bg-[#d9ff4f] selection:text-[#071d19]">
      {/* HEADER FIXO SUPERIOR NO ESTILO REDESIGN GOORNO */}
      <header className="border-b border-white/10 bg-[#071d19] sticky top-0 z-40 text-white shadow-xs">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-[#d9ff4f] flex items-center justify-center font-space font-black text-[#071d19] text-sm shadow-xs">
              GO
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-space font-bold text-base text-white tracking-tight">GoOrNo Master</span>
                <span className="rounded-full bg-[#d9ff4f] px-2.5 py-0.5 text-[10px] font-space font-bold uppercase tracking-wider text-[#071d19]">
                  Admin Master
                </span>
              </div>
              <p className="text-[11px] text-[#c2d0ca]">Painel de Gestão e Cupons de Cortesia</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => loadData(adminPassword)}
              disabled={loading}
              className="p-2 border border-white/15 bg-white/10 hover:bg-white/20 text-white rounded-xl transition shadow-xs cursor-pointer"
              title="Atualizar Dados"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            </button>

            {onBackToApp && (
              <button
                onClick={onBackToApp}
                className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-white/15 bg-white/10 hover:bg-white/20 text-xs font-space font-bold text-white transition shadow-xs cursor-pointer"
              >
                <span>Ver App</span>
                <ArrowUpRight className="h-3 w-3 text-[#d9ff4f]" />
              </button>
            )}

            <button
              onClick={handleLogout}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-red-500/30 bg-red-500/20 hover:bg-red-500/30 text-xs font-space font-bold text-red-200 transition cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Sair</span>
            </button>
          </div>
        </div>
      </header>

      {/* ÁREA PRINCIPAL */}
      <main className="flex-1 mx-auto max-w-7xl w-full px-4 py-6 sm:px-6 space-y-6">
        {/* FEEDBACK DE AÇÃO */}
        {actionFeedback && (
          <div className="p-4 bg-emerald-50 border border-emerald-300 rounded-2xl text-emerald-900 text-sm flex items-center justify-between shadow-xs animate-fade-in">
            <div className="flex items-center gap-2.5 font-medium">
              <CheckCircle className="w-5 h-5 shrink-0 text-emerald-600" />
              <span>{actionFeedback}</span>
            </div>
            <button onClick={() => setActionFeedback(null)} className="text-emerald-700 hover:text-emerald-900 font-bold ml-2 cursor-pointer">✕</button>
          </div>
        )}

        {/* NAVEGAÇÃO ENTRE ABAS NO ESTILO REDESIGN */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[rgba(7,29,25,0.12)] pb-4">
          <div className="flex items-center gap-1.5 p-1.5 bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl shadow-xs overflow-x-auto">
            <button
              onClick={() => setActiveTab('overview')}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs sm:text-sm font-space font-bold transition cursor-pointer border-0 ${
                activeTab === 'overview'
                  ? 'bg-[#071d19] text-[#d9ff4f] shadow-xs'
                  : 'bg-transparent text-[#7a8982] hover:text-[#071d19] hover:bg-[#f3f5ed]'
              }`}
            >
              <TrendingUp className="w-4 h-4" />
              <span>Visão Geral & Faturamento</span>
            </button>

            <button
              onClick={() => setActiveTab('drivers')}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs sm:text-sm font-space font-bold transition cursor-pointer border-0 ${
                activeTab === 'drivers'
                  ? 'bg-[#071d19] text-[#d9ff4f] shadow-xs'
                  : 'bg-transparent text-[#7a8982] hover:text-[#071d19] hover:bg-[#f3f5ed]'
              }`}
            >
              <Users className="w-4 h-4" />
              <span>Motoristas & Licenças ({licenses.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('vouchers')}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs sm:text-sm font-space font-bold transition cursor-pointer border-0 ${
                activeTab === 'vouchers'
                  ? 'bg-[#071d19] text-[#d9ff4f] shadow-xs'
                  : 'bg-transparent text-[#7a8982] hover:text-[#071d19] hover:bg-[#f3f5ed]'
              }`}
            >
              <Ticket className="w-4 h-4" />
              <span>Cupons & Vouchers ({vouchers.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('mailbox')}
              className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs sm:text-sm font-space font-bold transition cursor-pointer border-0 relative ${
                activeTab === 'mailbox'
                  ? 'bg-[#071d19] text-[#d9ff4f] shadow-xs'
                  : 'bg-transparent text-[#7a8982] hover:text-[#071d19] hover:bg-[#f3f5ed]'
              }`}
            >
              <Mail className="w-4 h-4" />
              <span>Caixa Postal ({messages.length})</span>
              {messages.filter((m) => m.status === 'unread').length > 0 && (
                <span className="bg-[#d9ff4f] text-[#071d19] text-[10px] font-space font-black px-1.5 py-0.5 rounded-full ml-1">
                  {messages.filter((m) => m.status === 'unread').length}
                </span>
              )}
            </button>
          </div>

          <div className="text-xs text-[#7a8982] font-dm">
            Status: <span className="font-space font-bold text-[#6b8900]">● Online & Sincronizado</span>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* ABA 1: VISÃO GERAL & MÉTRICAS                                              */}
        {/* ========================================================================= */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* GRID DE CARDS NO ESTILO REDESIGN */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
              {/* Card 1: MRR */}
              <div className="col-span-2 sm:col-span-1 bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-4 sm:p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#6b8900] mb-1">
                  <span className="text-[11px] font-space font-bold uppercase tracking-wider text-[#7a8982]">MRR Estimado</span>
                  <DollarSign className="w-4 h-4" />
                </div>
                <div className="text-2xl font-space font-bold text-[#071d19]">
                  R$ {stats?.estimatedMonthlyRevenue.toFixed(2).replace('.', ',') || '0,00'}
                </div>
                <p className="text-[11px] text-[#7a8982] mt-1">Faturamento recorrente</p>
              </div>

              {/* Card 2: Total Motoristas */}
              <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-4 sm:p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#071d19] mb-1">
                  <span className="text-[11px] font-space font-bold uppercase tracking-wider text-[#7a8982]">Motoristas</span>
                  <Users className="w-4 h-4 text-[#7a8982]" />
                </div>
                <div className="text-2xl font-space font-bold text-[#071d19]">{stats?.totalUsers || 0}</div>
                <p className="text-[11px] text-[#7a8982] mt-1">Total cadastrados</p>
              </div>

              {/* Card 3: Pagantes Ativos */}
              <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-4 sm:p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#6b8900] mb-1">
                  <span className="text-[11px] font-space font-bold uppercase tracking-wider text-[#7a8982]">Pagantes</span>
                  <CheckCircle className="w-4 h-4" />
                </div>
                <div className="text-2xl font-space font-bold text-[#6b8900]">{stats?.activePaid || 0}</div>
                <p className="text-[11px] text-[#7a8982] mt-1">Mensal ou vitalício</p>
              </div>

              {/* Card 4: Em Teste */}
              <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-4 sm:p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#0284c7] mb-1">
                  <span className="text-[11px] font-space font-bold uppercase tracking-wider text-[#7a8982]">Em Teste</span>
                  <Clock className="w-4 h-4" />
                </div>
                <div className="text-2xl font-space font-bold text-[#0284c7]">{stats?.inTrial || 0}</div>
                <p className="text-[11px] text-[#7a8982] mt-1">Período de 7 dias grátis</p>
              </div>

              {/* Card 5: Vencidas */}
              <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-4 sm:p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#b45309] mb-1">
                  <span className="text-[11px] font-space font-bold uppercase tracking-wider text-[#7a8982]">Vencidas</span>
                  <Calendar className="w-4 h-4" />
                </div>
                <div className="text-2xl font-space font-bold text-[#b45309]">{stats?.expired || 0}</div>
                <p className="text-[11px] text-[#7a8982] mt-1">Aguardando renovação</p>
              </div>

              {/* Card 6: Alertas Fraude */}
              <div className="bg-white border border-red-200 rounded-2xl p-4 sm:p-5 shadow-xs">
                <div className="flex items-center justify-between text-[#ff6e58] mb-1">
                  <span className="text-[11px] font-space font-bold uppercase tracking-wider text-[#7a8982]">Fraude</span>
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div className="text-2xl font-space font-bold text-[#ff6e58]">{stats?.fraudAlerts || 0}</div>
                <p className="text-[11px] text-[#7a8982] mt-1">Suspeitas detectadas</p>
              </div>
            </div>

            {/* SEÇÃO: COMPARTILHAR LINK DE DOWNLOAD & MENSAGEM DO WHATSAPP */}
            <div className="bg-white border-2 border-[#071d19] rounded-2xl p-5 sm:p-6 shadow-sm space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-xl bg-[#071d19] text-[#d9ff4f] grid place-items-center shrink-0">
                    <Share2 className="w-5 h-5" />
                  </div>
                  <div>
                    <h3 className="font-space font-bold text-base text-[#071d19] flex items-center gap-2">
                      Link Oficial de Download & Convite de Motoristas
                      <span className="text-[10px] bg-[#d9ff4f] text-[#071d19] font-space font-bold px-2.5 py-0.5 rounded-full">
                        Compartilhar
                      </span>
                    </h3>
                    <p className="text-xs text-[#7a8982]">
                      Envie este link para motoristas em grupos do WhatsApp para eles baixarem o APK com total tranquilidade
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0">
                  <button
                    onClick={() => {
                      navigator.clipboard.writeText(window.location.origin);
                      setCopiedShareLink(true);
                      setTimeout(() => setCopiedShareLink(false), 2500);
                    }}
                    className="inline-flex items-center gap-1.5 text-xs font-space font-bold bg-[#071d19] hover:bg-[#0d302a] text-[#d9ff4f] px-3.5 py-2.5 rounded-xl transition cursor-pointer shadow-xs border-0"
                  >
                    {copiedShareLink ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                    <span>{copiedShareLink ? 'Link Copiado!' : 'Copiar Link da Página'}</span>
                  </button>

                  <a
                    href={window.location.origin}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1 text-xs font-semibold text-[#7a8982] hover:text-[#071d19] px-2.5 py-2 rounded-xl border border-[rgba(7,29,25,0.12)] hover:bg-[#fbfcf7] transition"
                  >
                    <ExternalLink className="w-3.5 h-3.5" />
                    <span>Ver Página</span>
                  </a>
                </div>
              </div>

              {/* MENSAGEM PRONTA FORMATADA PARA WHATSAPP */}
              <div className="bg-[#fbfcf7] border border-[rgba(7,29,25,0.12)] rounded-xl p-4 space-y-3">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-space font-bold text-[#071d19] uppercase tracking-wider">
                    💬 Mensagem Pronta de Apresentação (Explicando o APK e Segurança):
                  </span>
                  <button
                    onClick={() => {
                      const msg = `🚗 *GoOrNo — O Copiloto dos Motoristas de Aplicativo (Uber e 99)*\n\nFala parceiro! Baixe o aplicativo oficial do GoOrNo e descubra na hora se a corrida compensa antes de aceitar:\n\n✅ Cálculo automático de R$/km e R$/hora na tela\n✅ Veredito em cores: 🟢 Aceitar, 🟡 Atenção, 🔴 Recusar\n✅ Sem propagandas e sem travamentos\n✅ 7 DIAS GRÁTIS de teste na rua para você testar!\n\n📥 *Link para Baixar o APK Oficial:*\n${window.location.origin}\n\n🛡️ *Aviso de Segurança:* Como o GoOrNo precisa ler a tela por cima da Uber/99, ele é distribuído em APK oficial direto aos motoristas (fora da Play Store). Se o seu celular avisar "arquivo pode ser nocivo", pode clicar em "Baixar mesmo assim" e "Instalar assim mesmo" sem medo — o app é 100% limpo, verificado e seguro!`;
                      navigator.clipboard.writeText(msg);
                      setCopiedShareMsg(true);
                      setTimeout(() => setCopiedShareMsg(false), 2500);
                    }}
                    className="inline-flex items-center gap-1.5 text-xs font-space font-bold bg-[#d9ff4f] hover:bg-[#bfe72d] text-[#071d19] px-3.5 py-1.5 rounded-lg transition cursor-pointer border-0 shadow-2xs"
                  >
                    {copiedShareMsg ? <Check className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
                    <span>{copiedShareMsg ? 'Mensagem Copiada!' : 'Copiar Texto para WhatsApp'}</span>
                  </button>
                </div>

                <div className="text-xs text-[#071d19] font-mono whitespace-pre-wrap bg-white p-3.5 rounded-lg border border-[rgba(7,29,25,0.12)] max-h-48 overflow-y-auto leading-relaxed">
{`🚗 *GoOrNo — O Copiloto dos Motoristas de Aplicativo (Uber e 99)*

Fala parceiro! Baixe o aplicativo oficial do GoOrNo e descubra na hora se a corrida compensa antes de aceitar:

✅ Cálculo automático de R$/km e R$/hora na tela
✅ Veredito em cores: 🟢 Aceitar, 🟡 Atenção, 🔴 Recusar
✅ Sem propagandas e sem travamentos
✅ 7 DIAS GRÁTIS de teste na rua para você testar!

📥 *Link para Baixar o APK Oficial:*
${window.location.origin}

🛡️ *Aviso de Segurança:* Como o GoOrNo precisa ler a tela por cima da Uber/99, ele é distribuído em APK oficial direto aos motoristas (fora da Play Store). Se o seu celular avisar "arquivo pode ser nocivo", pode clicar em "Baixar mesmo assim" e "Instalar assim mesmo" sem medo — o app é 100% limpo, verificado e seguro!`}
                </div>

                <div className="flex items-center justify-end">
                  <a
                    href={`https://api.whatsapp.com/send?text=${encodeURIComponent(
                      `🚗 *GoOrNo — O Copiloto dos Motoristas de Aplicativo (Uber e 99)*\n\nFala parceiro! Baixe o aplicativo oficial do GoOrNo e descubra na hora se a corrida compensa antes de aceitar:\n\n✅ Cálculo automático de R$/km e R$/hora na tela\n✅ Veredito em cores: 🟢 Aceitar, 🟡 Atenção, 🔴 Recusar\n✅ Sem propagandas e sem travamentos\n✅ 7 DIAS GRÁTIS de teste na rua para você testar!\n\n📥 *Link para Baixar o APK Oficial:*\n${window.location.origin}\n\n🛡️ *Aviso de Segurança:* Como o GoOrNo precisa ler a tela por cima da Uber/99, ele é distribuído em APK oficial direto aos motoristas (fora da Play Store). Se o seu celular avisar "arquivo pode ser nocivo", pode clicar em "Baixar mesmo assim" e "Instalar assim mesmo" sem medo — o app é 100% limpo, verificado e seguro!`
                    )}`}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-1.5 text-xs font-space font-bold text-white bg-[#25D366] hover:bg-[#20ba5a] px-3.5 py-1.5 rounded-lg transition shadow-2xs"
                  >
                    <span>Abrir no WhatsApp Web</span>
                    <ExternalLink className="w-3.5 h-3.5" />
                  </a>
                </div>
              </div>
            </div>

            {/* SEÇÃO DE DESTAQUE: REGRAS E STATUS RÁPIDO */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-5 shadow-xs space-y-3">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-5 h-5 text-[#6b8900]" />
                  <h3 className="font-space font-bold text-sm text-[#071d19]">Sistema Anti-Fraude e Proteção de Licença</h3>
                </div>
                <p className="text-xs text-[#7a8982] leading-relaxed">
                  Cada licença é vinculada exclusivamente ao ID do celular do motorista. O próprio motorista pode transferir para um novo aparelho até 2 vezes por mês. Tentativas adicionais disparam alerta automático para seu e-mail e são bloqueadas.
                </p>
              </div>

              <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-5 shadow-xs space-y-3">
                <div className="flex items-center gap-2">
                  <Gift className="w-5 h-5 text-[#8aa500]" />
                  <h3 className="font-space font-bold text-sm text-[#071d19]">Cupons de Cortesia e Campanhas</h3>
                </div>
                <p className="text-xs text-[#7a8982] leading-relaxed">
                  Crie vouchers promocionais personalizados (ex: 7 dias de bônus, 30 dias ou cortesia vitalícia) para atrair novos motoristas em grupos de WhatsApp ou fechar parcerias.
                </p>
                <button
                  onClick={() => setActiveTab('vouchers')}
                  className="inline-flex items-center gap-1.5 text-xs font-space font-bold text-[#071d19] bg-[#d9ff4f] hover:bg-[#bfe72d] px-3.5 py-2 rounded-xl transition cursor-pointer border-0 shadow-2xs"
                >
                  <Plus className="w-3.5 h-3.5" />
                  <span>Criar Novo Cupom Agora</span>
                </button>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* ABA 2: MOTORISTAS & LICENÇAS                                              */}
        {/* ========================================================================= */}
        {activeTab === 'drivers' && (
          <div className="space-y-4">
            {/* BARRA DE FILTROS E BUSCA */}
            <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-4 shadow-xs flex flex-col md:flex-row items-center justify-between gap-3">
              <div className="relative w-full md:w-80">
                <Search className="w-4 h-4 absolute left-3.5 top-3 text-[#7a8982]" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Buscar por WhatsApp ou ID..."
                  className="w-full pl-9 pr-4 py-2 bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl text-xs text-[#071d19] placeholder-[#7a8982]/70 focus:outline-none focus:border-[#071d19] transition"
                />
              </div>

              {/* Pílulas de filtro */}
              <div className="flex items-center gap-1.5 overflow-x-auto w-full md:w-auto">
                <button
                  onClick={() => setStatusFilter('all')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-space font-bold transition cursor-pointer border-0 ${
                    statusFilter === 'all'
                      ? 'bg-[#071d19] text-[#d9ff4f]'
                      : 'bg-[#fbfcf7] text-[#7a8982] hover:bg-[#f3f5ed]'
                  }`}
                >
                  Todos ({licenses.length})
                </button>
                <button
                  onClick={() => setStatusFilter('active')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-space font-bold transition cursor-pointer border-0 ${
                    statusFilter === 'active'
                      ? 'bg-[#6b8900] text-white'
                      : 'bg-[#fbfcf7] text-[#7a8982] hover:bg-[#f3f5ed]'
                  }`}
                >
                  Pagantes ({stats?.activePaid || 0})
                </button>
                <button
                  onClick={() => setStatusFilter('trial')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-space font-bold transition cursor-pointer border-0 ${
                    statusFilter === 'trial'
                      ? 'bg-[#0284c7] text-white'
                      : 'bg-[#fbfcf7] text-[#7a8982] hover:bg-[#f3f5ed]'
                  }`}
                >
                  Teste ({stats?.inTrial || 0})
                </button>
                <button
                  onClick={() => setStatusFilter('expired')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-space font-bold transition cursor-pointer border-0 ${
                    statusFilter === 'expired'
                      ? 'bg-[#b45309] text-white'
                      : 'bg-[#fbfcf7] text-[#7a8982] hover:bg-[#f3f5ed]'
                  }`}
                >
                  Vencidos ({stats?.expired || 0})
                </button>
                <button
                  onClick={() => setStatusFilter('fraud')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-space font-bold transition cursor-pointer border-0 ${
                    statusFilter === 'fraud'
                      ? 'bg-[#ff6e58] text-white'
                      : 'bg-[#fbfcf7] text-[#7a8982] hover:bg-[#f3f5ed]'
                  }`}
                >
                  Fraude ({stats?.fraudAlerts || 0})
                </button>
              </div>
            </div>

            {/* TABELA DE MOTORISTAS */}
            <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl shadow-xs overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-[#071d19]">
                  <thead className="bg-[#fbfcf7] border-b border-[rgba(7,29,25,0.1)] text-[#7a8982] uppercase font-space font-bold text-[10px] tracking-wider">
                    <tr>
                      <th className="py-3.5 px-4">WhatsApp / ID</th>
                      <th className="py-3.5 px-4">Status & Plano</th>
                      <th className="py-3.5 px-4">Válido Até</th>
                      <th className="py-3.5 px-4">Aparelho & Trocas</th>
                      <th className="py-3.5 px-4 text-right">Ações em 1-Clique</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[rgba(7,29,25,0.06)]">
                    {filteredLicenses.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="py-8 text-center text-[#7a8982]">
                          Nenhum motorista encontrado com os filtros atuais.
                        </td>
                      </tr>
                    ) : (
                      filteredLicenses.map((lic) => {
                        const isExpired = new Date(lic.validoAte || 0).getTime() < Date.now();
                        return (
                          <tr key={lic.id} className="hover:bg-[#fbfcf7] transition">
                            <td className="py-3.5 px-4 font-mono font-semibold">
                              <div className="flex items-center gap-2">
                                <span>{lic.whatsapp || lic.id}</span>
                                {lic.fraudSuspicion && (
                                  <span className="px-1.5 py-0.5 rounded bg-red-100 text-red-700 text-[10px] font-space font-bold">
                                    🚨 Suspeita
                                  </span>
                                )}
                              </div>
                              <span className="text-[10px] text-[#7a8982] font-sans block">
                                Desde {new Date(lic.createdAt).toLocaleDateString('pt-BR')}
                              </span>
                            </td>

                            <td className="py-3.5 px-4">
                              <span
                                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full font-space font-bold text-[10px] uppercase ${
                                  lic.status === 'blocked'
                                    ? 'bg-red-100 text-red-800 border border-red-200'
                                    : lic.plano === 'vitalicio'
                                    ? 'bg-[#071d19] text-[#d9ff4f]'
                                    : lic.plano === 'mensal' && !isExpired
                                    ? 'bg-[#dff4af] text-[#557000]'
                                    : lic.plano === 'trial' && !isExpired
                                    ? 'bg-sky-100 text-sky-800 border border-sky-200'
                                    : 'bg-[#ffe1d9] text-[#a34b3a]'
                                }`}
                              >
                                {lic.status === 'blocked'
                                  ? 'Bloqueado'
                                  : lic.plano === 'vitalicio'
                                  ? '👑 Vitalício'
                                  : lic.plano === 'mensal'
                                  ? (isExpired ? 'Mensal Vencido' : 'Mensal Ativo')
                                  : lic.plano === 'trial'
                                  ? (isExpired ? 'Teste Vencido' : 'Teste 7d')
                                  : 'Cortesia'}
                              </span>
                            </td>

                            <td className="py-3.5 px-4 font-mono text-[11px]">
                              {lic.validoAte
                                ? lic.validoAte.startsWith('2099')
                                  ? 'Para Sempre (Vitalício)'
                                  : new Date(lic.validoAte).toLocaleDateString('pt-BR')
                                : '--'}
                            </td>

                            <td className="py-3.5 px-4 text-[11px]">
                              <span className="block font-mono text-[10px] text-[#7a8982]">
                                {lic.deviceId ? lic.deviceId.slice(0, 18) + '...' : 'Sem vínculo'}
                              </span>
                              <span className={`text-[10px] font-bold ${lic.deviceTransfersCount >= 2 ? 'text-amber-600' : 'text-[#7a8982]'}`}>
                                {lic.deviceTransfersCount} trocas no mês
                              </span>
                            </td>

                            <td className="py-3.5 px-4 text-right">
                              <div className="flex items-center justify-end gap-1.5 flex-wrap">
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'add_days', 7)}
                                  className="px-2.5 py-1 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-800 rounded-lg text-[11px] font-space font-bold transition cursor-pointer"
                                  title="Adicionar 7 dias de cortesia"
                                >
                                  +7d
                                </button>
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'add_days', 30)}
                                  className="px-2.5 py-1 bg-blue-50 hover:bg-blue-100 border border-blue-200 text-blue-800 rounded-lg text-[11px] font-space font-bold transition cursor-pointer"
                                  title="Renovar 30 dias"
                                >
                                  +30d
                                </button>
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'set_lifetime')}
                                  className="px-2.5 py-1 bg-[#071d19] text-[#d9ff4f] hover:bg-[#0d302a] rounded-lg text-[11px] font-space font-bold transition cursor-pointer border-0"
                                  title="Conceder plano vitalício"
                                >
                                  Vitalício
                                </button>
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'reset_device')}
                                  className="px-2.5 py-1 bg-amber-50 hover:bg-amber-100 border border-amber-200 text-amber-800 rounded-lg text-[11px] font-space font-bold transition cursor-pointer"
                                  title="Desvincular aparelho e zerar trocas"
                                >
                                  Resetar
                                </button>
                                {lic.status === 'blocked' ? (
                                  <button
                                    onClick={() => executeAction(lic.whatsapp, 'unblock')}
                                    className="px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-[11px] font-space font-bold transition cursor-pointer border-0"
                                    title="Desbloquear motorista"
                                  >
                                    Desbloquear
                                  </button>
                                ) : (
                                  <button
                                    onClick={() => executeAction(lic.whatsapp, 'block')}
                                    className="px-2.5 py-1 bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 rounded-lg text-[11px] font-space font-bold transition cursor-pointer"
                                    title="Bloquear motorista"
                                  >
                                    Bloquear
                                  </button>
                                )}
                              </div>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* ABA 3: GESTÃO DE VOUCHERS & CUPONS                                        */}
        {/* ========================================================================= */}
        {/* ========================================================================= */}
        {/* ABA 3: GESTÃO DE VOUCHERS & CUPONS                                        */}
        {/* ========================================================================= */}
        {activeTab === 'vouchers' && (
          <div className="space-y-6">
            {/* CRIADOR DE NOVO VOUCHER */}
            <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-6 shadow-xs">
              <div className="flex items-center gap-2 mb-4">
                <Ticket className="w-5 h-5 text-[#071d19]" />
                <h2 className="font-space text-base font-bold text-[#071d19]">Criar Novo Cupom / Voucher de Cortesia</h2>
              </div>

              <form onSubmit={handleCreateVoucher} className="grid grid-cols-1 md:grid-cols-12 gap-4">
                {/* Código do Cupom */}
                <div className="md:col-span-3">
                  <label className="block text-[11px] font-space font-bold text-[#071d19] uppercase tracking-wider mb-1.5">
                    Código do Cupom *
                  </label>
                  <input
                    type="text"
                    value={newVoucherCode}
                    onChange={(e) => setNewVoucherCode(e.target.value.toUpperCase().replace(/\s/g, ''))}
                    placeholder="Ex: BEMVINDO7"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs font-mono font-bold text-[#071d19] placeholder-[#7a8982]/60 focus:outline-none focus:border-[#071d19] transition uppercase"
                    required
                  />
                </div>

                {/* Descrição */}
                <div className="md:col-span-4">
                  <label className="block text-[11px] font-space font-bold text-[#071d19] uppercase tracking-wider mb-1.5">
                    Descrição ou Parceria
                  </label>
                  <input
                    type="text"
                    value={newVoucherDesc}
                    onChange={(e) => setNewVoucherDesc(e.target.value)}
                    placeholder="Ex: Grupo Uber Campinas / Cortesia"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs text-[#071d19] placeholder-[#7a8982]/60 focus:outline-none focus:border-[#071d19] transition"
                  />
                </div>

                {/* Duração / Dias */}
                <div className="md:col-span-3">
                  <label className="block text-[11px] font-space font-bold text-[#071d19] uppercase tracking-wider mb-1.5">
                    Duração
                  </label>
                  <div className="flex items-center gap-2">
                    <select
                      value={newVoucherLifetime ? 'lifetime' : newVoucherDays}
                      onChange={(e) => {
                        if (e.target.value === 'lifetime') {
                          setNewVoucherLifetime(true);
                          setNewVoucherDays(999);
                        } else {
                          setNewVoucherLifetime(false);
                          setNewVoucherDays(Number(e.target.value));
                        }
                      }}
                      className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs font-medium text-[#071d19] focus:outline-none focus:border-[#071d19] transition cursor-pointer"
                    >
                      <option value="7">7 Dias Grátis</option>
                      <option value="15">15 Dias Grátis</option>
                      <option value="30">30 Dias (1 Mês)</option>
                      <option value="60">60 Dias (2 Meses)</option>
                      <option value="lifetime">👑 Acesso Vitalício (Para Sempre)</option>
                    </select>
                  </div>
                </div>

                {/* Limite de Usos */}
                <div className="md:col-span-2">
                  <label className="block text-[11px] font-space font-bold text-[#071d19] uppercase tracking-wider mb-1.5">
                    Limite de Usos
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={newVoucherMaxUses}
                    onChange={(e) => setNewVoucherMaxUses(Number(e.target.value))}
                    placeholder="0 = Ilimitado"
                    className="w-full bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl px-3.5 py-2.5 text-xs text-[#071d19] placeholder-[#7a8982]/60 focus:outline-none focus:border-[#071d19] transition"
                  />
                </div>

                <div className="md:col-span-12 flex justify-end">
                  <button
                    type="submit"
                    disabled={voucherSubmitting || !newVoucherCode.trim()}
                    className="bg-[#071d19] hover:bg-[#0d302a] text-[#d9ff4f] font-space font-bold px-5 py-2.5 rounded-xl shadow-xs transition flex items-center gap-2 text-xs cursor-pointer border-0 disabled:opacity-50"
                  >
                    {voucherSubmitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                    <span>Criar e Liberar Cupom</span>
                  </button>
                </div>
              </form>
            </div>

            {/* LISTA DE VOUCHERS CRIADOS */}
            <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl shadow-xs overflow-hidden">
              <div className="p-4 border-b border-[rgba(7,29,25,0.1)] flex items-center justify-between">
                <div>
                  <h3 className="font-space font-bold text-sm text-[#071d19]">Cupons Ativos e Histórico ({vouchers.length})</h3>
                  <p className="text-xs text-[#7a8982]">Motoristas podem resgatar esses códigos diretamente na aba "Plano Pro" do app</p>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-[#071d19]">
                  <thead className="bg-[#fbfcf7] border-b border-[rgba(7,29,25,0.1)] text-[#7a8982] uppercase font-space font-bold text-[10px] tracking-wider">
                    <tr>
                      <th className="py-3.5 px-4">Código do Cupom</th>
                      <th className="py-3.5 px-4">Descrição</th>
                      <th className="py-3.5 px-4">Duração</th>
                      <th className="py-3.5 px-4">Utilizações</th>
                      <th className="py-3.5 px-4">Status</th>
                      <th className="py-3.5 px-4 text-right">Ações</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[rgba(7,29,25,0.06)]">
                    {vouchers.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-[#7a8982]">
                          Nenhum voucher criado ainda. Use o formulário acima para criar seu primeiro cupom!
                        </td>
                      </tr>
                    ) : (
                      vouchers.map((v) => {
                        const isCopied = copiedVoucher === v.code;
                        return (
                          <tr key={v.code} className="hover:bg-[#fbfcf7] transition">
                            <td className="py-3.5 px-4">
                              <div className="flex items-center gap-2">
                                <span className="font-mono font-bold text-sm text-[#071d19] bg-[#fbfcf7] px-2.5 py-1 rounded-lg border border-[rgba(7,29,25,0.14)]">
                                  {v.code}
                                </span>
                                <button
                                  onClick={() => handleCopyVoucher(v.code)}
                                  className="p-1.5 rounded-lg border border-[rgba(7,29,25,0.12)] hover:bg-[#f3f5ed] text-[#7a8982] transition cursor-pointer"
                                  title="Copiar código"
                                >
                                  {isCopied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                                </button>
                              </div>
                            </td>

                            <td className="py-3.5 px-4 text-[11px] text-[#7a8982]">
                              {v.description}
                            </td>

                            <td className="py-3.5 px-4">
                              <span className="font-space font-bold text-xs text-[#071d19]">
                                {v.isLifetime ? '👑 Vitalício' : `+${v.days} dias`}
                              </span>
                            </td>

                            <td className="py-3.5 px-4 font-mono text-[11px]">
                              <span className="font-bold text-[#071d19]">{v.usedCount}</span>
                              <span className="text-[#7a8982]">
                                {v.maxUses > 0 ? ` / ${v.maxUses} usos` : ' usos (ilimitado)'}
                              </span>
                            </td>

                            <td className="py-3.5 px-4">
                              <span
                                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full font-space font-bold text-[10px] uppercase ${
                                  v.active
                                    ? 'bg-[#dff4af] text-[#557000]'
                                    : 'bg-slate-100 text-slate-600 border border-slate-200'
                                }`}
                              >
                                {v.active ? 'Ativo' : 'Desativado'}
                              </span>
                            </td>

                            <td className="py-3.5 px-4 text-right">
                              <div className="flex items-center justify-end gap-2">
                                <button
                                  onClick={() => handleToggleVoucher(v.code, v.active)}
                                  className={`px-2.5 py-1 rounded-lg border text-[11px] font-space font-bold transition cursor-pointer ${
                                    v.active
                                      ? 'bg-amber-50 border-amber-200 text-amber-800 hover:bg-amber-100'
                                      : 'bg-emerald-50 border-emerald-200 text-emerald-800 hover:bg-emerald-100'
                                  }`}
                                  title={v.active ? 'Desativar cupom' : 'Ativar cupom'}
                                >
                                  {v.active ? 'Pausar' : 'Ativar'}
                                </button>

                                <button
                                  onClick={() => handleDeleteVoucher(v.code)}
                                  className="p-1.5 rounded-lg border border-red-200 bg-red-50 hover:bg-red-100 text-red-700 transition cursor-pointer"
                                  title="Excluir cupom"
                                >
                                  <Trash2 className="w-3.5 h-3.5" />
                                </button>
                              </div>
                            </td>
                          </tr>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>
        )}

        {/* ABA: CAIXA POSTAL (FALE CONOSCO) */}
        {activeTab === 'mailbox' && (
          <div className="space-y-6">
            {/* CABEÇALHO DA CAIXA POSTAL & FILTROS */}
            <div className="bg-white border border-[rgba(7,29,25,0.12)] p-5 rounded-2xl shadow-xs space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                <div>
                  <h3 className="font-space font-bold text-base text-[#071d19] flex items-center gap-2">
                    <Mail className="w-5 h-5 text-[#6b8900]" />
                    <span>Caixa Postal dos Motoristas (Fale Conosco)</span>
                  </h3>
                  <p className="text-xs text-[#7a8982] mt-0.5">
                    Mensagens, dúvidas, sugestões e pedidos de ajuda enviados diretamente pelo site ou aplicativo.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <span className="text-xs font-semibold text-[#7a8982]">
                    Total: {messages.length} ({messages.filter((m) => m.status === 'unread').length} não lidas)
                  </span>
                </div>
              </div>

              {/* FILTROS E BUSCA */}
              <div className="flex flex-col sm:flex-row sm:items-center gap-3 pt-2 border-t border-[rgba(7,29,25,0.06)]">
                <div className="relative flex-1">
                  <Search className="w-4 h-4 text-[#7a8982] absolute left-3 top-2.5" />
                  <input
                    type="text"
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    placeholder="Buscar por nome, telefone ou mensagem..."
                    className="w-full pl-9 pr-3 py-1.5 bg-[#fbfcf7] border border-[rgba(7,29,25,0.14)] rounded-xl text-xs text-[#071d19] focus:outline-none focus:border-[#071d19]"
                  />
                </div>

                <div className="flex items-center gap-1.5 flex-wrap">
                  {(['all', 'unread', 'read', 'replied'] as const).map((st) => {
                    const label =
                      st === 'all'
                        ? 'Todas'
                        : st === 'unread'
                        ? 'Não Lidas'
                        : st === 'read'
                        ? 'Lidas'
                        : 'Respondidas';
                    const count =
                      st === 'all' ? messages.length : messages.filter((m) => m.status === st).length;
                    return (
                      <button
                        key={st}
                        onClick={() => setMessageFilter(st)}
                        className={`px-3 py-1.5 rounded-xl text-xs font-space font-bold transition cursor-pointer border-0 ${
                          messageFilter === st
                            ? 'bg-[#071d19] text-[#d9ff4f]'
                            : 'bg-[#fbfcf7] text-[#7a8982] hover:bg-[#f3f5ed]'
                        }`}
                      >
                        {label} ({count})
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>

            {/* LISTAGEM DE MENSAGENS */}
            <div className="space-y-3">
              {messages
                .filter((msg) => {
                  if (messageFilter !== 'all' && msg.status !== messageFilter) return false;
                  if (!searchTerm.trim()) return true;
                  const term = searchTerm.toLowerCase();
                  return (
                    msg.name.toLowerCase().includes(term) ||
                    msg.whatsapp.toLowerCase().includes(term) ||
                    msg.message.toLowerCase().includes(term) ||
                    msg.category.toLowerCase().includes(term)
                  );
                })
                .map((msg) => {
                  const cleanPhone = msg.whatsapp.replace(/\D/g, '');
                  const waNumber = cleanPhone.startsWith('55') ? cleanPhone : `55${cleanPhone}`;
                  const waLink = `https://wa.me/${waNumber}?text=${encodeURIComponent(
                    `Olá ${msg.name}! Aqui é do suporte GoOrNo sobre sua mensagem enviada no Fale Conosco.`
                  )}`;

                  const catLabels: Record<string, { label: string; color: string }> = {
                    duvida: { label: 'Dúvida Geral', color: 'bg-blue-100 text-blue-800 border-blue-200' },
                    sugestao: { label: 'Sugestão / Melhoria', color: 'bg-emerald-100 text-emerald-800 border-emerald-200' },
                    problema: { label: 'Problema / Erro', color: 'bg-rose-100 text-rose-800 border-rose-200' },
                    financeiro: { label: 'Assinatura / PIX', color: 'bg-amber-100 text-amber-800 border-amber-200' },
                    outro: { label: 'Outro', color: 'bg-slate-100 text-slate-800 border-slate-200' }
                  };
                  const catInfo = catLabels[msg.category] || catLabels.outro;

                  return (
                    <div
                      key={msg.id}
                      className={`bg-white border rounded-2xl p-5 shadow-xs transition ${
                        msg.status === 'unread'
                          ? 'border-amber-300 ring-2 ring-amber-100 bg-amber-50/20'
                          : 'border-[rgba(7,29,25,0.12)]'
                      }`}
                    >
                      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-3">
                        <div className="flex items-center gap-2.5 flex-wrap">
                          <span className="font-space font-bold text-sm text-[#071d19]">{msg.name}</span>
                          <a
                            href={waLink}
                            target="_blank"
                            rel="noreferrer"
                            className="font-mono text-xs text-[#6b8900] bg-[#dff4af]/60 hover:bg-[#dff4af] px-2 py-0.5 rounded-lg flex items-center gap-1 transition font-bold"
                            title="Conversar no WhatsApp"
                          >
                            <span>{msg.whatsapp}</span>
                            <ExternalLink className="w-3 h-3" />
                          </a>
                          <span
                            className={`text-[10px] font-space font-bold px-2 py-0.5 rounded-full border ${catInfo.color}`}
                          >
                            {catInfo.label}
                          </span>
                        </div>

                        <div className="flex items-center gap-2 text-xs text-[#7a8982]">
                          <Clock className="w-3.5 h-3.5" />
                          <span>{new Date(msg.createdAt).toLocaleString('pt-BR')}</span>
                          <span
                            className={`px-2 py-0.5 rounded-full font-space font-bold text-[10px] uppercase ${
                              msg.status === 'unread'
                                ? 'bg-amber-100 text-amber-800'
                                : msg.status === 'replied'
                                ? 'bg-[#dff4af] text-[#557000]'
                                : 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {msg.status === 'unread'
                              ? 'Não Lida'
                              : msg.status === 'replied'
                              ? 'Respondida'
                              : 'Lida'}
                          </span>
                        </div>
                      </div>

                      {/* CONTEÚDO DA MENSAGEM */}
                      <div className="bg-[#fbfcf7] p-3.5 rounded-xl border border-[rgba(7,29,25,0.08)] text-xs sm:text-sm text-[#071d19] whitespace-pre-wrap leading-relaxed mb-4">
                        {msg.message}
                      </div>

                      {/* BARRA DE AÇÕES */}
                      <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-[rgba(7,29,25,0.06)]">
                        <div className="flex items-center gap-2">
                          <a
                            href={waLink}
                            target="_blank"
                            rel="noreferrer"
                            onClick={() => {
                              if (msg.status === 'unread') {
                                handleUpdateMessageStatus(msg.id, 'replied');
                              }
                            }}
                            className="inline-flex items-center gap-1.5 bg-[#25D366] hover:bg-[#20ba5a] text-white text-xs font-space font-bold px-3 py-1.5 rounded-xl transition shadow-2xs"
                          >
                            <ExternalLink className="w-3.5 h-3.5" />
                            <span>Responder no WhatsApp</span>
                          </a>

                          {msg.status !== 'read' && (
                            <button
                              onClick={() => handleUpdateMessageStatus(msg.id, 'read')}
                              className="px-2.5 py-1.5 bg-slate-100 hover:bg-slate-200 text-slate-700 text-xs font-space font-medium rounded-xl transition cursor-pointer border-0"
                            >
                              Marcar como Lida
                            </button>
                          )}

                          {msg.status !== 'replied' && (
                            <button
                              onClick={() => handleUpdateMessageStatus(msg.id, 'replied')}
                              className="px-2.5 py-1.5 bg-emerald-50 hover:bg-emerald-100 text-emerald-800 text-xs font-space font-medium rounded-xl transition cursor-pointer border-0"
                            >
                              Marcar como Respondida
                            </button>
                          )}

                          {msg.status !== 'unread' && (
                            <button
                              onClick={() => handleUpdateMessageStatus(msg.id, 'unread')}
                              className="px-2.5 py-1.5 bg-amber-50 hover:bg-amber-100 text-amber-800 text-xs font-space font-medium rounded-xl transition cursor-pointer border-0"
                            >
                              Marcar Não Lida
                            </button>
                          )}
                        </div>

                        <button
                          onClick={() => handleDeleteMessage(msg.id)}
                          className="p-1.5 text-red-600 hover:bg-red-50 rounded-lg transition cursor-pointer border-0"
                          title="Remover mensagem"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  );
                })}

              {messages.length === 0 && (
                <div className="bg-white border border-[rgba(7,29,25,0.12)] rounded-2xl p-12 text-center text-[#7a8982]">
                  <Mail className="w-8 h-8 mx-auto mb-2 text-[#7a8982]/50" />
                  <p className="font-space font-bold text-sm text-[#071d19]">Nenhuma mensagem recebida ainda.</p>
                  <p className="text-xs mt-1">
                    Quando os motoristas enviarem dúvidas ou sugestões pelo Fale Conosco, elas aparecerão aqui.
                  </p>
                </div>
              )}
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

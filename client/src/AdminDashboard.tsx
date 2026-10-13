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
  Gift
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
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'trial' | 'expired' | 'fraud'>('all');
  const [activeTab, setActiveTab] = useState<'overview' | 'drivers' | 'vouchers'>('overview');
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
      const [resStats, resVouchers] = await Promise.all([
        fetch(`/api/admin/stats-and-licenses?adminPassword=${encodeURIComponent(password)}`),
        fetch(`/api/admin/vouchers?adminPassword=${encodeURIComponent(password)}`)
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
      } else {
        setAuthError('Erro ao carregar dados do servidor.');
      }
    } catch {
      setAuthError('Falha na conexão com o servidor.');
    } finally {
      setLoading(false);
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

  // TELA DE LOGIN CLEAN NO ESTILO MEUAUTÔNOMO
  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-[#f5f7f2] text-[#173a34] flex items-center justify-center p-4 sm:p-6 font-sans">
        <div className="w-full max-w-md bg-white border border-[#dce5dc] rounded-[28px] p-8 shadow-[0_20px_60px_rgba(23,58,52,0.08)]">
          <div className="text-center mb-6">
            <div className="mx-auto mb-3 grid h-14 w-14 place-items-center rounded-2xl bg-[#173a34] text-[#d9f56a] shadow-xs">
              <Lock className="h-6 w-6" />
            </div>
            <h1 className="text-2xl font-bold text-[#173a34]">Painel Administrativo</h1>
            <p className="mt-1 text-xs text-[#71867f]">
              Área de controle de motoristas, cupons de cortesia e combate à fraude
            </p>
          </div>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-bold text-[#173a34] uppercase tracking-wider mb-2">
                Senha Master
              </label>
              <input
                type="password"
                value={inputPassword}
                onChange={(e) => setInputPassword(e.target.value)}
                placeholder="Digite a senha administrativa..."
                className="w-full bg-[#f8faf7] border border-[#dce5dc] focus:border-[#173a34] rounded-xl px-4 py-3 text-[#173a34] placeholder-[#71867f]/60 focus:outline-none transition"
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
              className="w-full bg-[#173a34] hover:bg-[#122e29] text-[#d9f56a] font-bold py-3 px-4 rounded-xl shadow-xs transition flex items-center justify-center gap-2 cursor-pointer"
            >
              {loading ? <RefreshCw className="w-5 h-5 animate-spin" /> : 'Acessar Painel Master'}
            </button>

            {onBackToApp && (
              <button
                type="button"
                onClick={onBackToApp}
                className="w-full text-xs font-semibold text-[#71867f] hover:text-[#173a34] py-2 text-center transition cursor-pointer"
              >
                ← Voltar ao App GoOrNo
              </button>
            )}
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
    <div className="min-h-screen bg-[#f5f7f2] text-[#173a34] flex flex-col font-sans">
      {/* HEADER LIMPO E ELEGANTE NO ESTILO MEUAUTÔNOMO */}
      <header className="border-b border-[#dce5dc] bg-white sticky top-0 z-40 shadow-2xs">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6">
          <div className="flex items-center gap-3">
            <div className="h-10 w-10 rounded-xl bg-[#173a34] flex items-center justify-center font-black text-[#d9f56a] text-sm shadow-xs">
              GO
            </div>
            <div>
              <div className="flex items-center gap-2">
                <span className="font-bold text-base text-[#173a34] tracking-tight">GoOrNo Master</span>
                <span className="rounded-full bg-[#173a34] px-2.5 py-0.5 text-[10px] font-bold uppercase tracking-wider text-[#d9f56a]">
                  Admin Master
                </span>
              </div>
              <p className="text-[11px] text-[#71867f]">Painel de Gestão e Cupons de Cortesia</p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={() => loadData(adminPassword)}
              disabled={loading}
              className="p-2 border border-[#dce5dc] bg-white hover:bg-[#f2f7f0] text-[#173a34] rounded-xl transition shadow-2xs cursor-pointer"
              title="Atualizar Dados"
            >
              <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
            </button>

            {onBackToApp && (
              <button
                onClick={onBackToApp}
                className="hidden sm:inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-[#dce5dc] bg-white hover:bg-[#f2f7f0] text-xs font-semibold text-[#173a34] transition shadow-2xs cursor-pointer"
              >
                <span>Ver App</span>
                <ArrowUpRight className="h-3 w-3 text-[#71867f]" />
              </button>
            )}

            <button
              onClick={handleLogout}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl border border-red-200 bg-red-50 hover:bg-red-100 text-xs font-semibold text-red-700 transition cursor-pointer"
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

        {/* NAVEGAÇÃO ENTRE ABAS */}
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-[#dce5dc] pb-4">
          <div className="flex items-center gap-2 overflow-x-auto">
            <button
              onClick={() => setActiveTab('overview')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition cursor-pointer ${
                activeTab === 'overview'
                  ? 'bg-[#173a34] text-[#d9f56a] shadow-xs'
                  : 'bg-white border border-[#dce5dc] text-[#173a34] hover:bg-[#f2f7f0]'
              }`}
            >
              <TrendingUp className="w-4 h-4" />
              <span>Visão Geral & Faturamento</span>
            </button>

            <button
              onClick={() => setActiveTab('drivers')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition cursor-pointer ${
                activeTab === 'drivers'
                  ? 'bg-[#173a34] text-[#d9f56a] shadow-xs'
                  : 'bg-white border border-[#dce5dc] text-[#173a34] hover:bg-[#f2f7f0]'
              }`}
            >
              <Users className="w-4 h-4" />
              <span>Motoristas & Licenças ({licenses.length})</span>
            </button>

            <button
              onClick={() => setActiveTab('vouchers')}
              className={`flex items-center gap-2 px-4 py-2 rounded-xl text-xs sm:text-sm font-bold transition cursor-pointer ${
                activeTab === 'vouchers'
                  ? 'bg-[#173a34] text-[#d9f56a] shadow-xs'
                  : 'bg-white border border-[#dce5dc] text-[#173a34] hover:bg-[#f2f7f0]'
              }`}
            >
              <Ticket className="w-4 h-4" />
              <span>Cupons & Vouchers ({vouchers.length})</span>
            </button>
          </div>

          <div className="text-xs text-[#71867f]">
            Status: <span className="font-bold text-[#2d7d54]">Online & Sincronizado</span>
          </div>
        </div>

        {/* ========================================================================= */}
        {/* ABA 1: VISÃO GERAL & MÉTRICAS                                              */}
        {/* ========================================================================= */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            {/* GRID DE CARDS CLEAN */}
            <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-3 sm:gap-4">
              {/* Card 1: MRR */}
              <div className="col-span-2 sm:col-span-1 bg-white border border-[#dce5dc] rounded-2xl p-4 shadow-xs">
                <div className="flex items-center justify-between text-[#2d7d54] mb-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#71867f]">MRR Estimado</span>
                  <DollarSign className="w-4 h-4" />
                </div>
                <div className="text-2xl font-black text-[#173a34]">
                  R$ {stats?.estimatedMonthlyRevenue.toFixed(2).replace('.', ',') || '0,00'}
                </div>
                <p className="text-[11px] text-[#71867f] mt-1">Faturamento mensal recorrente</p>
              </div>

              {/* Card 2: Total Motoristas */}
              <div className="bg-white border border-[#dce5dc] rounded-2xl p-4 shadow-xs">
                <div className="flex items-center justify-between text-[#173a34] mb-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#71867f]">Motoristas</span>
                  <Users className="w-4 h-4 text-[#71867f]" />
                </div>
                <div className="text-2xl font-black text-[#173a34]">{stats?.totalUsers || 0}</div>
                <p className="text-[11px] text-[#71867f] mt-1">Total cadastrados</p>
              </div>

              {/* Card 3: Pagantes Ativos */}
              <div className="bg-white border border-[#dce5dc] rounded-2xl p-4 shadow-xs">
                <div className="flex items-center justify-between text-[#2d7d54] mb-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#71867f]">Pagantes</span>
                  <CheckCircle className="w-4 h-4" />
                </div>
                <div className="text-2xl font-black text-[#2d7d54]">{stats?.activePaid || 0}</div>
                <p className="text-[11px] text-[#71867f] mt-1">Mensal ou vitalício</p>
              </div>

              {/* Card 4: Em Teste */}
              <div className="bg-white border border-[#dce5dc] rounded-2xl p-4 shadow-xs">
                <div className="flex items-center justify-between text-[#0284c7] mb-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#71867f]">Em Teste</span>
                  <Clock className="w-4 h-4" />
                </div>
                <div className="text-2xl font-black text-[#0284c7]">{stats?.inTrial || 0}</div>
                <p className="text-[11px] text-[#71867f] mt-1">Período de 7 dias grátis</p>
              </div>

              {/* Card 5: Vencidas */}
              <div className="bg-white border border-[#dce5dc] rounded-2xl p-4 shadow-xs">
                <div className="flex items-center justify-between text-[#b45309] mb-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#71867f]">Vencidas</span>
                  <Calendar className="w-4 h-4" />
                </div>
                <div className="text-2xl font-black text-[#b45309]">{stats?.expired || 0}</div>
                <p className="text-[11px] text-[#71867f] mt-1">Aguardando renovação</p>
              </div>

              {/* Card 6: Alertas Fraude */}
              <div className="bg-white border border-red-200 rounded-2xl p-4 shadow-xs">
                <div className="flex items-center justify-between text-red-600 mb-1">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-[#71867f]">Fraude</span>
                  <ShieldAlert className="w-4 h-4" />
                </div>
                <div className="text-2xl font-black text-red-600">{stats?.fraudAlerts || 0}</div>
                <p className="text-[11px] text-[#71867f] mt-1">Suspeitas detectadas</p>
              </div>
            </div>

            {/* SEÇÃO DE DESTAQUE: REGRAS E STATUS RÁPIDO */}
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div className="bg-white border border-[#dce5dc] rounded-2xl p-5 shadow-xs space-y-3">
                <div className="flex items-center gap-2">
                  <ShieldCheck className="w-5 h-5 text-[#2d7d54]" />
                  <h3 className="font-bold text-sm text-[#173a34]">Sistema Anti-Fraude e Proteção de Licença</h3>
                </div>
                <p className="text-xs text-[#71867f] leading-relaxed">
                  Cada licença é vinculada exclusivamente ao ID do celular do motorista. O próprio motorista pode transferir para um novo aparelho até 2 vezes por mês. Tentativas adicionais disparam alerta automático para seu e-mail e são bloqueadas.
                </p>
              </div>

              <div className="bg-white border border-[#dce5dc] rounded-2xl p-5 shadow-xs space-y-3">
                <div className="flex items-center gap-2">
                  <Gift className="w-5 h-5 text-[#8aa500]" />
                  <h3 className="font-bold text-sm text-[#173a34]">Cupons de Cortesia e Campanhas</h3>
                </div>
                <p className="text-xs text-[#71867f] leading-relaxed">
                  Crie vouchers promocionais personalizados (ex: 7 dias de bônus, 30 dias ou cortesia vitalícia) para atrair novos motoristas em grupos de WhatsApp ou fechar parcerias.
                </p>
                <button
                  onClick={() => setActiveTab('vouchers')}
                  className="inline-flex items-center gap-1.5 text-xs font-bold text-[#173a34] bg-[#f7fbe8] hover:bg-[#edf7d7] border border-[#d9f56a] px-3 py-1.5 rounded-xl transition cursor-pointer"
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
            <div className="bg-white border border-[#dce5dc] rounded-2xl p-4 shadow-xs flex flex-col md:flex-row items-center justify-between gap-3">
              <div className="relative w-full md:w-80">
                <Search className="w-4 h-4 absolute left-3.5 top-3 text-[#71867f]" />
                <input
                  type="text"
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  placeholder="Buscar por WhatsApp ou ID..."
                  className="w-full pl-9 pr-4 py-2 bg-[#f8faf7] border border-[#dce5dc] rounded-xl text-xs text-[#173a34] placeholder-[#71867f]/70 focus:outline-none focus:border-[#173a34] transition"
                />
              </div>

              {/* Pílulas de filtro */}
              <div className="flex items-center gap-1.5 overflow-x-auto w-full md:w-auto">
                <button
                  onClick={() => setStatusFilter('all')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer ${
                    statusFilter === 'all'
                      ? 'bg-[#173a34] text-[#d9f56a]'
                      : 'bg-[#f8faf7] text-[#71867f] hover:bg-[#edf2eb]'
                  }`}
                >
                  Todos ({licenses.length})
                </button>
                <button
                  onClick={() => setStatusFilter('active')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer ${
                    statusFilter === 'active'
                      ? 'bg-[#2d7d54] text-white'
                      : 'bg-[#f8faf7] text-[#71867f] hover:bg-[#edf2eb]'
                  }`}
                >
                  Pagantes ({stats?.activePaid || 0})
                </button>
                <button
                  onClick={() => setStatusFilter('trial')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer ${
                    statusFilter === 'trial'
                      ? 'bg-[#0284c7] text-white'
                      : 'bg-[#f8faf7] text-[#71867f] hover:bg-[#edf2eb]'
                  }`}
                >
                  Teste ({stats?.inTrial || 0})
                </button>
                <button
                  onClick={() => setStatusFilter('expired')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer ${
                    statusFilter === 'expired'
                      ? 'bg-[#b45309] text-white'
                      : 'bg-[#f8faf7] text-[#71867f] hover:bg-[#edf2eb]'
                  }`}
                >
                  Vencidos ({stats?.expired || 0})
                </button>
                <button
                  onClick={() => setStatusFilter('fraud')}
                  className={`px-3 py-1.5 rounded-xl text-xs font-bold transition cursor-pointer ${
                    statusFilter === 'fraud'
                      ? 'bg-red-600 text-white'
                      : 'bg-[#f8faf7] text-[#71867f] hover:bg-[#edf2eb]'
                  }`}
                >
                  Fraude ({stats?.fraudAlerts || 0})
                </button>
              </div>
            </div>

            {/* TABELA DE MOTORISTAS */}
            <div className="bg-white border border-[#dce5dc] rounded-2xl shadow-xs overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-[#173a34]">
                  <thead className="bg-[#f8faf7] border-b border-[#dce5dc] text-[#71867f] uppercase font-bold text-[10px] tracking-wider">
                    <tr>
                      <th className="py-3 px-4">WhatsApp / ID</th>
                      <th className="py-3 px-4">Status & Plano</th>
                      <th className="py-3 px-4">Válido Até</th>
                      <th className="py-3 px-4">Aparelho & Trocas</th>
                      <th className="py-3 px-4 text-right">Ações em 1-Clique</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#dce5dc]/60">
                    {filteredLicenses.length === 0 ? (
                      <tr>
                        <td colSpan={5} className="py-8 text-center text-[#71867f]">
                          Nenhum motorista encontrado com os filtros atuais.
                        </td>
                      </tr>
                    ) : (
                      filteredLicenses.map((lic) => {
                        const isExpired = new Date(lic.validoAte || 0).getTime() < Date.now();
                        return (
                          <tr key={lic.id} className="hover:bg-[#f8faf7] transition">
                            <td className="py-3 px-4 font-mono font-semibold">
                              <div className="flex items-center gap-2">
                                <span>{lic.whatsapp || lic.id}</span>
                                {lic.fraudSuspicion && (
                                  <span className="px-1.5 py-0.5 rounded bg-red-100 text-red-700 text-[10px] font-bold">
                                    🚨 Suspeita
                                  </span>
                                )}
                              </div>
                              <span className="text-[10px] text-[#71867f] font-sans block">
                                Desde {new Date(lic.createdAt).toLocaleDateString('pt-BR')}
                              </span>
                            </td>

                            <td className="py-3 px-4">
                              <span
                                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full font-bold text-[10px] uppercase ${
                                  lic.status === 'blocked'
                                    ? 'bg-red-100 text-red-800 border border-red-200'
                                    : lic.plano === 'vitalicio'
                                    ? 'bg-[#173a34] text-[#d9f56a]'
                                    : lic.plano === 'mensal' && !isExpired
                                    ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                                    : lic.plano === 'trial' && !isExpired
                                    ? 'bg-sky-100 text-sky-800 border border-sky-200'
                                    : 'bg-amber-100 text-amber-800 border border-amber-200'
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

                            <td className="py-3 px-4 font-mono text-[11px]">
                              {lic.validoAte
                                ? lic.validoAte.startsWith('2099')
                                  ? 'Para Sempre (Vitalício)'
                                  : new Date(lic.validoAte).toLocaleDateString('pt-BR')
                                : '--'}
                            </td>

                            <td className="py-3 px-4 text-[11px]">
                              <span className="block font-mono text-[10px] text-[#71867f]">
                                {lic.deviceId ? lic.deviceId.slice(0, 18) + '...' : 'Sem vínculo'}
                              </span>
                              <span className={`text-[10px] font-bold ${lic.deviceTransfersCount >= 2 ? 'text-amber-600' : 'text-[#71867f]'}`}>
                                {lic.deviceTransfersCount} trocas no mês
                              </span>
                            </td>

                            <td className="py-3 px-4 text-right">
                              <div className="flex items-center justify-end gap-1.5 flex-wrap">
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'add_days', 7)}
                                  className="px-2 py-1 bg-emerald-50 hover:bg-emerald-100 border border-emerald-200 text-emerald-800 rounded-lg text-[11px] font-bold transition cursor-pointer"
                                  title="Adicionar 7 dias de cortesia"
                                >
                                  +7d
                                </button>
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'add_days', 30)}
                                  className="px-2 py-1 bg-blue-50 hover:bg-blue-100 border border-blue-200 text-blue-800 rounded-lg text-[11px] font-bold transition cursor-pointer"
                                  title="Renovar 30 dias"
                                >
                                  +30d
                                </button>
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'set_lifetime')}
                                  className="px-2 py-1 bg-[#173a34] text-[#d9f56a] hover:bg-[#122e29] rounded-lg text-[11px] font-bold transition cursor-pointer"
                                  title="Conceder plano vitalício"
                                >
                                  Vitalício
                                </button>
                                <button
                                  onClick={() => executeAction(lic.whatsapp, 'reset_device')}
                                  className="px-2 py-1 bg-amber-50 hover:bg-amber-100 border border-amber-200 text-amber-800 rounded-lg text-[11px] font-bold transition cursor-pointer"
                                  title="Desvincular aparelho e zerar trocas"
                                >
                                  Resetar
                                </button>
                                {lic.status === 'blocked' ? (
                                  <button
                                    onClick={() => executeAction(lic.whatsapp, 'unblock')}
                                    className="px-2 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg text-[11px] font-bold transition cursor-pointer"
                                    title="Desbloquear motorista"
                                  >
                                    Desbloquear
                                  </button>
                                ) : (
                                  <button
                                    onClick={() => executeAction(lic.whatsapp, 'block')}
                                    className="px-2 py-1 bg-red-50 hover:bg-red-100 border border-red-200 text-red-700 rounded-lg text-[11px] font-bold transition cursor-pointer"
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
        {activeTab === 'vouchers' && (
          <div className="space-y-6">
            {/* CRIADOR DE NOVO VOUCHER */}
            <div className="bg-white border border-[#dce5dc] rounded-2xl p-6 shadow-xs">
              <div className="flex items-center gap-2 mb-4">
                <Ticket className="w-5 h-5 text-[#173a34]" />
                <h2 className="text-base font-bold text-[#173a34]">Criar Novo Cupom / Voucher de Cortesia</h2>
              </div>

              <form onSubmit={handleCreateVoucher} className="grid grid-cols-1 md:grid-cols-12 gap-4">
                {/* Código do Cupom */}
                <div className="md:col-span-3">
                  <label className="block text-xs font-bold text-[#173a34] uppercase tracking-wider mb-1.5">
                    Código do Cupom *
                  </label>
                  <input
                    type="text"
                    value={newVoucherCode}
                    onChange={(e) => setNewVoucherCode(e.target.value.toUpperCase().replace(/\s/g, ''))}
                    placeholder="Ex: BEMVINDO7"
                    className="w-full bg-[#f8faf7] border border-[#dce5dc] rounded-xl px-3.5 py-2.5 text-xs font-mono font-bold text-[#173a34] placeholder-[#71867f]/60 focus:outline-none focus:border-[#173a34] transition uppercase"
                    required
                  />
                </div>

                {/* Descrição */}
                <div className="md:col-span-4">
                  <label className="block text-xs font-bold text-[#173a34] uppercase tracking-wider mb-1.5">
                    Descrição ou Parceria
                  </label>
                  <input
                    type="text"
                    value={newVoucherDesc}
                    onChange={(e) => setNewVoucherDesc(e.target.value)}
                    placeholder="Ex: Grupo Uber Campinas / Cortesia"
                    className="w-full bg-[#f8faf7] border border-[#dce5dc] rounded-xl px-3.5 py-2.5 text-xs text-[#173a34] placeholder-[#71867f]/60 focus:outline-none focus:border-[#173a34] transition"
                  />
                </div>

                {/* Duração / Dias */}
                <div className="md:col-span-3">
                  <label className="block text-xs font-bold text-[#173a34] uppercase tracking-wider mb-1.5">
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
                      className="w-full bg-[#f8faf7] border border-[#dce5dc] rounded-xl px-3.5 py-2.5 text-xs text-[#173a34] focus:outline-none focus:border-[#173a34] transition cursor-pointer"
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
                  <label className="block text-xs font-bold text-[#173a34] uppercase tracking-wider mb-1.5">
                    Limite de Usos
                  </label>
                  <input
                    type="number"
                    min="0"
                    value={newVoucherMaxUses}
                    onChange={(e) => setNewVoucherMaxUses(Number(e.target.value))}
                    placeholder="0 = Ilimitado"
                    className="w-full bg-[#f8faf7] border border-[#dce5dc] rounded-xl px-3.5 py-2.5 text-xs text-[#173a34] placeholder-[#71867f]/60 focus:outline-none focus:border-[#173a34] transition"
                  />
                </div>

                <div className="md:col-span-12 flex justify-end">
                  <button
                    type="submit"
                    disabled={voucherSubmitting || !newVoucherCode.trim()}
                    className="bg-[#173a34] hover:bg-[#122e29] text-[#d9f56a] font-bold px-5 py-2.5 rounded-xl shadow-xs transition flex items-center gap-2 text-xs cursor-pointer disabled:opacity-50"
                  >
                    {voucherSubmitting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Plus className="w-4 h-4" />}
                    <span>Criar e Liberar Cupom</span>
                  </button>
                </div>
              </form>
            </div>

            {/* LISTA DE VOUCHERS CRIADOS */}
            <div className="bg-white border border-[#dce5dc] rounded-2xl shadow-xs overflow-hidden">
              <div className="p-4 border-b border-[#dce5dc] flex items-center justify-between">
                <div>
                  <h3 className="font-bold text-sm text-[#173a34]">Cupons Ativos e Histórico ({vouchers.length})</h3>
                  <p className="text-xs text-[#71867f]">Motoristas podem resgatar esses códigos diretamente na aba "Plano Pro" do app</p>
                </div>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-[#173a34]">
                  <thead className="bg-[#f8faf7] border-b border-[#dce5dc] text-[#71867f] uppercase font-bold text-[10px] tracking-wider">
                    <tr>
                      <th className="py-3 px-4">Código do Cupom</th>
                      <th className="py-3 px-4">Descrição</th>
                      <th className="py-3 px-4">Duração</th>
                      <th className="py-3 px-4">Utilizações</th>
                      <th className="py-3 px-4">Status</th>
                      <th className="py-3 px-4 text-right">Ações</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#dce5dc]/60">
                    {vouchers.length === 0 ? (
                      <tr>
                        <td colSpan={6} className="py-8 text-center text-[#71867f]">
                          Nenhum voucher criado ainda. Use o formulário acima para criar seu primeiro cupom!
                        </td>
                      </tr>
                    ) : (
                      vouchers.map((v) => {
                        const isCopied = copiedVoucher === v.code;
                        return (
                          <tr key={v.code} className="hover:bg-[#f8faf7] transition">
                            <td className="py-3 px-4">
                              <div className="flex items-center gap-2">
                                <span className="font-mono font-bold text-sm text-[#173a34] bg-[#f8faf7] px-2.5 py-1 rounded-lg border border-[#dce5dc]">
                                  {v.code}
                                </span>
                                <button
                                  onClick={() => handleCopyVoucher(v.code)}
                                  className="p-1.5 rounded-lg border border-[#dce5dc] hover:bg-[#edf2eb] text-[#71867f] transition cursor-pointer"
                                  title="Copiar código"
                                >
                                  {isCopied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
                                </button>
                              </div>
                            </td>

                            <td className="py-3 px-4 text-[11px] text-[#71867f]">
                              {v.description}
                            </td>

                            <td className="py-3 px-4">
                              <span className="font-bold text-xs text-[#173a34]">
                                {v.isLifetime ? '👑 Vitalício' : `+${v.days} dias`}
                              </span>
                            </td>

                            <td className="py-3 px-4 font-mono text-[11px]">
                              <span className="font-bold text-[#173a34]">{v.usedCount}</span>
                              <span className="text-[#71867f]">
                                {v.maxUses > 0 ? ` / ${v.maxUses} usos` : ' usos (ilimitado)'}
                              </span>
                            </td>

                            <td className="py-3 px-4">
                              <span
                                className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full font-bold text-[10px] uppercase ${
                                  v.active
                                    ? 'bg-emerald-100 text-emerald-800 border border-emerald-200'
                                    : 'bg-slate-100 text-slate-600 border border-slate-200'
                                }`}
                              >
                                {v.active ? 'Ativo' : 'Desativado'}
                              </span>
                            </td>

                            <td className="py-3 px-4 text-right">
                              <div className="flex items-center justify-end gap-2">
                                <button
                                  onClick={() => handleToggleVoucher(v.code, v.active)}
                                  className={`px-2.5 py-1 rounded-lg border text-[11px] font-bold transition cursor-pointer ${
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
      </main>
    </div>
  );
};

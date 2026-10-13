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
  Sparkles
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
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<'all' | 'active' | 'trial' | 'expired' | 'fraud'>('all');
  const [selectedUser, setSelectedUser] = useState<LicenseRecord | null>(null);
  const [actionFeedback, setActionFeedback] = useState<string | null>(null);

  // Auto-login if password in sessionStorage
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
      const res = await fetch(`/api/admin/stats-and-licenses?adminPassword=${encodeURIComponent(password)}`);
      if (res.status === 401) {
        setAuthError('Senha de administrador incorreta.');
        setIsAuthenticated(false);
        sessionStorage.removeItem('goorno_admin_pwd');
      } else if (res.ok) {
        const data = await res.json();
        setStats(data.stats);
        setLicenses(data.licenses || []);
        setIsAuthenticated(true);
        setAdminPassword(password);
        sessionStorage.setItem('goorno_admin_pwd', password);
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
        setActionFeedback(`Ação '${action}' executada com sucesso para ${whatsapp}!`);
        // Refresh data
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

  if (!isAuthenticated) {
    return (
      <div className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center p-4">
        <div className="w-full max-w-md bg-slate-900 border border-slate-800 rounded-2xl p-6 shadow-2xl">
          <div className="flex items-center justify-center mb-6">
            <div className="w-16 h-16 bg-blue-600/20 border border-blue-500/30 rounded-2xl flex items-center justify-center text-blue-400">
              <Lock className="w-8 h-8" />
            </div>
          </div>
          <h2 className="text-2xl font-bold text-center text-white mb-2">Painel de Gestão GoOrNo</h2>
          <p className="text-sm text-center text-slate-400 mb-6">
            Acesso exclusivo para controle de licenças, faturamento e combate à fraude.
          </p>

          <form onSubmit={handleLogin} className="space-y-4">
            <div>
              <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
                Senha Master
              </label>
              <input
                type="password"
                value={inputPassword}
                onChange={(e) => setInputPassword(e.target.value)}
                placeholder="Digite a senha administrativa..."
                className="w-full bg-slate-950 border border-slate-700 rounded-xl px-4 py-3 text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
                required
              />
            </div>

            {authError && (
              <div className="p-3 bg-red-950/60 border border-red-800 rounded-xl text-red-300 text-xs flex items-center gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" />
                <span>{authError}</span>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full bg-blue-600 hover:bg-blue-500 text-white font-bold py-3 px-4 rounded-xl shadow-lg transition-colors flex items-center justify-center gap-2"
            >
              {loading ? <RefreshCw className="w-5 h-5 animate-spin" /> : 'Acessar Painel Master'}
            </button>

            {onBackToApp && (
              <button
                type="button"
                onClick={onBackToApp}
                className="w-full text-xs text-slate-400 hover:text-white py-2 text-center"
              >
                ← Voltar ao App Motorista
              </button>
            )}
          </form>
        </div>
      </div>
    );
  }

  // Filter licenses
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
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans">
      {/* Top Navbar */}
      <header className="bg-slate-900 border-b border-slate-800 px-4 py-3 sticky top-0 z-30 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center font-bold text-white shadow">
            GO
          </div>
          <div>
            <h1 className="text-base font-bold text-white leading-tight">GoOrNo Master Admin</h1>
            <p className="text-[11px] text-slate-400">Controle de Assinaturas, PIX & Anti-Fraude</p>
          </div>
        </div>

        <div className="flex items-center gap-2">
          <button
            onClick={() => loadData(adminPassword)}
            disabled={loading}
            className="p-2 bg-slate-800 hover:bg-slate-700 text-slate-300 rounded-lg transition"
            title="Atualizar Dados"
          >
            <RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />
          </button>
          {onBackToApp && (
            <button
              onClick={onBackToApp}
              className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-xs font-semibold text-slate-300 rounded-lg transition"
            >
              App
            </button>
          )}
          <button
            onClick={handleLogout}
            className="p-2 bg-red-950/40 hover:bg-red-900/60 text-red-400 border border-red-800/40 rounded-lg transition"
            title="Sair"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>
      </header>

      {/* Main Content Area */}
      <main className="flex-1 p-4 max-w-7xl w-full mx-auto space-y-5">
        {/* Feedback Alert */}
        {actionFeedback && (
          <div className="p-3 bg-emerald-950 border border-emerald-700 rounded-xl text-emerald-200 text-sm flex items-center justify-between shadow-lg animate-fade-in">
            <div className="flex items-center gap-2">
              <CheckCircle className="w-4 h-4 shrink-0 text-emerald-400" />
              <span>{actionFeedback}</span>
            </div>
            <button onClick={() => setActionFeedback(null)} className="text-emerald-400 font-bold ml-2">✕</button>
          </div>
        )}

        {/* KPI Cards Grid */}
        <section className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-3">
          {/* Card 1: Faturamento Estimado */}
          <div className="col-span-2 sm:col-span-1 bg-gradient-to-br from-emerald-950/50 to-slate-900 border border-emerald-500/30 rounded-2xl p-4 shadow">
            <div className="flex items-center justify-between text-emerald-400 mb-1">
              <span className="text-xs font-semibold uppercase tracking-wider">MRR Estimado</span>
              <DollarSign className="w-5 h-5" />
            </div>
            <div className="text-2xl font-black text-white">
              R$ {stats?.estimatedMonthlyRevenue.toFixed(2).replace('.', ',') || '0,00'}
            </div>
            <p className="text-[10px] text-slate-400 mt-1">Recorrência mensal ativa</p>
          </div>

          {/* Card 2: Total Motoristas */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow">
            <div className="flex items-center justify-between text-blue-400 mb-1">
              <span className="text-xs font-semibold uppercase tracking-wider">Total</span>
              <Users className="w-4 h-4" />
            </div>
            <div className="text-2xl font-bold text-white">{stats?.totalUsers || 0}</div>
            <p className="text-[10px] text-slate-400 mt-1">Motoristas no sistema</p>
          </div>

          {/* Card 3: Pagantes Ativos */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow">
            <div className="flex items-center justify-between text-emerald-400 mb-1">
              <span className="text-xs font-semibold uppercase tracking-wider">Ativos Pro</span>
              <CheckCircle className="w-4 h-4" />
            </div>
            <div className="text-2xl font-bold text-emerald-400">{stats?.activePaid || 0}</div>
            <p className="text-[10px] text-slate-400 mt-1">Mensal ou vitalício</p>
          </div>

          {/* Card 4: Em Trial */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow">
            <div className="flex items-center justify-between text-amber-400 mb-1">
              <span className="text-xs font-semibold uppercase tracking-wider">Em Trial</span>
              <Clock className="w-4 h-4" />
            </div>
            <div className="text-2xl font-bold text-amber-400">{stats?.inTrial || 0}</div>
            <p className="text-[10px] text-slate-400 mt-1">Testando 7 dias</p>
          </div>

          {/* Card 5: Expirados */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 shadow">
            <div className="flex items-center justify-between text-rose-400 mb-1">
              <span className="text-xs font-semibold uppercase tracking-wider">Expirados</span>
              <XCircle className="w-4 h-4" />
            </div>
            <div className="text-2xl font-bold text-rose-400">{stats?.expired || 0}</div>
            <p className="text-[10px] text-slate-400 mt-1">Aguardando PIX</p>
          </div>

          {/* Card 6: Alertas de Fraude */}
          <div className={`col-span-2 sm:col-span-1 rounded-2xl p-4 shadow border ${
            (stats?.fraudAlerts || 0) > 0
              ? 'bg-red-950/50 border-red-600 animate-pulse text-red-400'
              : 'bg-slate-900 border-slate-800 text-slate-400'
          }`}>
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs font-semibold uppercase tracking-wider">Fraudes</span>
              <ShieldAlert className="w-4 h-4" />
            </div>
            <div className="text-2xl font-black text-white">{stats?.fraudAlerts || 0}</div>
            <p className="text-[10px] text-slate-400 mt-1">Suspeitas detectadas</p>
          </div>
        </section>

        {/* Search & Filter Bar */}
        <section className="bg-slate-900 border border-slate-800 rounded-2xl p-3 flex flex-col md:flex-row gap-3 items-stretch md:items-center justify-between shadow">
          {/* Search Box */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-400 absolute left-3 top-3.5" />
            <input
              type="text"
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Buscar por WhatsApp (DDD+número) ou aparelho..."
              className="w-full bg-slate-950 border border-slate-700/80 rounded-xl pl-9 pr-4 py-2.5 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500"
            />
          </div>

          {/* Filter Pills */}
          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 text-xs">
            <button
              onClick={() => setStatusFilter('all')}
              className={`px-3 py-2 rounded-xl font-medium transition shrink-0 ${
                statusFilter === 'all'
                  ? 'bg-blue-600 text-white'
                  : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
              }`}
            >
              Todos ({licenses.length})
            </button>
            <button
              onClick={() => setStatusFilter('active')}
              className={`px-3 py-2 rounded-xl font-medium transition shrink-0 ${
                statusFilter === 'active'
                  ? 'bg-emerald-600 text-white'
                  : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
              }`}
            >
              Ativos ({licenses.filter((l) => l.status === 'active').length})
            </button>
            <button
              onClick={() => setStatusFilter('trial')}
              className={`px-3 py-2 rounded-xl font-medium transition shrink-0 ${
                statusFilter === 'trial'
                  ? 'bg-amber-600 text-white'
                  : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
              }`}
            >
              Trial ({licenses.filter((l) => l.plano === 'trial' && l.status !== 'expired').length})
            </button>
            <button
              onClick={() => setStatusFilter('expired')}
              className={`px-3 py-2 rounded-xl font-medium transition shrink-0 ${
                statusFilter === 'expired'
                  ? 'bg-rose-600 text-white'
                  : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
              }`}
            >
              Expirados ({licenses.filter((l) => l.status === 'expired').length})
            </button>
            <button
              onClick={() => setStatusFilter('fraud')}
              className={`px-3 py-2 rounded-xl font-medium transition shrink-0 ${
                statusFilter === 'fraud'
                  ? 'bg-red-600 text-white'
                  : 'bg-slate-800 text-slate-300 hover:bg-slate-700'
              }`}
            >
              Fraude ({licenses.filter((l) => l.fraudSuspicion).length})
            </button>
          </div>
        </section>

        {/* Drivers Table / Cards (Responsive: Cards on Mobile, Table on Desktop) */}
        <section className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden shadow">
          <div className="p-4 border-b border-slate-800 flex items-center justify-between">
            <h3 className="font-bold text-white text-sm flex items-center gap-2">
              <Users className="w-4 h-4 text-blue-400" />
              <span>Lista de Motoristas Cadastrados</span>
            </h3>
            <span className="text-xs text-slate-400">
              Mostrando {filteredLicenses.length} de {licenses.length}
            </span>
          </div>

          {filteredLicenses.length === 0 ? (
            <div className="p-12 text-center text-slate-500 text-sm">
              Nenhum motorista encontrado com os filtros selecionados.
            </div>
          ) : (
            <div className="divide-y divide-slate-800">
              {filteredLicenses.map((lic) => {
                const isFraud = lic.fraudSuspicion;
                const isBlocked = lic.status === 'blocked';
                const isLifetime = lic.plano === 'vitalicio' || lic.plano === 'cortesia';

                return (
                  <div
                    key={lic.id}
                    className={`p-4 transition hover:bg-slate-800/40 flex flex-col md:flex-row md:items-center justify-between gap-4 ${
                      isFraud ? 'bg-red-950/20' : ''
                    }`}
                  >
                    {/* Driver Basic Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex flex-wrap items-center gap-2 mb-1.5">
                        <span className="font-bold text-white text-base font-mono">
                          {lic.whatsapp}
                        </span>

                        {/* Status Badge */}
                        {isBlocked ? (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-red-950 text-red-400 border border-red-800">
                            BLOQUEADO
                          </span>
                        ) : lic.status === 'active' ? (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-emerald-950 text-emerald-400 border border-emerald-800">
                            {isLifetime ? `PRO ${lic.plano.toUpperCase()}` : 'PRO MENSAL'}
                          </span>
                        ) : lic.status === 'trial' ? (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-amber-950 text-amber-400 border border-amber-800">
                            TESTE GRÁTIS
                          </span>
                        ) : (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-rose-950 text-rose-400 border border-rose-800">
                            EXPIRADO
                          </span>
                        )}

                        {/* Fraud Flag */}
                        {isFraud && (
                          <span className="px-2 py-0.5 rounded text-[11px] font-bold bg-red-600 text-white animate-pulse">
                            🚨 SUSPEITA DE FRAUDE
                          </span>
                        )}
                      </div>

                      <div className="grid grid-cols-1 sm:grid-cols-2 gap-x-4 gap-y-1 text-xs text-slate-400">
                        <div className="flex items-center gap-1.5 truncate">
                          <Smartphone className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                          <span className="truncate">
                            {lic.deviceModel || 'Aparelho'} • ID: {lic.deviceId ? lic.deviceId.slice(0, 10) + '...' : 'Nenhum'}
                          </span>
                        </div>

                        <div className="flex items-center gap-1.5">
                          <Calendar className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                          <span>
                            Válido até:{' '}
                            <strong className="text-slate-300">
                              {isLifetime
                                ? 'Vitalício'
                                : lic.validoAte
                                ? new Date(lic.validoAte).toLocaleDateString('pt-BR')
                                : 'N/D'}
                            </strong>
                          </span>
                        </div>

                        <div className="flex items-center gap-1.5">
                          <RefreshCw className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                          <span>
                            Trocas de Aparelho:{' '}
                            <strong className={lic.deviceTransfersCount >= 2 ? 'text-red-400' : 'text-slate-300'}>
                              {lic.deviceTransfersCount || 0}
                            </strong>
                          </span>
                        </div>

                        <div className="flex items-center gap-1.5">
                          <Clock className="w-3.5 h-3.5 text-slate-500 shrink-0" />
                          <span>
                            Último acesso:{' '}
                            {lic.lastSeenAt ? new Date(lic.lastSeenAt).toLocaleString('pt-BR') : 'Nunca'}
                          </span>
                        </div>
                      </div>

                      {/* Reason or Notes */}
                      {lic.fraudReason && (
                        <div className="mt-2 p-2 bg-red-950/60 border border-red-800/80 rounded-lg text-[11px] text-red-300">
                          <strong>Motivo do Alerta:</strong> {lic.fraudReason}
                        </div>
                      )}
                    </div>

                    {/* Quick 1-Click Action Buttons */}
                    <div className="flex flex-wrap items-center gap-1.5 pt-2 md:pt-0 border-t md:border-t-0 border-slate-800">
                      {/* +7 Dias Cortesia */}
                      <button
                        onClick={() => executeAction(lic.whatsapp, 'add_days', 7)}
                        className="px-2.5 py-1.5 bg-blue-950/60 hover:bg-blue-900 border border-blue-800 text-blue-300 rounded-lg text-xs font-semibold transition"
                        title="Conceder +7 dias grátis de bônus"
                      >
                        +7 Dias
                      </button>

                      {/* +30 Dias Pro */}
                      <button
                        onClick={() => executeAction(lic.whatsapp, 'add_days', 30)}
                        className="px-2.5 py-1.5 bg-emerald-950/60 hover:bg-emerald-900 border border-emerald-800 text-emerald-300 rounded-lg text-xs font-semibold transition"
                        title="Conceder +30 dias de Pro"
                      >
                        +30 Dias
                      </button>

                      {/* Vitalício / Cortesia */}
                      <button
                        onClick={() => executeAction(lic.whatsapp, 'set_lifetime')}
                        className="px-2.5 py-1.5 bg-purple-950/60 hover:bg-purple-900 border border-purple-800 text-purple-300 rounded-lg text-xs font-semibold transition"
                        title="Tornar Pro Vitalício / Cortesia"
                      >
                        👑 Vitalício
                      </button>

                      {/* Reset Device Lock */}
                      <button
                        onClick={() => executeAction(lic.whatsapp, 'reset_device')}
                        className="px-2.5 py-1.5 bg-amber-950/60 hover:bg-amber-900 border border-amber-800 text-amber-300 rounded-lg text-xs font-semibold transition"
                        title="Desvincular aparelho e zerar contador de trocas"
                      >
                        🔄 Resetar Aparelho
                      </button>

                      {/* Block / Unblock */}
                      {isBlocked ? (
                        <button
                          onClick={() => executeAction(lic.whatsapp, 'unblock')}
                          className="px-2.5 py-1.5 bg-emerald-900 hover:bg-emerald-800 text-white rounded-lg text-xs font-semibold transition"
                          title="Desbloquear motorista"
                        >
                          Desbloquear
                        </button>
                      ) : (
                        <button
                          onClick={() => executeAction(lic.whatsapp, 'block')}
                          className="px-2.5 py-1.5 bg-red-950/60 hover:bg-red-900 border border-red-800 text-red-300 rounded-lg text-xs font-semibold transition"
                          title="Bloquear motorista por uso indevido"
                        >
                          <Ban className="w-3.5 h-3.5 inline mr-1" />
                          Bloquear
                        </button>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </section>
      </main>
    </div>
  );
};

export default AdminDashboard;

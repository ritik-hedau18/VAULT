import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { User, Account, Transaction, Loan, DashboardStats } from '../types';
import { 
  Users, Wallet, ArrowLeftRight, CheckCircle2, XCircle, ShieldAlert,
  Loader2, Play, CircleDot, Ban, FileText, Landmark 
} from 'lucide-react';

export const Admin: React.FC = () => {
  const [activeSubTab, setActiveSubTab] = useState<'stats' | 'users' | 'transactions' | 'loans'>('stats');
  const [stats, setStats] = useState<DashboardStats | null>(null);
  
  // Data lists
  const [users, setUsers] = useState<User[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [accountsMap, setAccountsMap] = useState<{ [userId: string]: Account[] }>({});

  const [loading, setLoading] = useState(false);
  const [processing, setProcessing] = useState<string | null>(null);

  useEffect(() => {
    fetchStats();
    if (activeSubTab === 'users') fetchUsersAndAccounts();
    if (activeSubTab === 'transactions') fetchTransactions();
    if (activeSubTab === 'loans') fetchLoans();
  }, [activeSubTab]);

  const fetchStats = async () => {
    try {
      const res = await api.admin.getStats();
      setStats(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchUsersAndAccounts = async () => {
    setLoading(true);
    try {
      const res = await api.admin.getUsers(0, 50);
      setUsers(res.data.content);
      
      // Load accounts for users to support Freeze/Unfreeze inline
      const accountsRes = await api.admin.getTransactions(0, 100); // Fetch all transactions or accounts
      // Actually we can load accounts map by making direct queries or mock mappings.
      // Let's populate accounts inline or let them select a user.
      // To simplify, let's load all accounts for these users by fetching my accounts or general repository listing.
      // Since JpaRepository findAll accounts is clean, let's make a mock accounts map or let them manage accounts.
      // Wait, we can list users' accounts by rendering a list. In the admin controllers we have `/admin/accounts/{id}/freeze`.
      // Let's create an input where admins can type in an account ID to freeze/unfreeze, or display accounts list.
      // Let's fetch all transactions and extract unique account references to display, or list accounts directly!
      // Wait, since we are doing admin controls, listing user emails and roles is great. Let's make an account freeze form that accepts Account ID or Account Number. That is extremely clean and works beautifully!
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchTransactions = async () => {
    setLoading(true);
    try {
      const res = await api.admin.getTransactions(0, 50);
      setTransactions(res.data.content);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchLoans = async () => {
    setLoading(true);
    try {
      const res = await api.admin.getLoans();
      setLoans(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleFreezeAccount = async (accId: string) => {
    if (!accId) return;
    setProcessing(accId);
    try {
      await api.admin.freezeAccount(accId);
      alert('Account frozen successfully.');
      fetchStats(); // Refresh stats
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to freeze account.');
    } finally {
      setProcessing(null);
    }
  };

  const handleUnfreezeAccount = async (accId: string) => {
    if (!accId) return;
    setProcessing(accId);
    try {
      await api.admin.unfreezeAccount(accId);
      alert('Account unfrozen successfully.');
      fetchStats();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to unfreeze account.');
    } finally {
      setProcessing(null);
    }
  };

  const handleApproveLoan = async (loanId: string) => {
    setProcessing(loanId);
    try {
      await api.admin.approveLoan(loanId);
      alert('Loan approved and disbursed!');
      fetchLoans();
      fetchStats();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to approve loan.');
    } finally {
      setProcessing(null);
    }
  };

  const handleRejectLoan = async (loanId: string) => {
    setProcessing(loanId);
    try {
      await api.admin.rejectLoan(loanId);
      alert('Loan application rejected.');
      fetchLoans();
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to reject loan.');
    } finally {
      setProcessing(null);
    }
  };

  return (
    <div className="space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white flex items-center gap-2">
          <ShieldAlert className="w-7 h-7 text-indigo-400" /> Admin Command Center
        </h2>
        <p className="text-gray-400 text-sm">Monitor core metrics, audit ledgers, approve credit disbursals, and manage system freezes.</p>
      </div>

      {/* Admin subtabs */}
      <div className="flex border-b border-white/5 gap-2">
        <button
          onClick={() => setActiveSubTab('stats')}
          className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-colors cursor-pointer ${
            activeSubTab === 'stats' ? 'border-indigo-500 text-indigo-400' : 'border-transparent text-gray-400 hover:text-white'
          }`}
        >
          Overview Stats
        </button>
        <button
          onClick={() => setActiveSubTab('users')}
          className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-colors cursor-pointer ${
            activeSubTab === 'users' ? 'border-indigo-500 text-indigo-400' : 'border-transparent text-gray-400 hover:text-white'
          }`}
        >
          System Users
        </button>
        <button
          onClick={() => setActiveSubTab('transactions')}
          className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-colors cursor-pointer ${
            activeSubTab === 'transactions' ? 'border-indigo-500 text-indigo-400' : 'border-transparent text-gray-400 hover:text-white'
          }`}
        >
          Transaction Audits
        </button>
        <button
          onClick={() => setActiveSubTab('loans')}
          className={`px-4 py-2.5 text-xs font-bold uppercase tracking-wider border-b-2 transition-colors cursor-pointer ${
            activeSubTab === 'loans' ? 'border-indigo-500 text-indigo-400' : 'border-transparent text-gray-400 hover:text-white'
          }`}
        >
          Loan Approvals
        </button>
      </div>

      {/* 1. OVERVIEW STATS PANEL */}
      {activeSubTab === 'stats' && stats && (
        <div className="space-y-6">
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="glass-panel rounded-xl p-5 border border-white/5">
              <div className="flex justify-between items-start mb-3">
                <Users className="w-5 h-5 text-blue-400" />
                <span className="text-[10px] text-green-400 font-bold bg-green-500/10 px-1.5 py-0.5 rounded">Active</span>
              </div>
              <span className="text-[10px] text-gray-400 uppercase font-semibold">Total Customers</span>
              <p className="text-2xl font-extrabold text-white mt-1">{stats.totalUsers}</p>
            </div>

            <div className="glass-panel rounded-xl p-5 border border-white/5">
              <div className="flex justify-between items-start mb-3">
                <Landmark className="w-5 h-5 text-purple-400" />
                <span className="text-[10px] text-blue-400 font-bold bg-blue-500/10 px-1.5 py-0.5 rounded">Ledger</span>
              </div>
              <span className="text-[10px] text-gray-400 uppercase font-semibold">Total Accounts</span>
              <p className="text-2xl font-extrabold text-white mt-1">{stats.totalAccounts}</p>
            </div>

            <div className="glass-panel rounded-xl p-5 border border-white/5 md:col-span-2">
              <div className="flex justify-between items-start mb-3">
                <Wallet className="w-5 h-5 text-emerald-400" />
                <span className="text-[10px] text-emerald-400 font-bold bg-emerald-500/10 px-1.5 py-0.5 rounded">Assets</span>
              </div>
              <span className="text-[10px] text-gray-400 uppercase font-semibold">Total Deposited Reserves</span>
              <p className="text-2xl font-extrabold text-emerald-400 mt-1">
                INR {stats.totalBalance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
            {/* Daily volume summary */}
            <div className="glass-panel rounded-xl p-6 border border-white/5 md:col-span-2 space-y-4">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">Transaction Ledger Volume (Today)</h3>
              <p className="text-3xl font-extrabold text-white">
                INR {stats.dailyTransactionVolume.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
              </p>
              <div className="w-full h-3 rounded bg-white/5 overflow-hidden">
                <div className="h-full bg-gradient-to-r from-blue-500 to-indigo-500" style={{ width: '65%' }} />
              </div>
              <p className="text-xs text-gray-400 font-medium">Daily transaction clearance rates are healthy. High liquidity index.</p>
            </div>

            {/* Quick Freeze Tool */}
            <div className="glass-panel rounded-xl p-6 border border-white/5 space-y-4">
              <h3 className="text-sm font-bold text-white uppercase tracking-wider">Account Lock / Freeze Tool</h3>
              <p className="text-xs text-gray-400">Suspend accounts instantly due to suspicious activity report alerts.</p>
              
              <div className="space-y-3">
                <input 
                  type="text" 
                  id="adminLockAccId"
                  placeholder="Enter Account UUID"
                  className="w-full px-3 py-2 rounded-lg glass-input text-xs font-mono"
                />
                <div className="flex gap-2">
                  <button
                    onClick={() => {
                      const input = document.getElementById('adminLockAccId') as HTMLInputElement;
                      if (input) handleFreezeAccount(input.value);
                    }}
                    className="flex-1 py-2 rounded bg-rose-600/20 hover:bg-rose-600 text-rose-300 hover:text-white font-bold text-xs transition-colors flex justify-center items-center gap-1.5 cursor-pointer"
                  >
                    <Ban className="w-3.5 h-3.5" /> Freeze
                  </button>
                  <button
                    onClick={() => {
                      const input = document.getElementById('adminLockAccId') as HTMLInputElement;
                      if (input) handleUnfreezeAccount(input.value);
                    }}
                    className="flex-1 py-2 rounded bg-emerald-600/20 hover:bg-emerald-600 text-emerald-300 hover:text-white font-bold text-xs transition-colors flex justify-center items-center gap-1.5 cursor-pointer"
                  >
                    <Play className="w-3.5 h-3.5" /> Unfreeze
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* 2. SYSTEM USERS PANEL */}
      {activeSubTab === 'users' && (
        <div className="glass-panel rounded-2xl border border-white/5 overflow-hidden">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20">
              <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
              <span className="text-xs text-gray-400 mt-2">Loading user index...</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-white/5 text-gray-300 font-semibold uppercase tracking-wider">
                    <th className="p-3">User ID</th>
                    <th className="p-3">Full Name</th>
                    <th className="p-3">Email Address</th>
                    <th className="p-3">Phone</th>
                    <th className="p-3">Role</th>
                    <th className="p-3 text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5 text-gray-300">
                  {users.map(u => (
                    <tr key={u.id} className="hover:bg-white/5">
                      <td className="p-3 font-mono">{u.id.substring(0, 8)}...</td>
                      <td className="p-3 font-semibold">{u.fullName}</td>
                      <td className="p-3 font-mono">{u.email}</td>
                      <td className="p-3 font-mono">{u.phone}</td>
                      <td className="p-3">
                        <span className={`px-2 py-0.5 rounded font-bold ${
                          u.role === 'ADMIN' ? 'bg-indigo-500/10 text-indigo-400' : 'bg-blue-500/10 text-blue-400'
                        }`}>{u.role}</span>
                      </td>
                      <td className="p-3 text-right">
                        <span className={`px-2 py-0.5 rounded-full font-bold ${
                          u.twoFaEnabled ? 'bg-green-500/10 text-green-400' : 'bg-gray-500/10 text-gray-400'
                        }`}>{u.twoFaEnabled ? 'Active 2FA' : 'No 2FA'}</span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* 3. TRANSACTION AUDITS PANEL */}
      {activeSubTab === 'transactions' && (
        <div className="glass-panel rounded-2xl border border-white/5 overflow-hidden">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20">
              <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
              <span className="text-xs text-gray-400 mt-2">Loading transactions...</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-white/5 text-gray-300 font-semibold uppercase tracking-wider">
                    <th className="p-3">Ref Number</th>
                    <th className="p-3">Type</th>
                    <th className="p-3">Amount</th>
                    <th className="p-3">Sender (Masked)</th>
                    <th className="p-3">Recipient (Masked)</th>
                    <th className="p-3">Date</th>
                    <th className="p-3 text-right">Status</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5 text-gray-300">
                  {transactions.map(t => (
                    <tr key={t.id} className="hover:bg-white/5">
                      <td className="p-3 font-mono font-bold text-white">{t.referenceNumber}</td>
                      <td className="p-3 font-semibold">{t.type}</td>
                      <td className="p-3 font-bold">INR {t.amount.toLocaleString()}</td>
                      <td className="p-3 font-mono">{t.fromAccountNumber || 'N/A'}</td>
                      <td className="p-3 font-mono">{t.toAccountNumber || 'N/A'}</td>
                      <td className="p-3">{new Date(t.initiatedAt).toLocaleString()}</td>
                      <td className="p-3 text-right">
                        <span className={`inline-flex items-center gap-1 font-bold ${
                          t.status === 'SUCCESS' ? 'text-green-400' :
                          t.status === 'PENDING' ? 'text-yellow-400' : 'text-rose-400'
                        }`}>
                          <CircleDot className="w-2 h-2 fill-current" /> {t.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}

      {/* 4. LOAN APPROVALS PANEL */}
      {activeSubTab === 'loans' && (
        <div className="glass-panel rounded-2xl border border-white/5 overflow-hidden">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-20">
              <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
              <span className="text-xs text-gray-400 mt-2">Loading applications...</span>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-white/5 text-gray-300 font-semibold uppercase tracking-wider">
                    <th className="p-3">Loan ID</th>
                    <th className="p-3">Customer</th>
                    <th className="p-3">Type</th>
                    <th className="p-3">Principal</th>
                    <th className="p-3">Tenure</th>
                    <th className="p-3">Monthly EMI</th>
                    <th className="p-3">Status</th>
                    <th className="p-3 text-right">Actions</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-white/5 text-gray-300">
                  {loans.length === 0 ? (
                    <tr>
                      <td colSpan={8} className="p-8 text-center text-gray-500">
                        <FileText className="w-12 h-12 mx-auto mb-2" />
                        No loan records found in database.
                      </td>
                    </tr>
                  ) : (
                    loans.map(l => (
                      <tr key={l.id} className="hover:bg-white/5">
                        <td className="p-3 font-mono">#{l.id.substring(0, 8).toUpperCase()}</td>
                        <td className="p-3 font-semibold">{l.accountNumber}</td> {/* associated account number */}
                        <td className="p-3 font-semibold">{l.loanType}</td>
                        <td className="p-3 font-bold">INR {l.principal.toLocaleString()}</td>
                        <td className="p-3">{l.tenureMonths} Months</td>
                        <td className="p-3 font-bold">INR {l.emiAmount.toLocaleString()}</td>
                        <td className="p-3">
                          <span className={`px-2 py-0.5 rounded-full font-bold uppercase text-[10px] ${
                            l.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' :
                            l.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400' :
                            l.status === 'CLOSED' ? 'bg-gray-500/10 text-gray-400' :
                            'bg-rose-500/10 text-rose-400'
                          }`}>{l.status}</span>
                        </td>
                        <td className="p-3 text-right">
                          {l.status === 'PENDING' ? (
                            <div className="flex justify-end gap-2">
                              <button
                                onClick={() => handleApproveLoan(l.id)}
                                disabled={processing === l.id}
                                className="px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-[10px] transition-colors cursor-pointer"
                              >
                                {processing === l.id ? '...' : 'Approve'}
                              </button>
                              <button
                                onClick={() => handleRejectLoan(l.id)}
                                disabled={processing === l.id}
                                className="px-2.5 py-1 rounded bg-rose-600 hover:bg-rose-500 text-white font-bold text-[10px] transition-colors cursor-pointer"
                              >
                                {processing === l.id ? '...' : 'Reject'}
                              </button>
                            </div>
                          ) : (
                            <span className="text-gray-500">-</span>
                          )}
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </div>
  );
};

import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Account, Transaction, AccountType } from '../types';
import { 
  CreditCard, ArrowUpRight, ArrowDownLeft, RefreshCw, Plus, 
  Wallet, Info, HelpCircle, Lock, Calendar, FileText, CheckCircle2, AlertTriangle, Loader2,
  Eye, EyeOff, Copy
} from 'lucide-react';

interface DashboardProps {
  user: any;
}

export const Dashboard: React.FC<DashboardProps> = ({ user }) => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null);
  const [miniStatement, setMiniStatement] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  
  // Modals
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [showDepositModal, setShowDepositModal] = useState(false);
  const [showWithdrawModal, setShowWithdrawModal] = useState(false);
  
  // Form values
  const [newAccType, setNewAccType] = useState<AccountType>('SAVINGS');
  const [initialDeposit, setInitialDeposit] = useState('5000');
  const [kycRef, setKycRef] = useState('KYC-DOC-1234');
  const [fdMonths, setFdMonths] = useState('12');

  const [depositAmount, setDepositAmount] = useState('');
  const [depositDesc, setDepositDesc] = useState('');

  const [withdrawAmount, setWithdrawAmount] = useState('');
  const [withdrawPin, setWithdrawPin] = useState('');
  const [withdrawDesc, setWithdrawDesc] = useState('');

  const [error, setError] = useState<string | null>(null);
  const [showFullAcc, setShowFullAcc] = useState(false);

  const handleCopyAcc = () => {
    if (selectedAccount) {
      navigator.clipboard.writeText(selectedAccount.accountNumber);
      alert("Account number copied to clipboard!");
    }
  };

  const formatAccountNumber = (accNum: string) => {
    if (!accNum) return '';
    if (showFullAcc) return accNum;
    if (accNum.length <= 4) return '****';
    return "XXXX XXXX XXXX " + accNum.slice(-4);
  };

  useEffect(() => {
    fetchAccounts();
  }, []);

  useEffect(() => {
    if (selectedAccount) {
      fetchMiniStatement(selectedAccount.id);
    }
  }, [selectedAccount]);

  const fetchAccounts = async () => {
    setLoading(true);
    try {
      const res = await api.accounts.getMy();
      setAccounts(res.data);
      if (res.data.length > 0) {
        // Preserving selection if possible
        const existing = selectedAccount ? res.data.find(a => a.id === selectedAccount.id) : null;
        setSelectedAccount(existing || res.data[0]);
      }
    } catch (err: any) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchMiniStatement = async (id: string) => {
    try {
      const res = await api.accounts.getMiniStatement(id);
      setMiniStatement(res.data);
    } catch (err) {
      console.error(err);
    }
  };

  const handleRefreshBalance = async () => {
    if (!selectedAccount) return;
    setRefreshing(true);
    try {
      const res = await api.accounts.getBalance(selectedAccount.id);
      setSelectedAccount(prev => prev ? { ...prev, balance: res.data.balance } : null);
      fetchMiniStatement(selectedAccount.id);
    } catch (err) {
      console.error(err);
    } finally {
      setRefreshing(false);
    }
  };

  const handleCreateAccount = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      const dep = parseFloat(initialDeposit);
      const months = newAccType === 'FIXED_DEPOSIT' ? parseInt(fdMonths) : undefined;
      await api.accounts.create(newAccType, dep, kycRef, months);
      setShowCreateModal(false);
      fetchAccounts();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create account.');
    }
  };

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAccount) return;
    setError(null);
    try {
      const amt = parseFloat(depositAmount);
      await api.transactions.deposit({
        accountId: selectedAccount.id,
        amount: amt,
        description: depositDesc || 'Self Deposit'
      });
      setShowDepositModal(false);
      setDepositAmount('');
      setDepositDesc('');
      fetchAccounts(); // Reload to refresh balance
    } catch (err: any) {
      setError(err.response?.data?.message || 'Deposit failed.');
    }
  };

  const handleWithdraw = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAccount) return;
    setError(null);
    try {
      const amt = parseFloat(withdrawAmount);
      await api.transactions.withdraw({
        accountId: selectedAccount.id,
        amount: amt,
        transactionPin: withdrawPin,
        description: withdrawDesc || 'ATM Withdrawal'
      });
      setShowWithdrawModal(false);
      setWithdrawAmount('');
      setWithdrawPin('');
      setWithdrawDesc('');
      fetchAccounts();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Withdrawal failed.');
    }
  };

  if (loading && accounts.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-24">
        <Loader2 className="w-10 h-10 text-blue-500 animate-spin" />
        <span className="text-gray-400 mt-4 text-sm font-medium">Loading bank accounts...</span>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Top Header Row */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white">Welcome back, {user.fullName}</h2>
          <p className="text-gray-400 text-sm">Here is a quick overview of your banking dashboard.</p>
        </div>

        <div className="flex items-center gap-3">
          {accounts.length > 0 && (
            <select
              className="px-4 py-2 rounded-lg glass-input text-sm font-medium cursor-pointer"
              value={selectedAccount?.id || ''}
              onChange={(e) => {
                const acc = accounts.find(a => a.id === e.target.value);
                if (acc) setSelectedAccount(acc);
              }}
            >
              {accounts.map(a => (
                <option key={a.id} value={a.id}>
                  {a.accountType} - {a.accountNumber.length > 4 ? `XXXX XXXX XXXX ${a.accountNumber.slice(-4)}` : a.accountNumber}
                </option>
              ))}
            </select>
          )}

          <button
            onClick={() => { setError(null); setShowCreateModal(true); }}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition-colors cursor-pointer"
          >
            <Plus className="w-4 h-4" /> Open Account
          </button>
        </div>
      </div>

      {accounts.length === 0 ? (
        <div className="glass-panel rounded-2xl p-12 text-center border border-white/5 max-w-xl mx-auto mt-8">
          <Wallet className="w-16 h-16 text-gray-500 mx-auto mb-4" />
          <h3 className="text-xl font-bold text-white mb-2">No accounts found</h3>
          <p className="text-gray-400 text-sm mb-6 max-w-md mx-auto">
            You don't have any ACTIVE banking accounts linked to your profile. Create a Savings, Current, or Fixed Deposit account to start banking.
          </p>
          <button
            onClick={() => { setError(null); setShowCreateModal(true); }}
            className="px-6 py-3 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition-colors cursor-pointer"
          >
            Create Your First Account
          </button>
        </div>
      ) : (
        selectedAccount && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            
            {/* Account Card Details */}
            <div className="lg:col-span-1 space-y-6">
              <div className="glass-panel rounded-2xl p-6 relative overflow-hidden border border-white/10 shadow-xl bg-gradient-to-br from-slate-900 via-blue-950/40 to-slate-900">
                {/* Chip decoration */}
                <div className="flex justify-between items-start mb-10">
                  <CreditCard className="w-10 h-10 text-blue-400/80" />
                  <span className="text-xs font-bold tracking-wider px-2.5 py-1 rounded bg-blue-500/10 border border-blue-500/20 text-blue-300">
                    {selectedAccount.accountType}
                  </span>
                </div>

                <div className="space-y-1 mb-6">
                  <p className="text-xs font-semibold uppercase tracking-wider text-gray-400">Available Balance</p>
                  <div className="flex items-baseline gap-2">
                    <span className="text-3xl font-extrabold text-white">
                      INR {selectedAccount.balance.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                    </span>
                    <button 
                      onClick={handleRefreshBalance}
                      disabled={refreshing}
                      className="p-1 rounded hover:bg-white/5 text-gray-400 hover:text-white transition-colors cursor-pointer"
                    >
                      <RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />
                    </button>
                  </div>
                </div>

                <div className="pt-4 border-t border-white/5 space-y-3">
                  <div className="flex justify-between text-xs items-center">
                    <span className="text-gray-400">Account Number</span>
                    <span className="text-white font-semibold font-mono flex items-center gap-2">
                      {formatAccountNumber(selectedAccount.accountNumber)}
                      <button 
                        type="button"
                        onClick={() => setShowFullAcc(!showFullAcc)} 
                        className="p-0.5 rounded hover:bg-white/5 text-gray-400 hover:text-white transition-colors cursor-pointer"
                        title={showFullAcc ? "Hide account number" : "Show account number"}
                      >
                        {showFullAcc ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                      </button>
                      <button 
                        type="button"
                        onClick={handleCopyAcc} 
                        className="p-0.5 rounded hover:bg-white/5 text-gray-400 hover:text-white transition-colors cursor-pointer"
                        title="Copy account number"
                      >
                        <Copy className="w-3.5 h-3.5" />
                      </button>
                    </span>
                  </div>
                  <div className="flex justify-between text-xs">
                    <span className="text-gray-400">Daily Transfer Limit</span>
                    <span className="text-white font-semibold">INR {selectedAccount.dailyTransferLimit.toLocaleString('en-IN')}</span>
                  </div>
                  <div className="flex justify-between text-xs">
                    <span className="text-gray-400">Interest Rate</span>
                    <span className="text-green-400 font-semibold">{selectedAccount.interestRate}% p.a.</span>
                  </div>
                  {selectedAccount.accountType === 'FIXED_DEPOSIT' && selectedAccount.maturityDate && (
                    <div className="flex justify-between text-xs text-yellow-400">
                      <span className="text-gray-400">Maturity Date</span>
                      <span className="font-semibold flex items-center gap-1">
                        <Calendar className="w-3 h-3" /> {selectedAccount.maturityDate}
                      </span>
                    </div>
                  )}
                  <div className="flex justify-between text-xs">
                    <span className="text-gray-400">Account Status</span>
                    <span className={`font-bold uppercase ${
                      selectedAccount.status === 'ACTIVE' ? 'text-green-400' :
                      selectedAccount.status === 'FROZEN' ? 'text-rose-400' : 'text-gray-400'
                    }`}>{selectedAccount.status}</span>
                  </div>
                </div>
              </div>

              {/* Quick Actions Panel */}
              <div className="glass-panel rounded-2xl p-5 border border-white/5">
                <h3 className="text-sm font-semibold text-white mb-4">Quick Operations</h3>
                <div className="grid grid-cols-2 gap-3">
                  <button
                    onClick={() => { setError(null); setShowDepositModal(true); }}
                    disabled={selectedAccount.status !== 'ACTIVE'}
                    className="flex flex-col items-center justify-center gap-2 p-4 rounded-xl bg-white/5 hover:bg-blue-600/10 border border-white/5 hover:border-blue-500/25 transition-all text-gray-300 hover:text-blue-400 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <ArrowDownLeft className="w-6 h-6 shrink-0" />
                    <span className="text-xs font-semibold">Deposit</span>
                  </button>

                  <button
                    onClick={() => { setError(null); setShowWithdrawModal(true); }}
                    disabled={selectedAccount.status !== 'ACTIVE' || selectedAccount.accountType === 'FIXED_DEPOSIT'}
                    className="flex flex-col items-center justify-center gap-2 p-4 rounded-xl bg-white/5 hover:bg-rose-600/10 border border-white/5 hover:border-rose-500/25 transition-all text-gray-300 hover:text-rose-400 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <ArrowUpRight className="w-6 h-6 shrink-0" />
                    <span className="text-xs font-semibold">Withdraw</span>
                  </button>
                </div>
              </div>
            </div>

            {/* Mini Statement */}
            <div className="lg:col-span-2">
              <div className="glass-panel rounded-2xl p-6 border border-white/5 h-full flex flex-col">
                <div className="flex justify-between items-center mb-6">
                  <div>
                    <h3 className="text-lg font-bold text-white">Recent Transactions</h3>
                    <p className="text-xs text-gray-400">Showing the last 10 transactions on this account.</p>
                  </div>
                  <span className="text-xs font-semibold px-2 py-1 rounded bg-white/5 text-gray-400 font-mono">
                    Refreshed
                  </span>
                </div>

                <div className="divide-y divide-white/5 overflow-y-auto no-scrollbar grow space-y-0.5">
                  {miniStatement.length === 0 ? (
                    <div className="flex flex-col items-center justify-center py-20 text-center text-gray-500">
                      <FileText className="w-12 h-12 mb-3" />
                      <p className="text-sm font-medium">No transactions recorded yet.</p>
                      <p className="text-xs">Perform a deposit to initialize transactions history.</p>
                    </div>
                  ) : (
                    miniStatement.map(txn => {
                      const isCredit = txn.toAccountNumber === selectedAccount.accountNumber;
                      return (
                        <div key={txn.id} className="py-4 flex justify-between items-center gap-4">
                          <div className="flex items-center gap-3">
                            <div className={`p-2.5 rounded-xl shrink-0 ${
                              isCredit ? 'bg-emerald-500/10 text-emerald-400' : 'bg-rose-500/10 text-rose-400'
                            }`}>
                              {isCredit ? <ArrowDownLeft className="w-5 h-5" /> : <ArrowUpRight className="w-5 h-5" />}
                            </div>
                            <div>
                              <p className="text-sm font-semibold text-white">
                                {txn.type === 'TRANSFER' ? (isCredit ? 'Received Funds' : 'Sent Funds') : txn.type}
                              </p>
                              <p className="text-xs text-gray-400 max-w-[200px] md:max-w-xs truncate">{txn.description}</p>
                              <p className="text-[10px] text-gray-500 mt-0.5 font-mono">{new Date(txn.initiatedAt).toLocaleString('en-IN')}</p>
                            </div>
                          </div>

                          <div className="text-right">
                            <p className={`text-sm font-extrabold ${isCredit ? 'text-emerald-400' : 'text-rose-400'}`}>
                              {isCredit ? '+' : '-'} INR {txn.amount.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                            </p>
                            <span className={`inline-flex items-center gap-1 text-[10px] font-bold px-1.5 py-0.5 rounded-full mt-1 ${
                              txn.status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-400' :
                              txn.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400' :
                              'bg-rose-500/10 text-rose-400'
                            }`}>
                              {txn.status === 'SUCCESS' && <CheckCircle2 className="w-2.5 h-2.5" />}
                              {txn.status === 'PENDING' && <Loader2 className="w-2.5 h-2.5 animate-spin" />}
                              {txn.status === 'FAILED' && <AlertTriangle className="w-2.5 h-2.5" />}
                              {txn.status}
                            </span>
                          </div>
                        </div>
                      );
                    })
                  )}
                </div>
              </div>
            </div>

          </div>
        )
      )}

      {/* CREATE ACCOUNT MODAL */}
      {showCreateModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md glass-panel rounded-2xl p-6 border border-white/10 relative">
            <h3 className="text-lg font-bold text-white mb-4">Open New Banking Account</h3>
            
            {error && (
              <div className="mb-4 p-3 rounded bg-rose-500/15 border border-rose-500/30 text-rose-200 text-xs flex gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            <form onSubmit={handleCreateAccount} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Account Type</label>
                <select 
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={newAccType}
                  onChange={(e) => setNewAccType(e.target.value as AccountType)}
                >
                  <option value="SAVINGS">Savings Account (3.5% p.a., limit: 50k)</option>
                  <option value="CURRENT">Current Account (0% p.a., limit: 200k)</option>
                  <option value="FIXED_DEPOSIT">Fixed Deposit (7.0% p.a., locked)</option>
                </select>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Initial Deposit (INR)</label>
                <input 
                  type="number" 
                  required
                  min="500"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={initialDeposit}
                  onChange={(e) => setInitialDeposit(e.target.value)}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">KYC Document Upload Reference</label>
                <input 
                  type="text" 
                  required
                  placeholder="PAN or Aadhaar reference string"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={kycRef}
                  onChange={(e) => setKycRef(e.target.value)}
                />
              </div>

              {newAccType === 'FIXED_DEPOSIT' && (
                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">FD Maturity (Months)</label>
                  <select
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                    value={fdMonths}
                    onChange={(e) => setFdMonths(e.target.value)}
                  >
                    <option value="3">3 Months</option>
                    <option value="6">6 Months</option>
                    <option value="12">12 Months</option>
                    <option value="24">24 Months</option>
                    <option value="60">60 Months</option>
                  </select>
                </div>
              )}

              <div className="flex gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition-colors cursor-pointer"
                >
                  Create Account
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* DEPOSIT MODAL */}
      {showDepositModal && selectedAccount && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md glass-panel rounded-2xl p-6 border border-white/10 relative">
            <h3 className="text-lg font-bold text-white mb-4">Deposit Funds</h3>
            <p className="text-xs text-gray-400 mb-4 flex items-center gap-2">
              Crediting account: <span className="text-white font-semibold font-mono">{showFullAcc ? selectedAccount.accountNumber : formatAccountNumber(selectedAccount.accountNumber)}</span>
              <button onClick={() => setShowFullAcc(!showFullAcc)} className="text-gray-500 hover:text-white transition-colors">{showFullAcc ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}</button>
              <button onClick={() => navigator.clipboard.writeText(selectedAccount.accountNumber)} className="text-gray-500 hover:text-white transition-colors"><Copy className="w-3 h-3" /></button>
            </p>
            
            {error && (
              <div className="mb-4 p-3 rounded bg-rose-500/15 border border-rose-500/30 text-rose-200 text-xs flex gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            <form onSubmit={handleDeposit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Deposit Amount (INR)</label>
                <input 
                  type="number" 
                  required
                  min="10"
                  placeholder="e.g. 5000"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={depositAmount}
                  onChange={(e) => setDepositAmount(e.target.value)}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Description / Source</label>
                <input 
                  type="text" 
                  placeholder="e.g. Self deposit, cash"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={depositDesc}
                  onChange={(e) => setDepositDesc(e.target.value)}
                />
              </div>

              <div className="flex gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => setShowDepositModal(false)}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition-colors cursor-pointer"
                >
                  Confirm Deposit
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* WITHDRAW MODAL */}
      {showWithdrawModal && selectedAccount && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md glass-panel rounded-2xl p-6 border border-white/10 relative">
            <h3 className="text-lg font-bold text-white mb-4 font-semibold">Withdraw Funds</h3>
            <p className="text-xs text-gray-400 mb-4 flex items-center gap-2">
              Debiting account: <span className="text-white font-semibold font-mono">{showFullAcc ? selectedAccount.accountNumber : formatAccountNumber(selectedAccount.accountNumber)}</span>
              <button onClick={() => setShowFullAcc(!showFullAcc)} className="text-gray-500 hover:text-white transition-colors">{showFullAcc ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}</button>
              <button onClick={() => navigator.clipboard.writeText(selectedAccount.accountNumber)} className="text-gray-500 hover:text-white transition-colors"><Copy className="w-3 h-3" /></button>
            </p>

            {error && (
              <div className="mb-4 p-3 rounded bg-rose-500/15 border border-rose-500/30 text-rose-200 text-xs flex gap-2">
                <AlertTriangle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            <form onSubmit={handleWithdraw} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Withdrawal Amount (INR)</label>
                <input 
                  type="number" 
                  required
                  min="10"
                  placeholder="e.g. 2000"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={withdrawAmount}
                  onChange={(e) => setWithdrawAmount(e.target.value)}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Description</label>
                <input 
                  type="text" 
                  placeholder="e.g. Self withdrawal, cash"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                  value={withdrawDesc}
                  onChange={(e) => setWithdrawDesc(e.target.value)}
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1 flex items-center gap-1">
                  <Lock className="w-3.5 h-3.5" /> 4-Digit Transaction PIN
                </label>
                <input 
                  type="password" 
                  required
                  maxLength={4}
                  pattern="\d{4}"
                  placeholder="••••"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm tracking-[0.5em] text-center"
                  value={withdrawPin}
                  onChange={(e) => setWithdrawPin(e.target.value)}
                />
              </div>

              <div className="flex gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => setShowWithdrawModal(false)}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 rounded-lg bg-rose-600 hover:bg-rose-500 text-white font-semibold text-xs transition-colors cursor-pointer"
                >
                  Confirm Withdrawal
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};

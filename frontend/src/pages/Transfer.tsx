import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Account, User } from '../types';
import { 
  Send, Lock, Key, ShieldCheck, HelpCircle, Info, 
  AlertTriangle, CheckCircle, RefreshCw, Loader2 
} from 'lucide-react';

interface TransferProps {
  user: User;
}

export const Transfer: React.FC<TransferProps> = ({ user }) => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [selectedAcc, setSelectedAcc] = useState<Account | null>(null);
  
  // Form State
  const [toAccNumber, setToAccNumber] = useState('');
  const [interBank, setInterBank] = useState(false);
  const [bankCode, setBankCode] = useState('');
  const [amount, setAmount] = useState('');
  const [pin, setPin] = useState('');
  const [totp, setTotp] = useState('');
  const [desc, setDesc] = useState('');
  
  // Idempotency state
  const [idempotencyKey, setIdempotencyKey] = useState('');

  // Processing state
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successData, setSuccessData] = useState<any | null>(null);

  useEffect(() => {
    fetchAccounts();
    regenerateIdempotencyKey();
  }, []);

  const fetchAccounts = async () => {
    try {
      const res = await api.accounts.getMy();
      const transferrable = res.data.filter(a => a.accountType !== 'FIXED_DEPOSIT' && a.status === 'ACTIVE');
      setAccounts(transferrable);
      if (transferrable.length > 0) {
        setSelectedAcc(transferrable[0]);
      }
    } catch (err) {
      console.error(err);
    }
  };

  const regenerateIdempotencyKey = () => {
    setIdempotencyKey(crypto.randomUUID());
  };

  const handleTransfer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedAcc) return;
    setError(null);
    setSuccessData(null);
    setLoading(true);

    const transferAmount = parseFloat(amount);
    
    // Client-side daily limit check
    if (selectedAcc.dailyTransferLimit < transferAmount) {
      setError(`Amount exceeds account daily transfer limit of INR ${selectedAcc.dailyTransferLimit.toLocaleString()}`);
      setLoading(false);
      return;
    }

    try {
      const res = await api.transactions.transfer({
        fromAccountId: selectedAcc.id,
        toAccountNumber: toAccNumber,
        interBank,
        bankCode: interBank ? bankCode : undefined,
        amount: transferAmount,
        transactionPin: pin,
        totpCode: user.twoFaEnabled ? totp : undefined,
        description: desc || 'Fund Transfer'
      }, idempotencyKey);

      setSuccessData(res.data);
      // Reset form on success (except source account)
      setToAccNumber('');
      setAmount('');
      setPin('');
      setTotp('');
      setDesc('');
      setBankCode('');
      regenerateIdempotencyKey(); // Set new key for next txn
      fetchAccounts(); // Refresh source balance metadata
    } catch (err: any) {
      setError(err.response?.data?.message || 'Transfer failed. Check details and PIN.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-white">Transfer Funds</h2>
        <p className="text-gray-400 text-sm">Transfer money securely between accounts or external banks instantly.</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Transfer Form */}
        <div className="lg:col-span-2">
          <div className="glass-panel rounded-2xl p-6 border border-white/5 relative">
            
            {successData && (
              <div className="mb-6 p-5 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-200 flex items-start gap-4">
                <CheckCircle className="w-6 h-6 text-emerald-400 shrink-0 mt-0.5" />
                <div>
                  <h4 className="font-bold text-sm text-white">Transfer Cleared Successfully!</h4>
                  <p className="text-xs text-emerald-300/80 mt-1">Your transaction has been processed. Details:</p>
                  <ul className="text-xs space-y-1 mt-2 list-disc pl-4 font-medium text-emerald-100">
                    <li>Reference Number: <span className="font-mono">{successData.referenceNumber}</span></li>
                    <li>Status: <span className="font-bold">{successData.status}</span></li>
                    <li>Amount: INR {successData.amount.toLocaleString()}</li>
                    {successData.toAccountNumber && <li>Beneficiary: {successData.toAccountNumber}</li>}
                  </ul>
                </div>
              </div>
            )}

            {error && (
              <div className="mb-6 p-4 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-200 text-xs flex gap-3 items-start">
                <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
                <div className="font-medium">{error}</div>
              </div>
            )}

            {accounts.length === 0 ? (
              <div className="text-center py-10 text-gray-500">
                <AlertTriangle className="w-12 h-12 mx-auto mb-3 text-amber-500/80" />
                <p className="text-sm font-semibold text-white">No active transaction accounts</p>
                <p className="text-xs mt-1">You must open an active Savings or Current account before performing transfers.</p>
              </div>
            ) : (
              <form onSubmit={handleTransfer} className="space-y-4">
                
                {/* Source Account */}
                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Source Account</label>
                  <select 
                    className="w-full px-3 py-2.5 rounded-lg glass-input text-sm"
                    value={selectedAcc?.id || ''}
                    onChange={(e) => {
                      const acc = accounts.find(a => a.id === e.target.value);
                      if (acc) setSelectedAcc(acc);
                    }}
                  >
                    {accounts.map(a => (
                      <option key={a.id} value={a.id}>
                        {a.accountType} - {a.accountNumber} (Bal: INR {a.balance.toLocaleString()})
                      </option>
                    ))}
                  </select>
                </div>

                {/* Transfer Mode */}
                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-2">Transfer Destination</label>
                  <div className="flex gap-4">
                    <label className="flex-1 flex items-center justify-between p-3 rounded-lg border border-white/5 bg-white/5 hover:bg-white/10 cursor-pointer text-sm">
                      <span className="font-medium text-white">Same Bank</span>
                      <input 
                        type="radio" 
                        name="transferMode" 
                        checked={!interBank} 
                        onChange={() => setInterBank(false)}
                      />
                    </label>
                    <label className="flex-1 flex items-center justify-between p-3 rounded-lg border border-white/5 bg-white/5 hover:bg-white/10 cursor-pointer text-sm">
                      <span className="font-medium text-white">Interbank Transfer</span>
                      <input 
                        type="radio" 
                        name="transferMode" 
                        checked={interBank} 
                        onChange={() => setInterBank(true)}
                      />
                    </label>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Destination Account */}
                  <div>
                    <label className="block text-xs font-semibold text-gray-400 mb-1">
                      {interBank ? 'External Account Number' : 'Beneficiary Account Number'}
                    </label>
                    <input 
                      type="text" 
                      required
                      placeholder="e.g. 100028734912"
                      className="w-full px-3 py-2 rounded-lg glass-input text-sm font-mono"
                      value={toAccNumber}
                      onChange={(e) => setToAccNumber(e.target.value)}
                    />
                  </div>

                  {/* Bank Code (Only if Interbank) */}
                  {interBank && (
                    <div>
                      <label className="block text-xs font-semibold text-gray-400 mb-1">Routing Bank / IFSC Code</label>
                      <input 
                        type="text" 
                        required
                        placeholder="e.g. UTIB0001827"
                        className="w-full px-3 py-2 rounded-lg glass-input text-sm uppercase font-mono"
                        value={bankCode}
                        onChange={(e) => setBankCode(e.target.value)}
                      />
                    </div>
                  )}
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {/* Amount */}
                  <div>
                    <label className="block text-xs font-semibold text-gray-400 mb-1">Amount (INR)</label>
                    <input 
                      type="number" 
                      required
                      min="1"
                      placeholder="e.g. 15000"
                      className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                      value={amount}
                      onChange={(e) => setAmount(e.target.value)}
                    />
                  </div>

                  {/* Transaction PIN */}
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
                      className="w-full px-3 py-2 rounded-lg glass-input text-sm text-center tracking-[0.5em]"
                      value={pin}
                      onChange={(e) => setPin(e.target.value)}
                    />
                  </div>
                </div>

                {/* Description */}
                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Description / Memo</label>
                  <input 
                    type="text" 
                    placeholder="e.g. Rent payment"
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                    value={desc}
                    onChange={(e) => setDesc(e.target.value)}
                  />
                </div>

                {/* 2FA Input (Only if enabled) */}
                {user.twoFaEnabled && (
                  <div className="p-4 rounded-xl bg-blue-500/5 border border-blue-500/20">
                    <label className="block text-xs font-semibold text-blue-300 mb-1 flex items-center gap-1">
                      <ShieldCheck className="w-3.5 h-3.5" /> Google Authenticator 2FA Code
                    </label>
                    <input 
                      type="text" 
                      required
                      maxLength={6}
                      pattern="\d{6}"
                      placeholder="000000"
                      className="w-full px-3 py-2 rounded-lg glass-input text-sm tracking-[0.2em] font-mono"
                      value={totp}
                      onChange={(e) => setTotp(e.target.value)}
                    />
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading}
                  className="w-full py-3 rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-semibold text-sm transition-all duration-300 shadow-lg shadow-blue-500/25 disabled:opacity-50 disabled:cursor-not-allowed mt-4 flex justify-center items-center gap-2 cursor-pointer"
                >
                  {loading ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" /> Executing Transaction...
                    </>
                  ) : (
                    <>
                      <Send className="w-4 h-4" /> Execute Transfer
                    </>
                  )}
                </button>

              </form>
            )}

          </div>
        </div>

        {/* Ledger & Idempotency Sidebar info */}
        <div className="lg:col-span-1 space-y-6">
          
          {/* Idempotency Display */}
          <div className="glass-panel rounded-2xl p-5 border border-white/5 space-y-4">
            <div className="flex justify-between items-center">
              <h3 className="text-sm font-semibold text-white">Idempotency Shield</h3>
              <button 
                onClick={regenerateIdempotencyKey}
                className="p-1 text-gray-400 hover:text-white rounded hover:bg-white/5 transition-colors cursor-pointer"
                title="Regenerate key"
              >
                <RefreshCw className="w-3.5 h-3.5" />
              </button>
            </div>
            
            <p className="text-xs text-gray-400">
              VAULT uses idempotency keys on write APIs to prevent duplicate transactions caused by connection drops or repeated clicks.
            </p>

            <div className="p-3 rounded bg-black/40 border border-white/5 font-mono text-[10px] break-all select-all text-gray-300">
              {idempotencyKey}
            </div>

            <div className="flex items-start gap-2.5 text-[10px] text-gray-400">
              <Info className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
              <span>This key will be automatically sent in the <code>idempotency-key</code> header. If you submit the same form again, the server returns the cached response instead of debiting your account twice.</span>
            </div>
          </div>

          {/* Daily limit gauge */}
          {selectedAcc && (
            <div className="glass-panel rounded-2xl p-5 border border-white/5 space-y-3">
              <h3 className="text-sm font-semibold text-white">Daily Limits</h3>
              <div className="space-y-1">
                <div className="flex justify-between text-xs text-gray-400">
                  <span>Transfer Limit Usage</span>
                  <span>INR {selectedAcc.dailyTransferLimit.toLocaleString()}</span>
                </div>
                <div className="w-full h-2 rounded bg-white/5 overflow-hidden">
                  <div className="h-full bg-blue-500 rounded" style={{ width: '40%' }} /> {/* Mock usage progress */}
                </div>
              </div>
              <p className="text-[10px] text-gray-500">Limits reset daily at midnight. Contact support to raise daily transfer limit.</p>
            </div>
          )}

        </div>

      </div>
    </div>
  );
};

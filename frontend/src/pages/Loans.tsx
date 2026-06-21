import React, { useEffect, useState } from 'react';
import { api } from '../services/api';
import { Loan, Account, EmiScheduleItem, LoanType } from '../types';
import { 
  Building2, Landmark, Coins, FileSpreadsheet, ArrowRight, ShieldAlert,
  Loader2, Calculator, CheckCircle2, Calendar, Lock, AlertCircle 
} from 'lucide-react';

interface LoansProps {
  user: any;
}

export const Loans: React.FC<LoansProps> = ({ user }) => {
  const [loans, setLoans] = useState<Loan[]>([]);
  const [accounts, setAccounts] = useState<Account[]>([]);
  
  // Modals
  const [showApplyModal, setShowApplyModal] = useState(false);
  const [showRepayModal, setShowRepayModal] = useState(false);
  const [showScheduleModal, setShowScheduleModal] = useState(false);
  
  const [selectedLoan, setSelectedLoan] = useState<Loan | null>(null);
  const [schedule, setSchedule] = useState<EmiScheduleItem[]>([]);
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  
  // Apply Form State
  const [accId, setAccId] = useState('');
  const [loanType, setLoanType] = useState<LoanType>('PERSONAL');
  const [principal, setPrincipal] = useState('100000');
  const [interestRate, setInterestRate] = useState('12.5');
  const [tenure, setTenure] = useState('24');

  const [loanRates, setLoanRates] = useState<Record<string, string>>({
    PERSONAL: '12.5',
    HOME: '8.5',
    CAR: '9.5',
  });
  
  // Repay Form State
  const [repayAmount, setRepayAmount] = useState('');
  const [repayPin, setRepayPin] = useState('');

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchLoans();
    fetchAccounts();
    fetchInterestRates();
  }, []);

  const fetchInterestRates = async () => {
    try {
      const res = await api.loans.getInterestRates();
      const ratesMap: Record<string, string> = {};
      res.data.forEach(item => {
        ratesMap[item.loanType] = item.interestRate.toString();
      });
      setLoanRates(ratesMap);
      if (ratesMap[loanType]) {
        setInterestRate(ratesMap[loanType]);
      }
    } catch (err) {
      console.error('Failed to fetch interest rates:', err);
    }
  };

  const fetchLoans = async () => {
    setLoading(true);
    try {
      const res = await api.loans.getMy();
      setLoans(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const fetchAccounts = async () => {
    try {
      const res = await api.accounts.getMy();
      const eligible = res.data.filter(a => a.accountType !== 'FIXED_DEPOSIT' && a.status === 'ACTIVE');
      setAccounts(eligible);
      if (eligible.length > 0) setAccId(eligible[0].id);
    } catch (err) {
      console.error(err);
    }
  };

  const fetchAmortization = async (loanId: string) => {
    setLoadingSchedule(true);
    try {
      const res = await api.loans.getSchedule(loanId);
      setSchedule(res.data);
    } catch (err) {
      console.error(err);
    } finally {
      setLoadingSchedule(false);
    }
  };

  // Real-time EMI Estimator
  const getEstimatedEmi = () => {
    const p = parseFloat(principal);
    const r = parseFloat(interestRate) / 12 / 100;
    const n = parseInt(tenure);
    if (!p || !r || !n) return 0;
    const pow = Math.pow(1 + r, n);
    return (p * r * pow) / (pow - 1);
  };

  const handleApply = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    try {
      await api.loans.apply({
        accountId: accId,
        loanType,
        principal: parseFloat(principal),
        interestRate: parseFloat(interestRate),
        tenureMonths: parseInt(tenure)
      });
      setShowApplyModal(false);
      fetchLoans();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Loan application failed.');
    }
  };

  const handleRepay = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedLoan) return;
    setError(null);
    try {
      await api.loans.repay(
        selectedLoan.id,
        parseFloat(repayAmount),
        repayPin
      );
      setShowRepayModal(false);
      setRepayAmount('');
      setRepayPin('');
      fetchLoans();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Repayment failed. Please check funds and transaction PIN.');
    }
  };

  const getLoanIcon = (type: LoanType) => {
    switch (type) {
      case 'HOME': return <Landmark className="w-8 h-8 text-blue-400" />;
      case 'CAR': return <Building2 className="w-8 h-8 text-purple-400" />;
      default: return <Coins className="w-8 h-8 text-indigo-400" />;
    }
  };

  return (
    <div className="space-y-6">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-white">Lending Portal</h2>
          <p className="text-gray-400 text-sm font-medium">Apply for Home, Personal, or Car loans with instant EMI amortization schedules.</p>
        </div>
        <button
          onClick={() => { setError(null); setShowApplyModal(true); }}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition-colors cursor-pointer"
        >
          <Calculator className="w-4 h-4" /> Apply for Loan
        </button>
      </div>

      {loading && loans.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20">
          <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
          <span className="text-sm text-gray-400 mt-3 font-medium">Loading loans portfolio...</span>
        </div>
      ) : loans.length === 0 ? (
        <div className="glass-panel rounded-2xl p-12 text-center border border-white/5 max-w-xl mx-auto mt-8">
          <Calculator className="w-16 h-16 text-gray-500 mx-auto mb-4" />
          <h3 className="text-xl font-bold text-white mb-2">No active loans</h3>
          <p className="text-gray-400 text-sm mb-6 max-w-md mx-auto">
            You don't have any pending or active loans. Click below to use our loan estimator and apply for finance.
          </p>
          <button
            onClick={() => { setError(null); setShowApplyModal(true); }}
            className="px-6 py-3 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm transition-colors cursor-pointer"
          >
            Apply for Loan
          </button>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {loans.map(loan => (
            <div key={loan.id} className="glass-panel rounded-2xl p-6 border border-white/10 flex flex-col justify-between">
              
              <div className="flex justify-between items-start mb-6">
                <div className="flex items-center gap-3">
                  <div className="p-2.5 rounded-xl bg-white/5 border border-white/5">
                    {getLoanIcon(loan.loanType)}
                  </div>
                  <div>
                    <h3 className="font-bold text-white text-base">{loan.loanType} Loan</h3>
                    <p className="text-xs text-gray-400 font-mono">ID: #{loan.id.substring(0, 8).toUpperCase()}</p>
                  </div>
                </div>
                
                <span className={`inline-flex items-center gap-1 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase ${
                  loan.status === 'ACTIVE' ? 'bg-emerald-500/10 text-emerald-400' :
                  loan.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400 font-medium' :
                  loan.status === 'CLOSED' ? 'bg-gray-500/10 text-gray-400' :
                  'bg-rose-500/10 text-rose-400'
                }`}>
                  {loan.status}
                </span>
              </div>

              <div className="grid grid-cols-2 gap-y-4 gap-x-2 border-t border-white/5 pt-4 mb-6">
                <div>
                  <span className="text-[10px] text-gray-400 block uppercase font-medium">Principal Amount</span>
                  <span className="text-sm font-extrabold text-white">INR {loan.principal.toLocaleString()}</span>
                </div>
                <div>
                  <span className="text-[10px] text-gray-400 block uppercase font-medium">Outstanding Balance</span>
                  <span className="text-sm font-extrabold text-indigo-400">INR {loan.outstandingAmount.toLocaleString()}</span>
                </div>
                <div>
                  <span className="text-[10px] text-gray-400 block uppercase font-medium">Monthly EMI</span>
                  <span className="text-sm font-extrabold text-white">INR {loan.emiAmount.toLocaleString()}</span>
                </div>
                <div>
                  <span className="text-[10px] text-gray-400 block uppercase font-medium">Interest Rate</span>
                  <span className="text-sm font-semibold text-emerald-400">{loan.interestRate}% p.a.</span>
                </div>
                {loan.status === 'ACTIVE' && loan.nextDueDate && (
                  <div className="col-span-2 flex items-center gap-1.5 text-xs text-yellow-400 mt-2">
                    <Calendar className="w-4 h-4 shrink-0" />
                    <span>Next installment due: <span className="font-semibold">{loan.nextDueDate}</span></span>
                  </div>
                )}
              </div>

              {/* Repay / Amortization Actions */}
              <div className="flex gap-3">
                <button
                  onClick={() => {
                    setSelectedLoan(loan);
                    fetchAmortization(loan.id);
                    setShowScheduleModal(true);
                  }}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 border border-white/5 text-gray-300 font-semibold text-xs flex justify-center items-center gap-1.5 transition-colors cursor-pointer"
                >
                  <FileSpreadsheet className="w-4 h-4" /> View Schedule
                </button>
                
                {loan.status === 'ACTIVE' && (
                  <button
                    onClick={() => {
                      setError(null);
                      setSelectedLoan(loan);
                      setRepayAmount(loan.emiAmount.toString());
                      setShowRepayModal(true);
                    }}
                    className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs flex justify-center items-center gap-1.5 transition-colors cursor-pointer"
                  >
                    <ArrowRight className="w-4 h-4" /> Pay EMI
                  </button>
                )}
              </div>

            </div>
          ))}
        </div>
      )}

      {/* APPLY LOAN MODAL */}
      {showApplyModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-lg glass-panel rounded-2xl p-6 border border-white/10 relative">
            <h3 className="text-lg font-bold text-white mb-4">Apply for Finance</h3>
            
            {error && (
              <div className="mb-4 p-3 rounded bg-rose-500/15 border border-rose-500/30 text-rose-200 text-xs flex gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            <form onSubmit={handleApply} className="space-y-4">
              
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Repayment/Disburse Account</label>
                  <select 
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                    value={accId}
                    onChange={(e) => setAccId(e.target.value)}
                  >
                    {accounts.map(a => (
                      <option key={a.id} value={a.id}>
                        {a.accountType} - {a.accountNumber}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Loan Purpose</label>
                  <select 
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                    value={loanType}
                    onChange={(e) => {
                      const selectedType = e.target.value;
                      setLoanType(selectedType as LoanType);
                      setInterestRate(loanRates[selectedType] || '8.5');
                    }}
                  >
                    <option value="PERSONAL">Personal Loan</option>
                    <option value="HOME">Home Loan</option>
                    <option value="CAR">Car Loan</option>
                  </select>
                </div>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Principal (INR)</label>
                  <input 
                    type="number" 
                    required
                    min="1000"
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                    value={principal}
                    onChange={(e) => setPrincipal(e.target.value)}
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Interest Rate (% p.a.)</label>
                  <input 
                    type="number" 
                    required
                    readOnly
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm bg-white/5 opacity-75 cursor-not-allowed"
                    value={interestRate}
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-gray-400 mb-1">Tenure (Months)</label>
                  <input 
                    type="number" 
                    required
                    min="3"
                    className="w-full px-3 py-2 rounded-lg glass-input text-sm"
                    value={tenure}
                    onChange={(e) => setTenure(e.target.value)}
                  />
                </div>
              </div>

              {/* Real-time calculator panel */}
              <div className="p-4 rounded-xl bg-blue-500/5 border border-blue-500/10 flex justify-between items-center">
                <div className="flex items-center gap-2">
                  <Calculator className="w-5 h-5 text-blue-400" />
                  <span className="text-xs font-semibold text-gray-300">Estimated Monthly EMI</span>
                </div>
                <span className="text-lg font-extrabold text-blue-400">
                  INR {getEstimatedEmi().toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                </span>
              </div>

              <div className="flex gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => setShowApplyModal(false)}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition-colors cursor-pointer"
                >
                  Submit Application
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* REPAY MODAL */}
      {showRepayModal && selectedLoan && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-md glass-panel rounded-2xl p-6 border border-white/10 relative">
            <h3 className="text-lg font-bold text-white mb-2">Loan EMI Installment Repayment</h3>
            <p className="text-xs text-gray-400 mb-4 font-mono">Loan ID: #{selectedLoan.id.substring(0, 8).toUpperCase()}</p>
            
            {error && (
              <div className="mb-4 p-3 rounded bg-rose-500/15 border border-rose-500/30 text-rose-200 text-xs flex gap-2">
                <AlertCircle className="w-4 h-4 shrink-0" /> {error}
              </div>
            )}

            <form onSubmit={handleRepay} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-gray-400 mb-1">Repayment Amount (INR)</label>
                <input 
                  type="number" 
                  required
                  min="1"
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm text-white font-semibold"
                  value={repayAmount}
                  onChange={(e) => setRepayAmount(e.target.value)}
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
                  className="w-full px-3 py-2 rounded-lg glass-input text-sm text-center tracking-[0.5em]"
                  value={repayPin}
                  onChange={(e) => setRepayPin(e.target.value)}
                />
              </div>

              <div className="flex gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => setShowRepayModal(false)}
                  className="flex-1 py-2 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="flex-1 py-2 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition-colors cursor-pointer"
                >
                  Confirm Payment
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* AMORTIZATION SCHEDULE MODAL */}
      {showScheduleModal && selectedLoan && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/60 backdrop-blur-sm">
          <div className="w-full max-w-2xl glass-panel rounded-2xl p-6 border border-white/10 relative max-h-[85vh] flex flex-col">
            <h3 className="text-lg font-bold text-white mb-1">EMI Repayment Amortization Schedule</h3>
            <p className="text-xs text-gray-400 mb-4 font-mono">Loan Details: {selectedLoan.loanType} - Interest {selectedLoan.interestRate}%</p>
            
            {loadingSchedule ? (
              <div className="flex flex-col items-center justify-center py-24 grow">
                <Loader2 className="w-8 h-8 text-blue-500 animate-spin" />
                <span className="text-xs text-gray-400 mt-2">Loading schedule...</span>
              </div>
            ) : (
              <div className="overflow-y-auto no-scrollbar grow mt-2 border border-white/5 rounded-lg">
                <table className="w-full text-left border-collapse text-xs">
                  <thead>
                    <tr className="bg-white/5 text-gray-300 font-semibold uppercase tracking-wider">
                      <th className="p-3">Inst. #</th>
                      <th className="p-3">Due Date</th>
                      <th className="p-3">EMI Amount</th>
                      <th className="p-3">Interest Component</th>
                      <th className="p-3">Principal Component</th>
                      <th className="p-3 text-right">Outstanding Principal</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-white/5">
                    {schedule.map(item => (
                      <tr key={item.installmentNo} className="hover:bg-white/5 text-gray-300">
                        <td className="p-3 font-mono">{item.installmentNo}</td>
                        <td className="p-3">{item.dueDate}</td>
                        <td className="p-3">INR {item.emi.toLocaleString(undefined, { minimumFractionDigits: 2 })}</td>
                        <td className="p-3 text-rose-300">INR {item.interest.toLocaleString(undefined, { minimumFractionDigits: 2 })}</td>
                        <td className="p-3 text-emerald-300">INR {item.principalPaid.toLocaleString(undefined, { minimumFractionDigits: 2 })}</td>
                        <td className="p-3 text-right font-mono">INR {item.remainingBalance.toLocaleString(undefined, { minimumFractionDigits: 2 })}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}

            <button
              onClick={() => setShowScheduleModal(false)}
              className="mt-6 py-2.5 w-full rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
            >
              Close Schedule
            </button>
          </div>
        </div>
      )}

    </div>
  );
};

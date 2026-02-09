import React, { useState } from 'react';
import { api } from '../services/api';
import { User } from '../types';
import { ShieldCheck, ShieldAlert, LogOut, CheckCircle, AlertCircle, Loader2, QrCode } from 'lucide-react';

interface NavbarProps {
  user: User;
  onLogout: () => void;
  onUpdateUser: (updatedUser: User) => void;
}

export const Navbar: React.FC<NavbarProps> = ({ user, onLogout, onUpdateUser }) => {
  const [show2faModal, setShow2faModal] = useState(false);
  const [qrUrl, setQrUrl] = useState('');
  const [secretKey, setSecretKey] = useState('');
  const [verifyCode, setVerifyCode] = useState('');
  
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSetup2FA = async () => {
    setLoading(true);
    setError(null);
    setSuccess(false);
    try {
      // Setup base endpoint is /api/auth/2fa/setup, our api client maps it
      const res = await api.auth.setup2fa();
      setSecretKey(res.data.secretKey);
      setQrUrl(res.data.qrCodeUrl);
      setShow2faModal(true);
    } catch (err: any) {
      alert(err.response?.data?.message || 'Failed to initiate 2FA setup.');
    } finally {
      setLoading(false);
    }
  };

  const handleVerify2FA = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError(null);
    try {
      await api.auth.verify2fa(verifyCode);
      setSuccess(true);
      onUpdateUser({ ...user, twoFaEnabled: true });
      setTimeout(() => {
        setShow2faModal(false);
        setSuccess(false);
        setVerifyCode('');
      }, 2000);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Verification failed. Try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      await api.auth.logout();
    } catch (err) {
      console.error(err);
    } finally {
      onLogout();
    }
  };

  return (
    <nav className="glass-panel border border-white/5 rounded-2xl px-6 py-4 flex justify-between items-center bg-slate-900/60 relative z-40">
      
      {/* Brand Logo */}
      <div className="flex items-center gap-2.5">
        <div className="p-2 rounded-xl bg-blue-600/10 border border-blue-500/25">
          <LandmarkIcon className="w-6 h-6 text-blue-400" />
        </div>
        <div>
          <span className="text-lg font-black tracking-wider text-white">VAULT</span>
          <span className="text-[9px] block text-gray-500 font-bold uppercase tracking-widest mt-0.5">unified ledger</span>
        </div>
      </div>

      {/* User Status controls */}
      <div className="flex items-center gap-6">
        
        {/* Profile Card */}
        <div className="hidden md:flex flex-col items-end">
          <span className="text-xs font-bold text-white">{user.fullName}</span>
          <span className="text-[10px] text-gray-400 font-mono mt-0.5">{user.email}</span>
        </div>

        {/* 2FA Shield badge */}
        <div className="flex items-center gap-3">
          {user.twoFaEnabled ? (
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-500/10 border border-emerald-500/25 text-emerald-400 text-xs font-bold shadow-md shadow-emerald-500/5">
              <ShieldCheck className="w-4 h-4" /> 2FA Secured
            </span>
          ) : (
            <button
              onClick={handleSetup2FA}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-amber-500/10 border border-amber-500/25 hover:bg-amber-500/20 text-amber-400 hover:text-amber-300 text-xs font-bold transition-all cursor-pointer shadow-md shadow-amber-500/5"
            >
              <ShieldAlert className="w-4 h-4" /> Enable 2FA
            </button>
          )}

          {/* Logout */}
          <button
            onClick={handleLogout}
            className="p-2 rounded-lg bg-white/5 hover:bg-rose-600/15 border border-white/5 hover:border-rose-500/25 text-gray-400 hover:text-rose-400 transition-all cursor-pointer"
            title="Sign Out"
          >
            <LogOut className="w-4 h-4" />
          </button>
        </div>

      </div>

      {/* 2FA SETUP MODAL */}
      {show2faModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center px-4 bg-black/70 backdrop-blur-md">
          <div className="w-full max-w-md glass-panel rounded-2xl p-6 border border-white/10 relative">
            <h3 className="text-lg font-bold text-white mb-2">Configure Google Authenticator</h3>
            <p className="text-xs text-gray-400 mb-6">Scan the QR code or enter the key manually to link Authenticator to VAULT.</p>

            {success ? (
              <div className="text-center py-10 space-y-3">
                <CheckCircle className="w-16 h-16 text-green-400 mx-auto animate-bounce" />
                <h4 className="font-bold text-white text-base">2FA Enabled Successfully!</h4>
                <p className="text-xs text-gray-400">Authenticator linked. Your account is now secure.</p>
              </div>
            ) : (
              <div className="space-y-6">
                
                {/* QR Code and Secret Key display */}
                <div className="flex flex-col items-center justify-center gap-3">
                  <div className="p-3 bg-white rounded-xl shadow-lg relative border border-gray-200">
                    {/* Render a mock QR Code interface if we can't load Google API QR rendering, or standard canvas.
                        We can use standard charts or a gorgeous placeholder, or a Google QR code API:
                        `https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(qrUrl)}`
                        This API is public, requires no login, and loads instantly in the image tag! Incredibly high-fidelity! */}
                    <img 
                      src={`https://api.qrserver.com/v1/create-qr-code/?size=150x150&data=${encodeURIComponent(qrUrl)}`}
                      alt="Authenticator QR Code"
                      className="w-36 h-36"
                    />
                  </div>
                  
                  <div className="w-full text-center">
                    <span className="text-[10px] text-gray-400 block uppercase font-medium">Secret Key (Manual entry)</span>
                    <span className="text-xs font-mono font-bold text-indigo-300 select-all tracking-wider block bg-black/40 px-3 py-1.5 rounded border border-white/5 mt-1">
                      {secretKey}
                    </span>
                  </div>
                </div>

                {error && (
                  <div className="p-3 rounded bg-rose-500/10 border border-rose-500/35 text-rose-300 text-xs flex gap-2">
                    <AlertCircle className="w-4 h-4 shrink-0" /> {error}
                  </div>
                )}

                <form onSubmit={handleVerify2FA} className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-gray-400 mb-1">Enter Verification Code</label>
                    <input 
                      type="text" 
                      required
                      maxLength={6}
                      pattern="\d{6}"
                      placeholder="000000"
                      className="w-full py-2.5 rounded-lg glass-input text-sm text-center font-mono tracking-[0.2em]"
                      value={verifyCode}
                      onChange={(e) => setVerifyCode(e.target.value)}
                    />
                  </div>

                  <div className="flex gap-3">
                    <button
                      type="button"
                      onClick={() => setShow2faModal(false)}
                      className="flex-1 py-2.5 rounded-lg bg-white/5 hover:bg-white/10 text-gray-300 font-semibold text-xs transition-colors cursor-pointer"
                    >
                      Cancel
                    </button>
                    <button
                      type="submit"
                      disabled={loading}
                      className="flex-1 py-2.5 rounded-lg bg-blue-600 hover:bg-blue-500 text-white font-semibold text-xs transition-colors flex justify-center items-center gap-1.5 cursor-pointer"
                    >
                      {loading ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : 'Verify & Enable'}
                    </button>
                  </div>
                </form>

              </div>
            )}

          </div>
        </div>
      )}

    </nav>
  );
};

// SVG Icon replacement inside React
const LandmarkIcon = (props: React.SVGProps<SVGSVGElement>) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    {...props}
  >
    <line x1="3" y1="21" x2="21" y2="21" />
    <line x1="3" y1="10" x2="21" y2="10" />
    <path d="M12 2L2 7h20L12 2z" />
    <line x1="5" y1="10" x2="5" y2="21" />
    <line x1="9" y1="10" x2="9" y2="21" />
    <line x1="13" y1="10" x2="13" y2="21" />
    <line x1="17" y1="10" x2="17" y2="21" />
  </svg>
);

import React, { useState } from 'react';
import { api } from '../services/api';
import { User } from '../types';
import { KeyRound, Mail, User as UserIcon, Phone, ShieldCheck, Lock, AlertCircle } from 'lucide-react';

interface AuthProps {
  onLoginSuccess: (user: User) => void;
}

export const Auth: React.FC<AuthProps> = ({ onLoginSuccess }) => {
  const [mode, setMode] = useState<'login' | 'register' | '2fa'>('login');
  
  // Registration State
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [password, setPassword] = useState('');
  const [transactionPin, setTransactionPin] = useState('');
  
  // Login State
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [totpCode, setTotpCode] = useState('');
  
  // Errors and Loading
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await api.auth.register({ fullName, email, phone, password, transactionPin });
      setLoginEmail(email);
      setMode('login');
      alert('Registration successful! Please login.');
      // Clear fields
      setFullName('');
      setEmail('');
      setPhone('');
      setPassword('');
      setTransactionPin('');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Registration failed. Check your inputs.');
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await api.auth.login({
        email: loginEmail,
        password: loginPassword,
        totpCode: mode === '2fa' ? totpCode : undefined
      });

      const data = res.data;
      if (data.twoFaRequired) {
        setMode('2fa');
      } else {
        localStorage.setItem('accessToken', data.accessToken);
        localStorage.setItem('refreshToken', data.refreshToken);
        
        onLoginSuccess({
          id: '', // Returned by token decoding or profile if needed
          email: data.email,
          fullName: data.fullName,
          role: data.role as 'CUSTOMER' | 'ADMIN',
          twoFaEnabled: data.twoFaEnabled
        });
      }
    } catch (err: any) {
      setError(err.response?.data?.message || 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-height-screen w-full flex items-center justify-center px-4 py-16">
      <div className="w-full max-w-md glass-panel rounded-2xl p-8 relative overflow-hidden border border-white/10">
        {/* Glow Effects */}
        <div className="absolute -top-24 -left-24 w-48 h-48 bg-blue-500/25 rounded-full blur-3xl" />
        <div className="absolute -bottom-24 -right-24 w-48 h-48 bg-purple-500/25 rounded-full blur-3xl" />

        <div className="text-center mb-8 relative z-10">
          <h1 className="text-4xl font-extrabold tracking-tight bg-gradient-to-r from-blue-400 via-indigo-400 to-purple-400 bg-clip-text text-transparent animate-text-gradient">
            VAULT
          </h1>
          <p className="text-gray-400 mt-2 text-sm font-medium">
            Verified Accounts & Unified Ledger Transactions
          </p>
        </div>

        {error && (
          <div className="mb-6 p-4 rounded-lg bg-rose-500/10 border border-rose-500/35 flex items-start gap-3 relative z-10">
            <AlertCircle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
            <div className="text-sm text-rose-200 font-medium">{error}</div>
          </div>
        )}

        {mode === 'login' && (
          <form onSubmit={handleLogin} className="space-y-5 relative z-10">
            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">Email Address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                <input
                  type="email"
                  required
                  placeholder="name@example.com"
                  className="w-full pl-11 pr-4 py-3 rounded-lg glass-input text-sm"
                  value={loginEmail}
                  onChange={(e) => setLoginEmail(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2">Password</label>
              <div className="relative">
                <KeyRound className="absolute left-3 top-3 w-5 h-5 text-gray-400" />
                <input
                  type="password"
                  required
                  placeholder="••••••••"
                  className="w-full pl-11 pr-4 py-3 rounded-lg glass-input text-sm"
                  value={loginPassword}
                  onChange={(e) => setLoginPassword(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-semibold text-sm transition-all duration-300 shadow-lg shadow-blue-500/25 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {loading ? 'Authenticating...' : 'Sign In'}
            </button>

            <p className="text-center text-xs text-gray-400 mt-4">
              Don't have an account?{' '}
              <button
                type="button"
                onClick={() => { setError(null); setMode('register'); }}
                className="text-blue-400 hover:underline font-semibold cursor-pointer"
              >
                Sign Up
              </button>
            </p>
          </form>
        )}

        {mode === 'register' && (
          <form onSubmit={handleRegister} className="space-y-4 relative z-10">
            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">Full Name</label>
              <div className="relative">
                <UserIcon className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
                <input
                  type="text"
                  required
                  placeholder="John Doe"
                  className="w-full pl-10 pr-4 py-2 rounded-lg glass-input text-sm"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">Email Address</label>
              <div className="relative">
                <Mail className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
                <input
                  type="email"
                  required
                  placeholder="john@example.com"
                  className="w-full pl-10 pr-4 py-2 rounded-lg glass-input text-sm"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">Phone Number</label>
              <div className="relative">
                <Phone className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
                <input
                  type="text"
                  required
                  placeholder="+919876543210"
                  className="w-full pl-10 pr-4 py-2 rounded-lg glass-input text-sm"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">Password</label>
              <div className="relative">
                <KeyRound className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
                <input
                  type="password"
                  required
                  placeholder="At least 8 characters"
                  className="w-full pl-10 pr-4 py-2 rounded-lg glass-input text-sm"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                />
              </div>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-1">4-Digit Transaction PIN</label>
              <div className="relative">
                <Lock className="absolute left-3 top-2.5 w-4 h-4 text-gray-400" />
                <input
                  type="password"
                  required
                  pattern="\d{4}"
                  placeholder="1234 (Used for transfers)"
                  maxLength={4}
                  className="w-full pl-10 pr-4 py-2 rounded-lg glass-input text-sm"
                  value={transactionPin}
                  onChange={(e) => setTransactionPin(e.target.value)}
                />
              </div>
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2.5 rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-semibold text-sm transition-all duration-300 shadow-lg shadow-blue-500/25 disabled:opacity-50 disabled:cursor-not-allowed mt-2 cursor-pointer"
            >
              {loading ? 'Registering...' : 'Sign Up'}
            </button>

            <p className="text-center text-xs text-gray-400 mt-2">
              Already have an account?{' '}
              <button
                type="button"
                onClick={() => { setError(null); setMode('login'); }}
                className="text-blue-400 hover:underline font-semibold cursor-pointer"
              >
                Sign In
              </button>
            </p>
          </form>
        )}

        {mode === '2fa' && (
          <form onSubmit={handleLogin} className="space-y-5 relative z-10">
            <div className="text-center mb-4">
              <ShieldCheck className="w-16 h-16 text-blue-400 mx-auto mb-2 animate-pulse" />
              <p className="text-sm text-gray-300 font-semibold">Two-Factor Authentication</p>
              <p className="text-xs text-gray-400 mt-1">Please enter the 6-digit verification code from your Google Authenticator app.</p>
            </div>

            <div>
              <label className="block text-xs font-semibold text-gray-300 uppercase tracking-wider mb-2 text-center">Verification Code</label>
              <input
                type="text"
                required
                maxLength={6}
                pattern="\d{6}"
                placeholder="000000"
                className="w-full py-3 rounded-lg glass-input text-lg tracking-[0.5em] text-center font-mono"
                value={totpCode}
                onChange={(e) => setTotpCode(e.target.value)}
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 rounded-lg bg-gradient-to-r from-blue-600 to-indigo-600 hover:from-blue-500 hover:to-indigo-500 text-white font-semibold text-sm transition-all duration-300 shadow-lg shadow-blue-500/25 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
            >
              {loading ? 'Verifying...' : 'Verify & Sign In'}
            </button>

            <button
              type="button"
              onClick={() => { setError(null); setMode('login'); setTotpCode(''); }}
              className="w-full text-center text-xs text-blue-400 hover:underline font-semibold cursor-pointer"
            >
              Back to Login
            </button>
          </form>
        )}
      </div>
    </div>
  );
};

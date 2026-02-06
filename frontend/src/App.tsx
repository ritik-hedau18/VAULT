import { useState, useEffect } from 'react';
import { Auth } from './pages/Auth';
import { Dashboard } from './pages/Dashboard';
import { Transfer } from './pages/Transfer';
import { Loans } from './pages/Loans';
import { Admin } from './pages/Admin';
import { Navbar } from './components/Navbar';
import { User } from './types';
import { 
  Wallet, Send, Calculator, ShieldCheck, Landmark,
  HelpCircle, CreditCard, ChevronRight, Menu, X 
} from 'lucide-react';

export default function App() {
  const [user, setUser] = useState<User | null>(null);
  const [activeTab, setActiveTab] = useState<'dashboard' | 'transfer' | 'loans' | 'admin'>('dashboard');
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    // Attempt session restore
    const storedUser = localStorage.getItem('user');
    const token = localStorage.getItem('accessToken');
    if (storedUser && token) {
      setUser(JSON.parse(storedUser));
    }

    // Register refresh-token expiration listener
    const handleAuthLogout = () => {
      handleLogout();
      alert('Your session has expired. Please login again.');
    };
    window.addEventListener('auth-logout', handleAuthLogout);

    return () => {
      window.removeEventListener('auth-logout', handleAuthLogout);
    };
  }, []);

  const handleLoginSuccess = (loggedInUser: User) => {
    setUser(loggedInUser);
    localStorage.setItem('user', JSON.stringify(loggedInUser));
    setActiveTab('dashboard');
  };

  const handleLogout = () => {
    setUser(null);
    localStorage.removeItem('user');
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    setActiveTab('dashboard');
  };

  if (!user) {
    return (
      <div className="min-h-screen bg-[#070a13] flex items-center justify-center">
        <Auth onLoginSuccess={handleLoginSuccess} />
      </div>
    );
  }

  const tabs = [
    { id: 'dashboard', label: 'My Dashboard', icon: <Wallet className="w-4 h-4" /> },
    { id: 'transfer', label: 'Fund Transfer', icon: <Send className="w-4 h-4" /> },
    { id: 'loans', label: 'Lending Portal', icon: <Calculator className="w-4 h-4" /> },
    ...(user.role === 'ADMIN' 
      ? [{ id: 'admin', label: 'Admin Panel', icon: <ShieldCheck className="w-4 h-4" /> }] 
      : [])
  ];

  return (
    <div className="min-h-screen bg-[#070a13] flex flex-col md:flex-row relative">
      
      {/* BACKGROUND EFFECTS */}
      <div className="fixed inset-0 pointer-events-none z-0">
        <div className="absolute top-1/4 left-1/4 w-[500px] h-[500px] bg-blue-900/10 rounded-full blur-[120px]" />
        <div className="absolute bottom-1/4 right-1/4 w-[500px] h-[500px] bg-purple-900/10 rounded-full blur-[120px]" />
      </div>

      {/* MOBILE HEADER BAR */}
      <div className="md:hidden w-full px-5 py-4 flex justify-between items-center bg-slate-950 border-b border-white/5 z-40 relative">
        <div className="flex items-center gap-2">
          <Landmark className="w-5 h-5 text-blue-400" />
          <span className="text-sm font-black tracking-wider text-white">VAULT</span>
        </div>
        <button
          onClick={() => setSidebarOpen(!sidebarOpen)}
          className="p-1 rounded text-gray-400 hover:text-white"
        >
          {sidebarOpen ? <X className="w-6 h-6" /> : <Menu className="w-6 h-6" />}
        </button>
      </div>

      {/* SIDEBAR PANEL */}
      <aside className={`
        fixed md:sticky top-0 left-0 h-screen w-64 shrink-0 glass-panel border-r border-white/5 flex flex-col justify-between py-6 px-4 z-50 transition-transform duration-300 bg-slate-950/90 md:bg-transparent
        ${sidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}
      `}>
        <div className="space-y-8">
          
          {/* Logo brand */}
          <div className="hidden md:flex items-center gap-3 px-2">
            <div className="p-2 rounded-xl bg-blue-600/10 border border-blue-500/25">
              <Landmark className="w-5 h-5 text-blue-400" />
            </div>
            <div>
              <h2 className="text-base font-extrabold text-white leading-none">VAULT</h2>
              <span className="text-[9px] text-gray-500 font-bold uppercase tracking-widest mt-1 block">ledger core</span>
            </div>
          </div>

          {/* Navigation Links */}
          <nav className="space-y-1">
            {tabs.map((tab) => (
              <button
                key={tab.id}
                onClick={() => {
                  setActiveTab(tab.id as any);
                  setSidebarOpen(false);
                }}
                className={`w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-xs font-semibold tracking-wide transition-all cursor-pointer ${
                  activeTab === tab.id
                    ? 'bg-blue-600 text-white shadow-md shadow-blue-600/20'
                    : 'text-gray-400 hover:bg-white/5 hover:text-white'
                }`}
              >
                <div className="flex items-center gap-3">
                  {tab.icon}
                  <span>{tab.label}</span>
                </div>
                <ChevronRight className={`w-3.5 h-3.5 opacity-50 ${activeTab === tab.id ? 'block' : 'hidden'}`} />
              </button>
            ))}
          </nav>
        </div>

        {/* Footer info */}
        <div className="space-y-4 px-2">
          <div className="p-4 rounded-xl bg-white/5 border border-white/5 space-y-2">
            <div className="flex items-center gap-2">
              <CreditCard className="w-4 h-4 text-blue-400" />
              <span className="text-[10px] font-bold text-white uppercase tracking-wider">Secured Network</span>
            </div>
            <p className="text-[9px] text-gray-500 leading-normal">
              AES-256 encrypted ledger records are stored securely. Double click prevention enabled.
            </p>
          </div>
          
          <div className="flex justify-between items-center text-[10px] text-gray-500">
            <span>VAULT Platform v1.0</span>
            <HelpCircle className="w-3.5 h-3.5 hover:text-white cursor-pointer" />
          </div>
        </div>

      </aside>

      {/* MAIN MAIN CONTENT BLOCK */}
      <main className="grow flex flex-col p-4 md:p-8 gap-6 overflow-x-hidden min-h-screen relative z-10 w-full">
        {/* Top Navbar */}
        <Navbar 
          user={user} 
          onLogout={handleLogout} 
          onUpdateUser={(updated) => {
            setUser(updated);
            localStorage.setItem('user', JSON.stringify(updated));
          }} 
        />

        {/* Dynamic page render */}
        <div className="grow animate-fade-in">
          {activeTab === 'dashboard' && <Dashboard user={user} />}
          {activeTab === 'transfer' && <Transfer user={user} />}
          {activeTab === 'loans' && <Loans user={user} />}
          {activeTab === 'admin' && <Admin />}
        </div>
      </main>

    </div>
  );
}

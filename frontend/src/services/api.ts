import axios from 'axios';
import { 
  User, Account, Transaction, Loan, EmiScheduleItem, DashboardStats, 
  AccountType, LoanType 
} from '../types';

const API_BASE_URL = 'https://localhost:8080/api';

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // For cookie support if needed
});

// Request Interceptor: Attach Auth Token & Generate Idempotency Key for writes
apiClient.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('accessToken');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Automatically append idempotency key for transactions write endpoints if not manually provided
    const isWriteTxn = config.method === 'post' && (
      config.url?.includes('/transactions/transfer') || 
      config.url?.includes('/transactions/deposit') || 
      config.url?.includes('/transactions/withdraw')
    );

    if (isWriteTxn && !config.headers['idempotency-key']) {
      // Generate a client-side UUID
      config.headers['idempotency-key'] = crypto.randomUUID();
    }

    return config;
  },
  (error) => Promise.reject(error)
);

// Response Interceptor: Auto-rotate Refresh Token on 401 Unauthorized
let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (token) {
      prom.resolve(token);
    } else {
      prom.reject(error);
    }
  });
  failedQueue = [];
};

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    if (error.response?.status === 401 && !originalRequest._retry) {
      if (originalRequest.url?.includes('/auth/login') || originalRequest.url?.includes('/auth/refresh')) {
        return Promise.reject(error);
      }

      if (isRefreshing) {
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then((token) => {
            originalRequest.headers.Authorization = `Bearer ${token}`;
            return apiClient(originalRequest);
          })
          .catch((err) => Promise.reject(err));
      }

      originalRequest._retry = true;
      isRefreshing = true;

      const refreshToken = localStorage.getItem('refreshToken');
      if (!refreshToken) {
        isRefreshing = false;
        return Promise.reject(error);
      }

      try {
        const res = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken });
        const { accessToken: newAccess, refreshToken: newRefresh } = res.data;
        
        localStorage.setItem('accessToken', newAccess);
        localStorage.setItem('refreshToken', newRefresh);
        
        apiClient.defaults.headers.common.Authorization = `Bearer ${newAccess}`;
        processQueue(null, newAccess);
        isRefreshing = false;

        originalRequest.headers.Authorization = `Bearer ${newAccess}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        isRefreshing = false;
        
        // Refresh token expired or invalid: logout user
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.dispatchEvent(new Event('auth-logout'));
        
        return Promise.reject(refreshError);
      }
    }

    return Promise.reject(error);
  }
);

// API Endpoints Mapping
export const api = {
  // Auth Module
  auth: {
    register: (data: any) => apiClient.post('/auth/register', data),
    login: (data: any) => apiClient.post('/auth/login', data),
    setup2fa: () => apiClient.post('/auth/2fa/setup'),
    verify2fa: (code: string) => apiClient.post('/auth/2fa/verify', { code }),
    validate2fa: (data: any) => apiClient.post('/auth/2fa/validate', data),
    logout: () => apiClient.post('/auth/logout').finally(() => {
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
    }),
  },

  // Account Module
  accounts: {
    create: (accountType: AccountType, initialDeposit: number, kycDocReference?: string, fixedDepositMaturityMonths?: number) => 
      apiClient.post<Account>('/accounts', { accountType, initialDeposit, kycDocReference, fixedDepositMaturityMonths }),
    getMy: () => apiClient.get<Account[]>('/accounts/my'),
    getDetails: (id: string) => apiClient.get<Account>(`/accounts/${id}`),
    getBalance: (id: string) => apiClient.get<{ balance: number }>(`/accounts/${id}/balance`),
    getMiniStatement: (id: string) => apiClient.get<Transaction[]>(`/accounts/${id}/mini-statement`),
  },

  // Transaction Module
  transactions: {
    transfer: (data: {
      fromAccountId: string;
      toAccountNumber: string;
      interBank: boolean;
      bankCode?: string;
      amount: number;
      transactionPin: string;
      totpCode?: string;
      description?: string;
    }, idempotencyKey?: string) => {
      const headers = idempotencyKey ? { 'idempotency-key': idempotencyKey } : {};
      return apiClient.post<Transaction>('/transactions/transfer', data, { headers });
    },
    deposit: (data: { accountId: string; amount: number; description?: string }, idempotencyKey?: string) => {
      const headers = idempotencyKey ? { 'idempotency-key': idempotencyKey } : {};
      return apiClient.post<Transaction>('/transactions/deposit', data, { headers });
    },
    withdraw: (data: { accountId: string; amount: number; transactionPin: string; description?: string }, idempotencyKey?: string) => {
      const headers = idempotencyKey ? { 'idempotency-key': idempotencyKey } : {};
      return apiClient.post<Transaction>('/transactions/withdraw', data, { headers });
    },
    getHistory: (accountId: string, page = 0, size = 10) => 
      apiClient.get<any>(`/transactions/history`, { params: { accountId, page, size } }),
    getDetails: (refNo: string) => apiClient.get<Transaction>(`/transactions/${refNo}`),
  },

  // Loan Module
  loans: {
    apply: (data: {
      accountId: string;
      loanType: LoanType;
      principal: number;
      interestRate: number;
      tenureMonths: number;
    }) => apiClient.post<Loan>('/loans/apply', data),
    getMy: () => apiClient.get<Loan[]>('/loans/my'),
    repay: (loanId: string, amount: number, transactionPin: string) => 
      apiClient.post<Loan>(`/loans/${loanId}/repay`, { amount, transactionPin }),
    getSchedule: (loanId: string) => apiClient.get<EmiScheduleItem[]>(`/loans/${loanId}/emi-schedule`),
  },

  // Admin Module
  admin: {
    getUsers: (page = 0, size = 10) => apiClient.get<any>('/admin/users', { params: { page, size } }),
    freezeAccount: (id: string) => apiClient.put<Account>(`/admin/accounts/${id}/freeze`),
    unfreezeAccount: (id: string) => apiClient.put<Account>(`/admin/accounts/${id}/unfreeze`),
    getTransactions: (page = 0, size = 10) => apiClient.get<any>('/admin/transactions', { params: { page, size } }),
    approveLoan: (id: string) => apiClient.post<Loan>(`/admin/loans/${id}/approve`),
    rejectLoan: (id: string) => apiClient.post<Loan>(`/admin/loans/${id}/reject`),
    getLoans: () => apiClient.get<Loan[]>('/admin/loans'),
    getStats: () => apiClient.get<DashboardStats>('/admin/dashboard/stats'),
  }
};

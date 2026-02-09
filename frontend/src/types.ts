export interface User {
  id: string;
  email: string;
  fullName: string;
  phone?: string;
  role: 'CUSTOMER' | 'ADMIN';
  twoFaEnabled: boolean;
}

export type AccountType = 'SAVINGS' | 'CURRENT' | 'FIXED_DEPOSIT';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';

export interface Account {
  id: string;
  userId: string;
  accountNumber: string;
  accountType: AccountType;
  balance: number;
  currency: string;
  dailyTransferLimit: number;
  status: AccountStatus;
  interestRate: number;
  maturityDate?: string;
  kycDocReference?: string;
  createdAt: string;
}

export type TransactionType = 'TRANSFER' | 'DEPOSIT' | 'WITHDRAWAL' | 'EMI';
export type TransactionStatus = 'PENDING' | 'SUCCESS' | 'FAILED' | 'REVERSED';

export interface Transaction {
  id: string;
  referenceNumber: string;
  amount: number;
  type: TransactionType;
  status: TransactionStatus;
  description: string;
  initiatedAt: string;
  completedAt?: string;
  fromAccountNumber?: string;
  toAccountNumber?: string;
}

export type LoanType = 'HOME' | 'PERSONAL' | 'CAR';
export type LoanStatus = 'PENDING' | 'APPROVED' | 'ACTIVE' | 'CLOSED' | 'REJECTED';

export interface Loan {
  id: string;
  userId: string;
  accountId: string;
  accountNumber: string;
  loanType: LoanType;
  principal: number;
  interestRate: number;
  tenureMonths: number;
  emiAmount: number;
  outstandingAmount: number;
  status: LoanStatus;
  nextDueDate?: string;
  createdAt: string;
}

export interface EmiScheduleItem {
  installmentNo: number;
  emi: number;
  interest: number;
  principalPaid: number;
  remainingBalance: number;
  dueDate: string;
}

export interface DashboardStats {
  totalUsers: number;
  totalAccounts: number;
  totalBalance: number;
  dailyTransactionVolume: number;
}

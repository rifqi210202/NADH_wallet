export interface FileNode {
  name: string;
  type: 'file' | 'dir';
  path: string;
  children?: FileNode[];
  language?: string;
  content?: string;
}

export interface WalletState {
  balance: number;
  userId: string;
  userName: string;
  email: string;
  phone: string;
  walletId: string;
  isVerified: boolean;
  hasPin: boolean;
  pinHash: string;
  transactions: TransactionRecord[];
  auditLogs: AuditLogRecord[];
  paymentRequests: PaymentRequestRecord[];
}

export interface TransactionRecord {
  id: string;
  type: 'TOPUP' | 'TRANSFER_IN' | 'TRANSFER_OUT' | 'PAYMENT' | 'REFUND';
  amount: number;
  status: 'PENDING' | 'SUCCESS' | 'FAILED';
  date: string;
  senderName?: string;
  senderNum?: string;
  recipientName?: string;
  recipientNum?: string;
  description: string;
  referenceId?: string;
}

export interface AuditLogRecord {
  id: string;
  action: string;
  timestamp: string;
  details: string;
  ipAddress: string;
  status: 'SUCCESS' | 'FAILURE';
}

export interface PaymentRequestRecord {
  id: string;
  amount: number;
  merchantName: string;
  orderId: string;
  status: 'PENDING' | 'PAID' | 'EXPIRED' | 'CANCELLED';
  createdTime: string;
  callbackUrl: string;
}

import { useState, useEffect } from 'react';
import { 
  Smartphone, 
  Database, 
  Code, 
  ShieldCheck, 
  Layers, 
  LogOut, 
  ArrowRightLeft, 
  PiggyBank, 
  CreditCard, 
  CheckCircle2, 
  AlertCircle, 
  RotateCw, 
  Play, 
  Copy, 
  Check, 
  Terminal, 
  User, 
  Lock, 
  Shield, 
  Info, 
  X, 
  ChevronRight, 
  Menu, 
  ArrowUpRight, 
  ArrowDownLeft, 
  Bell,
  Eye,
  EyeOff,
  Briefcase,
  ExternalLink,
  SmartphoneIcon,
  HardDrive
} from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { kotlinFiles, AndroidFile } from './data/kotlinFiles';
import { pgsqlSchema } from './data/pgsqlSchema';
import { expressCode } from './data/expressCode';
import { 
  WalletState, 
  TransactionRecord, 
  AuditLogRecord, 
  PaymentRequestRecord 
} from './types';

// Helper to format currency
const formatToRupiah = (val: number): string => {
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    minimumFractionDigits: 0
  }).format(val);
};

export default function App() {
  // Navigation Tabs
  const [activeTab, setActiveTab] = useState<'emulator' | 'schema' | 'kotlin' | 'backend' | 'guide' | 'security'>('emulator');
  
  // App Core State (Simulated Local Engine synced between interactive DB & phone)
  const [state, setState] = useState<WalletState>({
    balance: 2750000,
    userId: '8ca5-d72b-4e12-b13c-0e73e91a0c84',
    userName: 'Rifqi Nadhir Altaz',
    email: 'rifqinadhiraltaz25@gmail.com',
    phone: '081234567890',
    walletId: '889981234567890',
    isVerified: true,
    hasPin: true,
    pinHash: 'hashed_123456', // Simulated pin is 123456
    transactions: [
      {
        id: 'tx-001',
        type: 'TOPUP',
        amount: 500000,
        status: 'SUCCESS',
        date: '2026-05-19 14:30:22',
        description: 'Top up Balance via Virtual Account Bank Mandiri',
        referenceId: 'TOP-192837482'
      },
      {
        id: 'tx-002',
        type: 'TRANSFER_OUT',
        amount: 150000,
        status: 'SUCCESS',
        date: '2026-05-19 16:45:10',
        senderName: 'Rifqi Nadhir Altaz',
        recipientName: 'Ahmad Fauzi',
        recipientNum: '889987654321098',
        description: 'Transfer ke 889987654321098 (Ahmad Fauzi)',
        referenceId: 'TRF-928374635'
      },
      {
        id: 'tx-003',
        type: 'PAYMENT',
        amount: 89000,
        status: 'SUCCESS',
        date: '2026-05-20 09:12:05',
        description: 'Pembayaran Order Website #INV-92831',
        referenceId: 'PAY-INV-92831-20938'
      }
    ],
    auditLogs: [
      {
        id: 'log-001',
        action: 'AUTH_LOGIN',
        timestamp: '2026-05-20T04:12:00Z',
        details: 'User rifqinadhiraltaz25@gmail.com logged in successfully via Android App',
        ipAddress: '182.253.14.72',
        status: 'SUCCESS'
      },
      {
        id: 'log-002',
        action: 'PIN_VERIFY',
        timestamp: '2026-05-20T04:13:45Z',
        details: 'Transaction PIN validation succeeded for eWallet transaction payment',
        ipAddress: '182.253.14.72',
        status: 'SUCCESS'
      }
    ],
    paymentRequests: [
      {
        id: 'pay-req-101',
        amount: 245000,
        merchantName: 'Mitra Belanja Toko Utama',
        orderId: 'INV-883712',
        status: 'PENDING',
        createdTime: '2026-05-20T04:30:00Z',
        callbackUrl: 'https://main-website.com/api/payment-callback'
      },
      {
        id: 'pay-req-102',
        amount: 115000,
        merchantName: 'Warung Gadget Nusantara',
        orderId: 'INV-726190',
        status: 'PENDING',
        createdTime: '2026-05-20T04:45:00Z',
        callbackUrl: 'https://main-website.com/api/payment-callback'
      }
    ]
  });

  // Emulator UI Interactive states
  const [phoneScreen, setPhoneScreen] = useState<'splash' | 'login' | 'otp' | 'home' | 'transfer' | 'topup' | 'pay_website' | 'pin_settings' | 'notification'>('splash');
  const [loginPhone, setLoginPhone] = useState('081234567890');
  const [loginPassword, setLoginPassword] = useState('password123');
  const [otpInput, setOtpInput] = useState('');
  const [isOtpLoading, setIsOtpLoading] = useState(false);
  
  // Transfer flow states
  const [transferTarget, setTransferTarget] = useState('');
  const [isValidatingRecipient, setIsValidatingRecipient] = useState(false);
  const [validatedRecipientName, setValidatedRecipientName] = useState<string | null>(null);
  const [transferAmount, setTransferAmount] = useState('');
  const [transferPin, setTransferPin] = useState('');
  const [pinVisible, setPinVisible] = useState(false);
  const [transferError, setTransferError] = useState('');
  const [transferSuccess, setTransferSuccess] = useState(false);

  // Top Up flow states
  const [topupAmount, setTopupAmount] = useState('100000');
  const [topupSuccess, setTopupSuccess] = useState(false);

  // Pay Website invoice states
  const [selectedInvoice, setSelectedInvoice] = useState<PaymentRequestRecord | null>(null);
  const [invoicePayPin, setInvoicePayPin] = useState('');
  const [invoicePaySuccess, setInvoicePaySuccess] = useState(false);
  const [invoicePayError, setInvoicePayError] = useState('');
  const [checkoutSyncStatus, setCheckoutSyncStatus] = useState('');

  // Pin Settings states
  const [currentPinInput, setCurrentPinInput] = useState('');
  const [newPinInput, setNewPinInput] = useState('');
  const [confirmPinInput, setConfirmPinInput] = useState('');
  const [pinSettingsError, setPinSettingsError] = useState('');
  const [pinSettingsSuccess, setPinSettingsSuccess] = useState('');

  // Sandbox API panel states
  const [sandboxEndpoint, setSandboxEndpoint] = useState<'register' | 'login' | 'transfer' | 'pin-verify' | 'payment-pay' | 'webhook'>('transfer');
  const [sandboxPayload, setSandboxPayload] = useState<string>('');
  const [sandboxResponse, setSandboxResponse] = useState<any>(null);
  const [sandboxSqlLogs, setSandboxSqlLogs] = useState<string[]>([]);
  const [isSandboxExecuting, setIsSandboxExecuting] = useState(false);

  // SQL tables details state for SQL explorer
  const [selectedSchemaTable, setSelectedSchemaTable] = useState<string>('wallets');

  // Copy indicator state
  const [copiedText, setCopiedText] = useState<string | null>(null);
  const [selectedKotlinFile, setSelectedKotlinFile] = useState<AndroidFile>(kotlinFiles[0]);

  // Notifications
  const [notificationMsg, setNotificationMsg] = useState<{title: string, desc: string} | null>(null);

  const handleCopy = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopiedText(label);
    setTimeout(() => setCopiedText(null), 1500);
  };

  const addAuditLog = (action: string, details: string, ip: string, success: boolean) => {
    const newLog: AuditLogRecord = {
      id: `log-${Math.floor(1000 + Math.random() * 9000)}`,
      action,
      timestamp: new Date().toISOString(),
      details,
      ipAddress: ip,
      status: success ? 'SUCCESS' : 'FAILURE'
    };
    setState(prev => ({
      ...prev,
      auditLogs: [newLog, ...prev.auditLogs]
    }));
  };

  // Trigger custom in-app notifications
  const triggerInAppNotification = (title: string, desc: string) => {
    setNotificationMsg({ title, desc });
    setTimeout(() => {
      setNotificationMsg(null);
    }, 4500);
  };

  // Sync sandbox state with client data on change
  useEffect(() => {
    let payload = '';
    if (sandboxEndpoint === 'transfer') {
      payload = JSON.stringify({
        recipientWalletNumber: '889987654321098',
        amount: 250000,
        pin: '123456',
        idempotencyKey: `trf-key-${Math.floor(100000 + Math.random() * 900000)}`
      }, null, 2);
    } else if (sandboxEndpoint === 'register') {
      payload = JSON.stringify({
        fullName: 'Rifqi Nadhir Altaz',
        email: 'rifqinadhiraltaz25@gmail.com',
        phoneNumber: '081234567890',
        password: 'securePassword123'
      }, null, 2);
    } else if (sandboxEndpoint === 'login') {
      payload = JSON.stringify({
        phoneNumber: '081234567890',
        password: 'securePassword123'
      }, null, 2);
    } else if (sandboxEndpoint === 'pin-verify') {
      payload = JSON.stringify({
        pin: '123456'
      }, null, 2);
    } else if (sandboxEndpoint === 'payment-pay') {
      payload = JSON.stringify({
        paymentRequestId: state.paymentRequests[0]?.id || 'pay-req-101',
        pin: '123456'
      }, null, 2);
    } else if (sandboxEndpoint === 'webhook') {
      payload = JSON.stringify({
        merchant_order_id: 'INV-883712',
        paymentStatus: 'SUCCESS',
        amount: 245000,
        walletTransactionId: 'tx-204124931',
        signature: '6e4fa83fcfecbd028bc13463a5ef598cc1'
      }, null, 2);
    }
    setSandboxPayload(payload);
  }, [sandboxEndpoint, state.paymentRequests]);

  // Execute Simulated API Endpoint
  const handleExecuteSandbox = () => {
    setIsSandboxExecuting(true);
    setTimeout(() => {
      try {
        const parsedBody = JSON.parse(sandboxPayload);
        let response: any = {};
        let sqls: string[] = [];

        if (sandboxEndpoint === 'transfer') {
          sqls = [
            '-- Start Atomic Isolation Level Transaction',
            'BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;',
            `-- Check Idempotency Key matching reference_id = '${parsedBody.idempotencyKey || 'test-key'}'`,
            `SELECT id FROM wallet_transactions WHERE reference_id = '${parsedBody.idempotencyKey || 'test-key'}';`,
            `-- Lock user wallet and check balance (FOR UPDATE to prevent concurrently modified lock race condition)`,
            `SELECT w.*, p.pin_hash FROM wallets w JOIN wallet_pins p ON w.id = p.wallet_id WHERE w.user_id = '${state.userId}' FOR UPDATE;`,
            `-- Select and lock recipient wallet by wallet number`,
            `SELECT * FROM wallets WHERE wallet_number = '${parsedBody.recipientWalletNumber}' FOR UPDATE;`,
            `-- Verify encrypted bcrypt pin compatibility`,
            `-- UPDATE sender balance`,
            `UPDATE wallets SET balance = balance - ${parsedBody.amount}, updated_at = NOW() WHERE user_id = '${state.userId}';`,
            `-- UPDATE recipient balance`,
            `UPDATE wallets SET balance = balance + ${parsedBody.amount}, updated_at = NOW() WHERE wallet_number = '${parsedBody.recipientWalletNumber}';`,
            `-- Insert Immutable doubleledger records`,
            `INSERT INTO wallet_transactions (reference_id, wallet_id, type, amount, status, description) VALUES ('${parsedBody.idempotencyKey}', 'W-SENDER-ID', 'TRANSFER', ${parsedBody.amount}, 'SUCCESS', 'Transfer to ${parsedBody.recipientWalletNumber}');`,
            `INSERT INTO audit_logs (user_id, action, status) VALUES ('${state.userId}', 'TRANSFER_BALANCE', 'SUCCESS');`,
            'COMMIT;'
          ];

          if (parseFloat(parsedBody.amount) > state.balance) {
            response = {
              status: 'FAILED',
              message: 'Transaksi dibatalkan: Saldo eWallet pengirim tidak mencukupi.',
              code: '400_INSUFFICIENT_BALANCE'
            };
            addAuditLog('TRANSFER_FAILED', `Simulated transfer failed due to insufficient balance of ${parsedBody.amount}`, '127.0.0.1', false);
          } else if (parsedBody.pin !== '123456') {
            response = {
              status: 'FAILED',
              message: 'Transaksi ditolak: PIN keamanan salah.',
              code: '403_INVALID_PIN'
            };
            addAuditLog('TRANSFER_FAILED_PIN', 'Simulated transfer failed due to incorrect PIN input', '127.0.0.1', false);
          } else {
            response = {
              status: 'SUCCESS',
              message: 'Transfer peer-to-peer berhasil diproses.',
              transactionId: `tx-${Math.floor(100000 + Math.random() * 900000)}`,
              data: {
                sender: state.walletId,
                recipient: parsedBody.recipientWalletNumber,
                amount: parsedBody.amount,
                idempotencyKey: parsedBody.idempotencyKey,
                fee: 0,
                timestamp: new Date().toISOString()
              }
            };
            // Balance update
            setState(prev => ({
              ...prev,
              balance: prev.balance - parseFloat(parsedBody.amount),
              transactions: [
                {
                  id: `tx-${Math.floor(100000 + Math.random() * 900000)}`,
                  type: 'TRANSFER_OUT',
                  amount: parseFloat(parsedBody.amount),
                  status: 'SUCCESS',
                  date: new Date().toISOString().replace('T', ' ').substring(0, 19),
                  description: `Transfer ke ${parsedBody.recipientWalletNumber}`
                },
                ...prev.transactions
              ]
            }));
            addAuditLog('TRANSFER_BALANCE', `Simulated P2P transfer of ${parsedBody.amount} to wallet ${parsedBody.recipientWalletNumber}`, '127.0.0.1', true);
            triggerInAppNotification('Transfer Dikirim', `Saldo Anda berkurang ${formatToRupiah(parsedBody.amount)}`);
          }
        } 
        else if (sandboxEndpoint === 'register') {
          sqls = [
            '-- Check if phone or email already holds active record',
            `SELECT id FROM users WHERE email = '${parsedBody.email}' OR phone_number = '${parsedBody.phoneNumber}';`,
            '-- Hash plain text password using bcrypt high-factor cost round',
            `INSERT INTO users (full_name, email, phone_number, password_hash) VALUES ('${parsedBody.fullName}', '${parsedBody.email}', '${parsedBody.phoneNumber}', '$2b$12$...encrypted_hash...') RETURNING id;`,
            '-- Auto bootstrap associated single primary eWallet matching user id',
            `INSERT INTO wallets (user_id, wallet_number, balance) VALUES ('RET_USER_ID', '8899' || RIGHT('${parsedBody.phoneNumber}', 11), 0.00);`,
            `INSERT INTO audit_logs (action, details, status) VALUES ('AUTH_REGISTER', 'User registration completed for ${parsedBody.email}', 'SUCCESS');`
          ];
          response = {
            status: 'SUCCESS',
            message: 'Registrasi akun pengguna baru berhasil diproses. Silakan verifikasi kode OTP.',
            data: {
              userId: 'u-55294-fba8',
              email: parsedBody.email,
              phone: parsedBody.phoneNumber,
              isVerified: false,
              walletCreated: `8899${parsedBody.phoneNumber.substring(Math.max(0, parsedBody.phoneNumber.length - 11))}`
            }
          };
          addAuditLog('AUTH_REGISTER', `Created new user endpoint record for ${parsedBody.email}`, '127.0.0.1', true);
        }
        else if (sandboxEndpoint === 'login') {
          sqls = [
            `SELECT * FROM users WHERE phone_number = '${parsedBody.phoneNumber}';`,
            `-- Verify password hash utilizing secure comparative argon2/bcrypt`,
            '-- Generate and sign Auth Token with JWT Short Expiry (15m)',
            `-- Check refresh token database for rotation storage`,
            `INSERT INTO refresh_tokens (user_id, token_string, expires_at) VALUES ('USER_ID', 'jwt_refresh_str', NOW() + INTERVAL '7 days');`,
            `INSERT INTO audit_logs (user_id, action, status) VALUES ('USER_ID', 'AUTH_LOGIN', 'SUCCESS');`
          ];
          response = {
            status: 'SUCCESS',
            message: 'Otentikasi berhasil. Sesi diizinkan.',
            accessToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjFhMmIzYyIsImV...ACCESS_TOKEN',
            refreshToken: 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpZCI6IjFhMm...REFRESH_TOKEN',
            expiresIn: 900 // 15 Minutes
          };
          addAuditLog('AUTH_LOGGED_IN', `User authenticate login with hp ${parsedBody.phoneNumber}`, '127.0.0.1', true);
        }
        else if (sandboxEndpoint === 'pin-verify') {
          sqls = [
            `SELECT pin_hash FROM wallet_pins JOIN wallets ON wallets.id = wallet_pins.wallet_id WHERE wallets.user_id = '${state.userId}';`
          ];
          if (parsedBody.pin === '123456') {
            response = { status: 'SUCCESS', verified: true, message: 'PIN terverifikasi.' };
            addAuditLog('PIN_VERIFIED', 'Secure transaction PIN code verified successfully', '127.0.0.1', true);
          } else {
            response = { status: 'FAILED', verified: false, message: 'PIN transaksi salah. Sisa percobaan 2 kali lagi sebelum dikunci.' };
            addAuditLog('PIN_VERIFY_FAILED', 'Secure pin execution failed', '127.0.0.1', false);
          }
        }
        else if (sandboxEndpoint === 'payment-pay') {
          const invId = parsedBody.paymentRequestId;
          const invoiceItem = state.paymentRequests.find(p => p.id === invId);
          sqls = [
            `-- Lock payment invoice and evaluate status security`,
            `SELECT * FROM wallet_payment_requests WHERE id = '${invId}' FOR UPDATE;`,
            `-- Lock debtor wallet balance`,
            `SELECT w.*, p.pin_hash FROM wallets w JOIN wallet_pins p ON w.id = p.wallet_id WHERE w.user_id = '${state.userId}' FOR UPDATE;`,
            `-- Subtract balance from debtor`,
            `UPDATE wallets SET balance = balance - ${invoiceItem?.amount || 0} WHERE id = 'W-DEBTOR-ID';`,
            `-- Insert transaction ledger`,
            `INSERT INTO wallet_transactions (reference_id, wallet_id, type, amount, status, description) VALUES ('PAY-${invId}-REF', 'W-DEBTOR-ID', 'PAYMENT', ${invoiceItem?.amount || 0}, 'SUCCESS', 'Payment of order #${invoiceItem?.orderId || 'MOCK'}');`,
            `-- Update invoice state to PAID`,
            `UPDATE wallet_payment_requests SET status = 'PAID', updated_at = NOW() WHERE id = '${invId}';`,
            'COMMIT;'
          ];

          if (invoiceItem) {
            if (state.balance < invoiceItem.amount) {
              response = { status: 'FAILED', message: 'Saldo tidak mencukupi untuk melunasi tagihan website.' };
              addAuditLog('PAYMENT_INV_FAILED', `Simulated invoice pay of ${invoiceItem.amount} failed (insufficient balance)`, '127.0.0.1', false);
            } else if (parsedBody.pin !== '123456') {
              response = { status: 'FAILED', message: 'Konfirmasi dibatalkan: PIN eWallet salah.' };
              addAuditLog('PAYMENT_INV_FAILED', `Failed invoice payment code pin error`, '127.0.0.1', false);
            } else {
              response = {
                status: 'SUCCESS',
                message: 'Invoice order berhasil dibayar. Callback webhook dikirimkan ke server utama.',
                callbackResult: {
                  webhookUrl: invoiceItem.callbackUrl,
                  httpStatus: '200_OK',
                  payloadSent: {
                    orderId: invoiceItem.orderId,
                    paymentStatus: 'SUCCESS',
                    amount: invoiceItem.amount,
                    signature: '6e4fa83fcfecbd028bc12...'
                  }
                }
              };
              // Perform update in state
              setState(prev => ({
                ...prev,
                balance: prev.balance - invoiceItem.amount,
                paymentRequests: prev.paymentRequests.map(p => p.id === invId ? { ...p, status: 'PAID' } : p),
                transactions: [
                  {
                    id: `tx-${Math.floor(100000 + Math.random() * 900000)}`,
                    type: 'PAYMENT',
                    amount: invoiceItem.amount,
                    status: 'SUCCESS',
                    date: new Date().toISOString().replace('T', ' ').substring(0, 19),
                    description: `Pembayaran Order Website #${invoiceItem.orderId}`
                  },
                  ...prev.transactions
                ]
              }));
              addAuditLog('PAYMENT_INV_CONFIRM', `Successfully paid invoice order #${invoiceItem.orderId} of ${invoiceItem.amount}. Webhook successfully delivered.`, '127.0.0.1', true);
              triggerInAppNotification('Tagihan Terbayar', `Pembayaran order #${invoiceItem.orderId} sukses.`);
            }
          } else {
            response = { status: 'FAILED', message: 'Invoice tidak valid.' };
          }
        }
        else if (sandboxEndpoint === 'webhook') {
          sqls = [
            `-- Website receives callback and validates Signature`,
            `-- SELECT order_id FROM website_orders WHERE order_id = '${parsedBody.merchant_order_id}';`,
            `-- Atomic Update Order Status to SUCCESS`,
            `UPDATE website_orders SET payment_status = 'PAID', ewallet_ref = '${parsedBody.walletTransactionId}', updated_at = NOW() WHERE order_id = '${parsedBody.merchant_order_id}';`
          ];
          response = {
            status: 'RECEIVED',
            message: 'Website utama berhasil mendeteksi callback IPN (Instant Payment Notification). Status order otomatis diubah ke PAID.',
            httpResponseCode: 200,
            databaseUpdated: true,
            orderUpdated: parsedBody.merchant_order_id
          };
          addAuditLog('WEBHOOK_SIMULATION_RECEIVED', `Website core captured callback IPN webhook confirm for order #${parsedBody.merchant_order_id}`, 'Website-Hosting-IP', true);
        }

        setSandboxResponse(response);
        setSandboxSqlLogs(sqls);
      } catch (err) {
        setSandboxResponse({ status: 'FAILED', message: 'Invalid JSON payload format.' });
      }
      setIsSandboxExecuting(false);
    }, 700);
  };

  // Simulated OTP Trigger
  const triggerOtpVerif = () => {
    setIsOtpLoading(true);
    setTimeout(() => {
      setIsOtpLoading(false);
      setPhoneScreen('otp');
      triggerInAppNotification('Kode OTP Terkirim', 'Kode OTP eWallet: 2509 telah dikirimkan ke email Anda.');
    }, 1000);
  };

  // Perform transfer on Simulated phone Emulator
  const handleEmulatorTransfer = () => {
    setTransferError('');
    if (!transferTarget) {
      setTransferError('Nomor Wallet / HP / Email wajib diisi');
      return;
    }
    const amt = parseFloat(transferAmount);
    if (!amt || amt <= 0) {
      setTransferError('Nominal transfer harus lebih besar dari Rp 0');
      return;
    }
    if (amt > state.balance) {
      setTransferError('Saldo Anda tidak mencukupi untuk melakukan transfer');
      return;
    }
    if (transferPin !== '123456') {
      setTransferError('PIN Keamanan eWallet salah kawan. Coba hubungkan real/silakan cek pin default: 123456');
      return;
    }

    // Process local transfer
    setState(prev => ({
      ...prev,
      balance: prev.balance - amt,
      transactions: [
        {
          id: `tx-${Math.floor(100000 + Math.random() * 900000)}`,
          type: 'TRANSFER_OUT',
          amount: amt,
          status: 'SUCCESS',
          date: new Date().toISOString().replace('T', ' ').substring(0, 19),
          description: `Transfer ke ${transferTarget} (${validatedRecipientName || 'Suryadi Buono'})`,
          referenceId: `TRF-${Math.floor(100000000 + Math.random() * 900000000)}`
        },
        ...prev.transactions
      ]
    }));

    addAuditLog('TRANSFER_BALANCE', `App Emulator - Transfer ${amt} to recipient ${transferTarget}`, 'Android-App-Emulator', true);
    setTransferSuccess(true);
    triggerInAppNotification('Transfer Sukses', `Rp ${amt.toLocaleString('id-ID')} berhasil ditransfer.`);
  };

  const handleValidateRecipient = () => {
    if (!transferTarget) return;
    setIsValidatingRecipient(true);
    setTimeout(() => {
      setIsValidatingRecipient(false);
      if (transferTarget.includes('876543') || transferTarget.includes('fauzi') || transferTarget.includes('Ahmad')) {
        setValidatedRecipientName('Ahmad Fauzi');
      } else if (transferTarget.includes('0811') || transferTarget.includes('suryadi')) {
        setValidatedRecipientName('Suryadi Buono');
      } else {
        setValidatedRecipientName('Budi Kusuma');
      }
    }, 800);
  };

  // Perform top up on emulator
  const handleEmulatorTopup = () => {
    const amt = parseFloat(topupAmount);
    if (!amt || amt <= 0) return;

    setState(prev => ({
      ...prev,
      balance: prev.balance + amt,
      transactions: [
        {
          id: `tx-${Math.floor(100000 + Math.random() * 900000)}`,
          type: 'TOPUP',
          amount: amt,
          status: 'SUCCESS',
          date: new Date().toISOString().replace('T', ' ').substring(0, 19),
          description: `Top up via VA Bank Transfer`,
          referenceId: `TOP-${Math.floor(100000000 + Math.random() * 900000000)}`
        },
        ...prev.transactions
      ]
    }));

    addAuditLog('TOPUP_BALANCE', `App Emulator - Topup balance value RP ${amt}`, 'Android-App-Emulator', true);
    setTopupSuccess(true);
    triggerInAppNotification('Top Up Berhasil', `Saldo Anda bertambah ${formatToRupiah(amt)}`);
  };

  // Perform user pay invoice on emulator
  const handleCreateWebsiteCheckout = async () => {
    setCheckoutSyncStatus('Membuat tagihan checkout NADH_Wallet...');
    const amount = Math.floor(75000 + Math.random() * 350000);
    const merchantOrderId = `INV-${Math.floor(100000 + Math.random() * 900000)}`;

    try {
      const response = await fetch('/api/wallet/payment-requests', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          merchantName: 'NADH Store Checkout',
          merchantOrderId,
          amount,
          customerEmail: state.email,
          callbackUrl: `${window.location.origin}/api/payment-callback`,
        }),
      });
      const payload = await response.json();

      if (!response.ok || !payload.success) {
        throw new Error(payload.error || 'Gagal membuat tagihan checkout.');
      }

      const apiInvoice = payload.data;
      const nextInvoice: PaymentRequestRecord = {
        id: apiInvoice.id,
        amount: apiInvoice.amount,
        merchantName: apiInvoice.merchantName,
        orderId: apiInvoice.merchantOrderId,
        status: apiInvoice.status,
        createdTime: new Date().toISOString(),
        callbackUrl: `${window.location.origin}/api/payment-callback`,
      };

      setState(prev => ({
        ...prev,
        paymentRequests: [nextInvoice, ...prev.paymentRequests.filter(item => item.id !== nextInvoice.id)]
      }));
      setCheckoutSyncStatus(`Tagihan ${merchantOrderId} sudah dikirim ke API. Refresh APK untuk melihatnya.`);
      addAuditLog('CHECKOUT_WALLET_REQUEST', `Website checkout created NADH_Wallet invoice ${merchantOrderId}`, 'Website-Localhost', true);
      triggerInAppNotification('Tagihan Baru', `Order ${merchantOrderId} siap dibayar di APK.`);
    } catch (error: any) {
      setCheckoutSyncStatus(error.message || 'API belum aktif. Jalankan npm run dev:all.');
      addAuditLog('CHECKOUT_WALLET_FAILED', 'Website checkout failed to create NADH_Wallet invoice', 'Website-Localhost', false);
    }
  };

  const handleEmulatorPayInvoice = () => {
    setInvoicePayError('');
    if (!selectedInvoice) return;
    if (invoicePayPin !== '123456') {
      setInvoicePayError('PIN eWallet salah. Silakan coba default: 123456');
      return;
    }
    if (state.balance < selectedInvoice.amount) {
      setInvoicePayError('Saldo Anda tidak mencukupi untuk bayar order ini');
      return;
    }

    const amt = selectedInvoice.amount;
    const invId = selectedInvoice.id;

    setState(prev => ({
      ...prev,
      balance: prev.balance - amt,
      paymentRequests: prev.paymentRequests.map(p => p.id === invId ? { ...p, status: 'PAID' } : p),
      transactions: [
        {
          id: `tx-${Math.floor(100000 + Math.random() * 900000)}`,
          type: 'PAYMENT',
          amount: amt,
          status: 'SUCCESS',
          date: new Date().toISOString().replace('T', ' ').substring(0, 19),
          description: `Pembayaran Order #${selectedInvoice.orderId}`,
          referenceId: `PAY-INV-${selectedInvoice.orderId}`
        },
        ...prev.transactions
      ]
    }));

    addAuditLog('PAYMENT_INV_CONFIRM', `App Emulator - Paid Invoice #${selectedInvoice.orderId} for amount ${amt}`, 'Android-App-Emulator', true);
    setInvoicePaySuccess(true);
    triggerInAppNotification('Bill Terbayar', `Tagihan order #${selectedInvoice.orderId} lunas.`);
  };

  // Change PIN inside emulator settings
  const handleUpdatePinEmulator = () => {
    setPinSettingsError('');
    setPinSettingsSuccess('');

    if (currentPinInput !== '123456') {
      setPinSettingsError('PIN Lama salah!');
      return;
    }
    if (newPinInput.length !== 6 || isNaN(Number(newPinInput))) {
      setPinSettingsError('PIN Baru harus berupa 6 digit angka');
      return;
    }
    if (newPinInput !== confirmPinInput) {
      setPinSettingsError('Konfirmasi PIN Baru tidak sesuai');
      return;
    }

    setState(prev => ({
      ...prev,
      pinHash: `hashed_${newPinInput}`
    }));

    setNewPinInput('');
    setCurrentPinInput('');
    setConfirmPinInput('');
    setPinSettingsSuccess('PIN eWallet Anda Berhasil diperbarui!');
    addAuditLog('PIN_CHANGED', 'User successfully changed account wallet PIN securely', 'Android-App-Emulator', true);
    triggerInAppNotification('PIN Diubah', 'PIN eWallet berhasil diganti.');
  };

  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 font-sans selection:bg-indigo-500 selection:text-white pb-12">
      
      {/* Dynamic Notification Toast */}
      <AnimatePresence>
        {notificationMsg && (
          <motion.div 
            initial={{ opacity: 0, y: -50, scale: 0.9 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -20, scale: 0.9 }}
            className="fixed top-6 right-6 z-50 bg-emerald-600 text-white px-5 py-4 rounded-xl shadow-2xl flex items-center space-x-3 max-w-sm border border-emerald-500"
          >
            <CheckCircle2 className="w-6 h-6 shrink-0" />
            <div>
              <p className="font-bold text-sm">{notificationMsg.title}</p>
              <p className="text-xs text-emerald-100 mt-0.5">{notificationMsg.desc}</p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {/* Top Professional Banner Header */}
      <header className="border-b border-slate-800 bg-slate-950/80 backdrop-blur-md sticky top-0 z-40 px-6 py-4">
        <div className="max-w-7xl mx-auto flex flex-col md:flex-row items-start md:items-center justify-between gap-4">
          <div className="flex items-center space-x-3.5">
            <div className="p-2.5 bg-gradient-to-br from-indigo-500 to-indigo-600 rounded-xl shadow-lg border border-indigo-400/20">
              <Briefcase className="w-6 h-6 text-white" />
            </div>
            <div>
              <div className="flex items-center space-x-2">
                <span className="text-xs font-semibold px-2.5 py-0.5 bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 rounded-full">Senior Architect Portal</span>
                <span className="text-xs font-semibold px-2.5 py-0.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded-full">PostgreSQL Atomic Integrated</span>
              </div>
              <h1 className="text-xl font-bold tracking-tight text-white mt-0.5">eWallet Developer & Integrator Console</h1>
            </div>
          </div>
          
          <div className="flex items-center space-x-3 text-xs shrink-0 self-end md:self-auto">
            <div className="bg-slate-800 px-3 py-1.5 rounded-lg border border-slate-700 flex items-center space-x-2">
              <div className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></div>
              <span className="text-slate-300 font-mono">Simulated DB Connected (PostgreSQL Mock Pool)</span>
            </div>
            <a 
              href="#kotlin" 
              onClick={() => { setActiveTab('kotlin') }}
              className="bg-indigo-600 hover:bg-slate-800 text-slate-100 hover:text-indigo-400 px-3.5 py-1.5 rounded-lg border border-indigo-500/20 font-medium transition flex items-center gap-1.5"
            >
              <Code className="w-3.5 h-3.5" />
              <span>Get Code</span>
            </a>
          </div>
        </div>
      </header>

      {/* Main Workspace Frame */}
      <main className="max-w-7xl mx-auto px-6 mt-8">
        
        {/* Quick Abstract Guide */}
        <section className="bg-gradient-to-r from-slate-950 to-slate-900 border border-slate-800 rounded-2xl p-6 mb-8 shadow-xl relative overflow-hidden">
          <div className="absolute top-0 right-0 w-96 h-96 bg-indigo-500/5 rounded-full blur-3xl pointer-events-none"></div>
          <p className="text-xs font-bold text-indigo-400 uppercase tracking-wider mb-1.5">Arsitektur Integrasi Solusi</p>
          <h2 className="text-lg font-bold text-white mb-2">Sinkronisasi Penuh Antara Website & Aplikasi Android Native</h2>
          <p className="text-slate-300 text-sm leading-relaxed max-w-4xl">
            Selamat datang di Developer Blueprint Hub. Di sini, Anda memiliki akses penuh ke **schema database PostgreSQL**, file **Android Kotlin Native (Jetpack Compose)**, dan **Express.js Server API** mandiri. 
            Aplikasi Android hanya berkomunikasi melalui secure REST API. Transaksi keuangan (P2P Transfer, Invoice Web) dijamin mutlak aman di sisi backend menggunakan isolasi atomik **SELECT FOR UPDATE** demi mencegah masalah *double spending* tanpa harus terhubung langsung ke database dari sisi klien.
          </p>
          
          {/* Action Tabs Menu */}
          <div className="flex items-center gap-2 mt-6 overflow-x-auto pb-2 scrollbar-none border-t border-slate-800/60 pt-5">
            {[
              { id: 'emulator', label: '📱 Android Emulator UI', icon: Smartphone },
              { id: 'backend', label: '🔌 API Sandbox & Logs', icon: Terminal },
              { id: 'schema', label: '🗃️ PostgreSQL Schema', icon: Database },
              { id: 'kotlin', label: '📁 Kotlin Source Explorer', icon: Code },
              { id: 'security', label: '🔒 Security Checklist', icon: ShieldCheck },
              { id: 'guide', label: '🚀 APK Compilation', icon: Layers }
            ].map((tab) => {
              const Icon = tab.icon;
              const active = activeTab === tab.id;
              return (
                <button
                  key={tab.id}
                  id={`tab-${tab.id}`}
                  onClick={() => setActiveTab(tab.id as any)}
                  className={`flex items-center space-x-2 px-4 py-2.5 rounded-xl font-medium text-xs transition shrink-0 border whitespace-nowrap ${
                    active 
                      ? 'bg-slate-100 text-slate-900 border-white shadow-lg font-semibold' 
                      : 'bg-slate-955 text-slate-400 border-slate-800 hover:bg-slate-800 hover:text-white'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span>{tab.label}</span>
                </button>
              );
            })}
          </div>
        </section>

        {/* Workspace Body */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 items-start">
          
          {/* Tab Content Display (8 cols) */}
          <div className="lg:col-span-8 space-y-8">
            
            {/* 1. EMULATOR VIEW */}
            {activeTab === 'emulator' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl relative">
                <div className="flex items-center justify-between border-b border-slate-800 pb-4 mb-6">
                  <div>
                    <h3 className="font-bold text-white flex items-center gap-2">
                      <Smartphone className="w-5 h-5 text-indigo-400" />
                      Interactive Android Emulator
                    </h3>
                    <p className="text-xs text-slate-400 mt-0.5">Interaksikan langsung mockup aplikasi Android Kotlin Compose yang terhubung sistem.</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <button 
                      onClick={() => {
                        setPhoneScreen('splash');
                        setTransferSuccess(false);
                        setTopupSuccess(false);
                        setInvoicePaySuccess(false);
                      }}
                      className="text-xs bg-slate-800 hover:bg-slate-700 text-slate-300 px-3 py-1.5 rounded-lg border border-slate-700 transition flex items-center gap-1"
                    >
                      <RotateCw className="w-3 h-3" /> Reset Phone
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-12 gap-6 items-center">
                  
                  {/* Emulator Left Guide Panel */}
                  <div className="md:col-span-4 space-y-4">
                    <div className="bg-slate-900 p-4 rounded-xl border border-slate-800 space-y-3">
                      <h4 className="text-xs font-bold text-white flex items-center gap-1.5 uppercase tracking-wider text-indigo-400">
                        <Info className="w-3.5 h-3.5" /> Simulator Info
                      </h4>
                      <p className="text-xs text-slate-300 leading-relaxed">
                        Telepon virtual di sebelah kanan merender UI Jetpack Compose. State saldo eWallet otomatis terhubung dengan database lokal simulasi di tab API Sandbox.
                      </p>
                      
                      <div className="text-[11px] space-y-1.5 bg-slate-950 p-2.5 rounded-lg border border-slate-800 font-mono">
                        <p className="text-slate-400"><span className="text-indigo-400">User:</span> {state.userName}</p>
                        <p className="text-slate-400"><span className="text-indigo-400">Wallet ID:</span> {state.walletId}</p>
                        <p className="text-slate-400"><span className="text-indigo-400">Default PIN:</span> <span className="text-emerald-400">123456</span></p>
                        <p className="text-slate-400"><span className="text-indigo-400">Sync status:</span> LIVE OK</p>
                      </div>
                    </div>

                    <div className="bg-slate-900 p-4 rounded-xl border border-slate-800 space-y-2">
                      <h4 className="text-xs font-bold text-white uppercase tracking-wider text-amber-400">Integrasi Web Order</h4>
                      <p className="text-[11px] text-slate-300 leading-relaxed">
                        Klik tombol <strong>"Bayar Order"</strong> pada handphone untuk mensimulasikan order dari website utama Anda yang meminta konfirmasi pembayaran ke aplikasi eWallet ini.
                      </p>
                    </div>
                  </div>

                  {/* Render Handphone Container Chassis */}
                  <div className="md:col-span-8 flex justify-center">
                    <div className="relative w-80 h-[580px] bg-slate-900 rounded-[40px] border-[10px] border-slate-950 shadow-2xl overflow-hidden flex flex-col ring-4 ring-indigo-500/10">
                      
                      {/* Phone Speaker & Camera Notch */}
                      <div className="absolute top-0 left-1/2 transform -translate-x-1/2 w-32 h-6 bg-slate-950 rounded-b-xl z-30 flex items-center justify-center">
                        <div className="w-12 h-1 bg-slate-800 rounded-full mb-1"></div>
                        <div className="w-2.5 h-2.5 bg-indigo-900 rounded-full ml-2 mb-1 border border-slate-950"></div>
                      </div>

                      {/* Phone Info Header bar */}
                      <div className="bg-slate-950 px-5 pt-7 pb-2 flex justify-between items-center text-[10px] font-mono text-slate-400 select-none z-20">
                        <span>04:56 UTC</span>
                        <div className="flex items-center space-x-2">
                          <span>LTE</span>
                          <span className="text-emerald-400">98% ⚡</span>
                        </div>
                      </div>

                      {/* Screen Content Wrapper */}
                      <div className="flex-1 bg-slate-900 p-4 overflow-y-auto relative text-slate-100/90 text-sm">
                        
                        {/* SCREEN A: SPLASH SCREEN */}
                        {phoneScreen === 'splash' && (
                          <div className="flex flex-col items-center justify-between h-full py-8 text-center">
                            <div></div>
                            <div className="space-y-3">
                              <div className="mx-auto w-16 h-16 bg-gradient-to-tr from-indigo-500 to-emerald-500 rounded-2xl flex items-center justify-center shadow-lg">
                                <CreditCard className="w-8 h-8 text-white" />
                              </div>
                              <h4 className="text-xl font-extrabold tracking-tight text-white">M-Wallet Link</h4>
                              <p className="text-xs text-slate-400 max-w-[200px] mx-auto">Android native secure client eWallet system app.</p>
                            </div>
                            <button
                              onClick={() => setPhoneScreen('login')}
                              className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-semibold py-2.5 px-4 rounded-xl text-xs transition duration-200 cursor-pointer"
                            >
                              Mulai Aplikasi
                            </button>
                          </div>
                        )}

                        {/* SCREEN B: LOGIN */}
                        {phoneScreen === 'login' && (
                          <div className="flex flex-col h-full justify-between py-2">
                            <div className="space-y-4">
                              <div className="space-y-1">
                                <h4 className="text-lg font-bold text-white">Selamat Datang</h4>
                                <p className="text-xs text-slate-400">Masuk untuk mengakses akun eWallet Anda</p>
                              </div>

                              <div className="space-y-3">
                                <div className="space-y-1">
                                  <label className="text-[10px] text-slate-400 uppercase font-mono">Nomor Handphone</label>
                                  <input 
                                    type="text" 
                                    value={loginPhone}
                                    onChange={(e) => setLoginPhone(e.target.value)}
                                    placeholder="081xxx"
                                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 font-mono text-slate-100"
                                  />
                                </div>

                                <div className="space-y-1">
                                  <label className="text-[10px] text-slate-400 uppercase font-mono">Password</label>
                                  <input 
                                    type="password" 
                                    value={loginPassword}
                                    onChange={(e) => setLoginPassword(e.target.value)}
                                    placeholder="••••••••"
                                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-3 py-2 text-xs focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 text-slate-100"
                                  />
                                </div>

                                <span className="text-[11px] text-indigo-400 block hover:underline cursor-pointer">Lupa password?</span>
                              </div>
                            </div>

                            <div className="space-y-2 mt-4">
                              <button
                                onClick={triggerOtpVerif}
                                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-semibold py-2 rounded-xl text-xs flex justify-center items-center gap-1.5 cursor-pointer"
                              >
                                {isOtpLoading && <RotateCw className="w-3.5 h-3.5 animate-spin" />}
                                Masuk Rekening
                              </button>
                              
                              <div className="text-center">
                                <span className="text-xs text-slate-400 text-[10px]">Belum memiliki akun? </span>
                                <span className="text-xs text-indigo-400 hover:underline text-[10px] font-semibold cursor-pointer" onClick={() => triggerInAppNotification("Sistem Demo", "Gunakan Akun Demo yang sudah terdaftar untuk tes instan")}>Daftar</span>
                              </div>
                            </div>
                          </div>
                        )}

                        {/* SCREEN C: OTP VERIFICATION */}
                        {phoneScreen === 'otp' && (
                          <div className="flex flex-col h-full justify-between py-2">
                            <div className="space-y-4">
                              <div className="space-y-1">
                                <h4 className="text-lg font-bold text-white">Verifikasi OTP</h4>
                                <p className="text-xs text-slate-400">Kode OTP 6 digit telah dikirimkan ke email atau SMS Anda.</p>
                              </div>

                              <div className="space-y-2">
                                <label className="text-[11px] text-slate-400">Masukkan OTP Demo:</label>
                                <input 
                                  type="text" 
                                  value={otpInput}
                                  onChange={(e) => setOtpInput(e.target.value)}
                                  placeholder="Masukkan 2509"
                                  className="w-full text-center tracking-[0.5em] bg-slate-950 border border-slate-800 rounded-lg px-3 py-2.5 text-base font-bold focus:outline-none focus:border-indigo-500"
                                />
                                <span className="text-[10px] text-slate-400 text-center block mt-1">Gunakan kode demo: <strong className="text-emerald-400 font-mono">2509</strong></span>
                              </div>
                            </div>

                            <button
                              onClick={() => {
                                if (otpInput === '2509') {
                                  setPhoneScreen('home');
                                  addAuditLog('AUTH_OTP_SUCCESS', 'Multi-factor email OTP validation succeeded', 'Android-App-Emulator', true);
                                } else {
                                  triggerInAppNotification('OTP Salah', 'Kode OTP yang dimasukkan tidak sesuai. Masukkan 2509 untuk demo.');
                                }
                              }}
                              className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-semibold py-2 rounded-xl text-xs cursor-pointer"
                            >
                              Verifikasi & Selesai
                            </button>
                          </div>
                        )}

                        {/* SCREEN D: HOME DASHBOARD */}
                        {phoneScreen === 'home' && (
                          <div className="space-y-4 py-1">
                            {/* Dashboard Header */}
                            <div className="flex justify-between items-center">
                              <div>
                                <p className="text-[10px] text-slate-400">Halo, nasabah</p>
                                <h5 className="font-bold text-white text-sm">{state.userName}</h5>
                                <p className="text-[9px] text-slate-400 font-mono">No Wallet: {state.walletId}</p>
                              </div>
                              <div className="text-right">
                                <span className="text-[9px] px-1.5 py-0.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 rounded font-bold font-mono">VERIFIED</span>
                              </div>
                            </div>

                            {/* Balance Card Container */}
                            <div className="bg-gradient-to-br from-indigo-600 to-indigo-700 p-4 rounded-xl text-white space-y-3 shadow-md relative overflow-hidden">
                              <div className="absolute right-0 bottom-0 opacity-10">
                                <Smartphone className="w-24 h-24 transform translate-y-4 translate-x-4" />
                              </div>
                              <div>
                                <p className="text-[10px] text-indigo-100 uppercase tracking-wider">Saldo Aktif eWallet</p>
                                <h3 className="text-lg font-bold tracking-tight mt-0.5">{formatToRupiah(state.balance)}</h3>
                              </div>
                              <div className="border-t border-indigo-500 pt-2 flex justify-between text-[9px] text-indigo-100">
                                <span>Synergy: PostgreSQL Live DB</span>
                                <span className="font-semibold text-emerald-300">Atomic Safe</span>
                              </div>
                            </div>

                            {/* Grid Actions */}
                            <div className="grid grid-cols-3 gap-2">
                              <button 
                                onClick={() => {
                                  setTransferSuccess(false);
                                  setPhoneScreen('transfer');
                                }} 
                                className="flex flex-col items-center p-2 bg-slate-950 rounded-xl hover:bg-slate-800 transition border border-slate-800/80 cursor-pointer"
                              >
                                <ArrowRightLeft className="w-4 h-4 text-indigo-400 mb-1" />
                                <span className="text-[10px] font-semibold text-slate-300">Transfer</span>
                              </button>

                              <button 
                                onClick={() => {
                                  setTopupSuccess(false);
                                  setPhoneScreen('topup');
                                }} 
                                className="flex flex-col items-center p-2 bg-slate-950 rounded-xl hover:bg-slate-800 transition border border-slate-800/80 cursor-pointer"
                              >
                                <PiggyBank className="w-4 h-4 text-emerald-400 mb-1" />
                                <span className="text-[10px] font-semibold text-slate-300">Top Up</span>
                              </button>

                              <button 
                                onClick={() => {
                                  setInvoicePaySuccess(false);
                                  setPhoneScreen('pay_website');
                                }} 
                                className="flex flex-col items-center p-2 bg-slate-950 rounded-xl hover:bg-slate-800 transition border border-slate-800/80 cursor-pointer text-center"
                              >
                                <CreditCard className="w-4 h-4 text-amber-400 mb-1" />
                                <span className="text-[10px] font-semibold text-slate-300">Bayar Order</span>
                              </button>
                            </div>

                            {/* Transaction List Section */}
                            <div className="space-y-2">
                              <div className="flex justify-between items-center">
                                <span className="text-xs font-bold text-white">Riwayat Transaksi</span>
                                <span className="text-[10px] text-indigo-400 hover:underline cursor-pointer" onClick={() => triggerInAppNotification("Scroll Simulator", "Gunakan gesture scroll/scroll list di atas simulator")}>Lihat Semua</span>
                              </div>

                              <div className="space-y-2 max-h-[170px] overflow-y-auto pr-1">
                                {state.transactions.map((tx) => {
                                  const isDebit = tx.type === 'TRANSFER_OUT' || tx.type === 'PAYMENT';
                                  return (
                                    <div key={tx.id} className="bg-slate-950/80 p-2.5 rounded-lg border border-slate-850 flex items-center justify-between text-[11px]">
                                      <div className="flex items-center space-x-2">
                                        <div className={`p-1.5 rounded-lg ${isDebit ? 'bg-red-500/10 text-red-400' : 'bg-emerald-500/10 text-emerald-400'}`}>
                                          {isDebit ? <ArrowUpRight className="w-3.5 h-3.5" /> : <ArrowDownLeft className="w-3.5 h-3.5" /> }
                                        </div>
                                        <div>
                                          <p className="font-semibold text-slate-100 truncate max-w-[130px]">{tx.description}</p>
                                          <p className="text-[9px] text-slate-400 font-mono mt-0.5">{tx.date}</p>
                                        </div>
                                      </div>
                                      <div className="text-right">
                                        <p className={`font-mono font-bold ${isDebit ? 'text-red-400' : 'text-emerald-400'}`}>
                                          {isDebit ? '-' : '+'}{tx.amount.toLocaleString('id-ID')}
                                        </p>
                                        <span className="text-[8px] px-1 text-emerald-400 bg-emerald-500/10 rounded">SUCCESS</span>
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            </div>

                            {/* System Security Notice banner */}
                            <div className="p-2 bg-slate-950/40 rounded-lg border border-slate-800 flex items-start space-x-2">
                              <Shield className="w-3.5 h-3.5 text-indigo-400 mt-0.5 shrink-0" />
                              <div className="text-[9px] text-slate-400 leading-normal">
                                PIN keamanan Anda terdaftar secara terenkripsi. Kelola di <span className="text-indigo-400 underline font-semibold cursor-pointer" onClick={() => setPhoneScreen('pin_settings')}>Settings PIN</span>.
                              </div>
                            </div>
                          </div>
                        )}

                        {/* SCREEN E: TRANSFER PAGE */}
                        {phoneScreen === 'transfer' && (
                          <div className="space-y-4 py-1 flex flex-col h-full justify-between">
                            <div className="space-y-4">
                              <div className="flex items-center space-x-2 border-b border-slate-800 pb-2">
                                <button onClick={() => setPhoneScreen('home')} className="text-xs text-indigo-400 hover:underline">←</button>
                                <span className="text-xs font-bold text-white">Kirim Uang (P2P)</span>
                              </div>

                              {transferSuccess ? (
                                <div className="text-center py-6 space-y-4">
                                  <div className="w-12 h-12 bg-emerald-500/20 text-emerald-400 rounded-full flex items-center justify-center mx-auto">
                                    <CheckCircle2 className="w-8 h-8" />
                                  </div>
                                  <div className="space-y-1">
                                    <h5 className="font-bold text-white">Transfer Berhasil</h5>
                                    <p className="text-[11px] text-slate-400">Pasti & Atomic: Data tersinkronisasi ke server pusat.</p>
                                  </div>
                                  <div className="bg-slate-950 p-2 text-left rounded-lg text-xs font-mono text-slate-300">
                                    <p>Tujuan: {transferTarget}</p>
                                    <p>Jumlah: Rp {parseFloat(transferAmount).toLocaleString('id-ID')}</p>
                                  </div>
                                  <button onClick={() => {
                                    setPhoneScreen('home');
                                    setTransferSuccess(false);
                                    setTransferAmount('');
                                    setTransferTarget('');
                                    setValidatedRecipientName(null);
                                    setTransferPin('');
                                  }} className="bg-indigo-600 text-white px-4 py-1.5 rounded-lg text-xs w-full">Kembali</button>
                                </div>
                              ) : (
                                <div className="space-y-3">
                                  {/* Input nomor wallet */}
                                  <div className="space-y-1">
                                    <label className="text-[10px] text-slate-400 uppercase font-mono">Tujuan (No Wallet / HP / Email)</label>
                                    <div className="flex gap-1.5">
                                      <input 
                                        type="text" 
                                        value={transferTarget}
                                        onChange={(e) => setTransferTarget(e.target.value)}
                                        placeholder="cth: 889987654321098"
                                        className="flex-1 bg-slate-950 border border-slate-850 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:border-indigo-500 text-slate-100"
                                      />
                                      <button 
                                        onClick={handleValidateRecipient}
                                        className="bg-indigo-600 hover:bg-indigo-500 text-white px-2.5 py-1 rounded-lg text-[10px] font-semibold"
                                      >
                                        Cek
                                      </button>
                                    </div>
                                    
                                    {isValidatingRecipient && <p className="text-[9px] text-slate-400 animate-pulse">Memverifikasi identitas...</p>}
                                    {validatedRecipientName && (
                                      <p className="text-[10px] text-emerald-400 font-semibold bg-emerald-500/10 p-1 rounded">
                                        Penerima Sah: {validatedRecipientName}
                                      </p>
                                    )}
                                  </div>

                                  {/* Input nominal */}
                                  <div className="space-y-1">
                                    <label className="text-[10px] text-slate-400 uppercase font-mono">Nominal Transfer (Rp)</label>
                                    <input 
                                      type="number" 
                                      value={transferAmount}
                                      onChange={(e) => setTransferAmount(e.target.value)}
                                      placeholder="Jumlah Uang"
                                      className="w-full bg-slate-950 border border-slate-850 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:border-indigo-500 text-slate-100"
                                    />
                                  </div>

                                  {/* Input PIN Transaksi */}
                                  <div className="space-y-1">
                                    <label className="text-[10px] text-slate-400 uppercase font-mono">6 Digit PIN eWallet</label>
                                    <div className="relative">
                                      <input 
                                        type={pinVisible ? 'text' : 'password'} 
                                        maxLength={6}
                                        value={transferPin}
                                        onChange={(e) => setTransferPin(e.target.value)}
                                        placeholder="******"
                                        className="w-full bg-slate-950 border border-slate-850 rounded-lg px-2.5 py-1.5 text-xs text-center font-mono tracking-widest focus:outline-none focus:border-indigo-500 text-slate-100"
                                      />
                                      <button 
                                        onClick={() => setPinVisible(!pinVisible)}
                                        type="button"
                                        className="absolute right-2.5 top-2 text-slate-400 hover:text-white"
                                      >
                                        {pinVisible ? <EyeOff className="w-3.5 h-3.5" /> : <Eye className="w-3.5 h-3.5" />}
                                      </button>
                                    </div>
                                    <span className="text-[8px] text-slate-400">PIN default anda adalah <strong className="text-amber-400">123456</strong></span>
                                  </div>

                                  {transferError && <p className="text-[10px] text-red-400 font-semibold">{transferError}</p>}
                                </div>
                              )}
                            </div>

                            {!transferSuccess && (
                              <button 
                                onClick={handleEmulatorTransfer}
                                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2 rounded-xl text-xs cursor-pointer mt-4"
                              >
                                Konfirmasi & Kirim
                              </button>
                            )}
                          </div>
                        )}

                        {/* SCREEN F: TOP UP PAGE */}
                        {phoneScreen === 'topup' && (
                          <div className="space-y-4 py-1 flex flex-col h-full justify-between">
                            <div className="space-y-4">
                              <div className="flex items-center space-x-2 border-b border-slate-800 pb-2">
                                <button onClick={() => setPhoneScreen('home')} className="text-xs text-indigo-400 hover:underline">←</button>
                                <span className="text-xs font-bold text-white">Isi Saldo (Top Up)</span>
                              </div>

                              {topupSuccess ? (
                                <div className="text-center py-6 space-y-4 animate-fadeIn">
                                  <div className="w-12 h-12 bg-emerald-500/20 text-emerald-400 rounded-full flex items-center justify-center mx-auto">
                                    <CheckCircle2 className="w-8 h-8" />
                                  </div>
                                  <div>
                                    <h5 className="font-bold text-white">Top Up Sukses</h5>
                                    <p className="text-[11px] text-slate-400">Saldo ditambahkan secara real-time demi kenyamanan.</p>
                                  </div>
                                  <button onClick={() => {
                                    setPhoneScreen('home');
                                    setTopupSuccess(false);
                                  }} className="bg-indigo-600 text-white px-4 py-1.5 rounded-lg text-xs w-full">Lanjut</button>
                                </div>
                              ) : (
                                <div className="space-y-3">
                                  <p className="text-xs text-slate-300">Simulasikan pengisian saldo wallet terintegerasi payment gateway :</p>
                                  
                                  <div className="grid grid-cols-2 gap-2">
                                    {['50000', '100000', '250000', '500000'].map((val) => (
                                      <button 
                                        key={val} 
                                        type="button" 
                                        onClick={() => setTopupAmount(val)}
                                        className={`px-3 py-2 rounded-lg border text-xs font-bold ${topupAmount === val ? 'bg-indigo-600 text-white border-white' : 'bg-slate-950 text-slate-300 border-slate-800'}`}
                                      >
                                        Rp {parseInt(val).toLocaleString('id-ID')}
                                      </button>
                                    ))}
                                  </div>

                                  <div className="space-y-1">
                                    <label className="text-[10px] text-slate-400 font-mono">Pilih Metode Simulasi</label>
                                    <select className="w-full bg-slate-950 border border-slate-800 rounded-lg p-2 text-xs text-slate-200">
                                      <option>Virtual Account BNI/Mandiri/BRI</option>
                                      <option>QRIS Instant Payment Approval</option>
                                      <option>Credit Card (Sandbox PG)</option>
                                    </select>
                                  </div>
                                </div>
                              )}
                            </div>

                            {!topupSuccess && (
                              <button 
                                onClick={handleEmulatorTopup}
                                className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-2 rounded-xl text-xs cursor-pointer"
                              >
                                Lakukan Pembayaran VA
                              </button>
                            )}
                          </div>
                        )}

                        {/* SCREEN G: PAY WEB ORDER (INTER-SYSTEM INTEGRATION FLUSH) */}
                        {phoneScreen === 'pay_website' && (
                          <div className="space-y-4 py-1 flex flex-col h-full justify-between">
                            <div className="space-y-4">
                              <div className="flex items-center space-x-2 border-b border-slate-800 pb-2">
                                <button onClick={() => setPhoneScreen('home')} className="text-xs text-indigo-400 hover:underline">←</button>
                                <span className="text-xs font-bold text-white">Pembayaran Tagihan Website</span>
                              </div>

                              {invoicePaySuccess ? (
                                <div className="text-center py-6 space-y-4">
                                  <div className="w-12 h-12 bg-emerald-500/10 text-emerald-400 rounded-full flex items-center justify-center mx-auto">
                                    <CheckCircle2 className="w-8 h-8" />
                                  </div>
                                  <div className="space-y-1">
                                    <h5 className="font-bold text-emerald-400">Website Terbayar</h5>
                                    <p className="text-[10px] text-slate-400 leading-relaxed">Website utama anda secara otomatis mendeteksi status sukses order ini via webhook callback instant.</p>
                                  </div>
                                  <button onClick={() => {
                                    setPhoneScreen('home');
                                    setInvoicePaySuccess(false);
                                    setSelectedInvoice(null);
                                    setInvoicePayPin('');
                                  }} className="bg-indigo-600 text-white px-4 py-1.5 rounded-lg text-xs w-full">Kembali Ke Dashboard</button>
                                </div>
                              ) : (
                                <div className="space-y-4">
                                  <div className="space-y-2">
                                    <button
                                      onClick={handleCreateWebsiteCheckout}
                                      className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-2 rounded-xl text-xs cursor-pointer"
                                    >
                                      Simulasikan Checkout NADH_Wallet
                                    </button>
                                    {checkoutSyncStatus && (
                                      <p className="text-[10px] text-emerald-300 leading-relaxed">{checkoutSyncStatus}</p>
                                    )}
                                  </div>

                                  <p className="text-[11px] text-slate-300">Pilih salah satu order aktif di website Anda untuk ditarik tagihannya:</p>
                                  
                                  <div className="space-y-2">
                                    {state.paymentRequests.map((req) => (
                                      <div 
                                        key={req.id} 
                                        onClick={() => { if (req.status === 'PENDING') setSelectedInvoice(req); }}
                                        className={`p-2.5 rounded-lg border text-left cursor-pointer transition ${
                                          req.status !== 'PENDING' 
                                            ? 'opacity-50 border-slate-800 bg-slate-950 cursor-not-allowed' 
                                            : selectedInvoice?.id === req.id 
                                            ? 'border-indigo-500 bg-indigo-500/10' 
                                            : 'border-slate-800 bg-slate-950 hover:bg-slate-900'
                                        }`}
                                      >
                                        <div className="flex justify-between items-center">
                                          <span className="text-[11px] font-bold text-slate-200">{req.merchantName}</span>
                                          <span className={`text-[8px] font-extrabold px-1 rounded ${req.status === 'PENDING' ? 'bg-amber-500/10 text-amber-400' : 'bg-emerald-500/10 text-emerald-400'}`}>{req.status}</span>
                                        </div>
                                        <p className="text-[10px] text-slate-400 font-mono mt-0.5">Order ID: {req.orderId}</p>
                                        <p className="text-[11px] font-bold text-indigo-400 mt-1">{formatToRupiah(req.amount)}</p>
                                      </div>
                                    ))}
                                  </div>

                                  {selectedInvoice && (
                                    <div className="mt-4 p-3 bg-slate-950 rounded-xl border border-slate-850 space-y-3">
                                      <p className="text-[11px] font-semibold text-white">Masukkan PIN untuk Lunasi: {selectedInvoice.orderId}</p>
                                      <input 
                                        type="password" 
                                        maxLength={6}
                                        value={invoicePayPin}
                                        onChange={(e) => setInvoicePayPin(e.target.value)}
                                        placeholder="PIN (default 123456)"
                                        className="w-full bg-slate-900 border border-slate-800 rounded-lg p-2 text-center text-xs tracking-widest text-slate-100 placeholder:tracking-normal placeholder:font-sans placeholder:text-[10.5px]"
                                      />
                                      {invoicePayError && <p className="text-[10px] text-red-400 font-medium">{invoicePayError}</p>}
                                    </div>
                                  )}
                                </div>
                              )}
                            </div>

                            {!invoicePaySuccess && selectedInvoice && (
                              <button 
                                onClick={handleEmulatorPayInvoice}
                                className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2 rounded-xl text-xs cursor-pointer"
                              >
                                Bayar Tagihan
                              </button>
                            )}
                          </div>
                        )}

                        {/* SCREEN H: PIN SETTINGS */}
                        {phoneScreen === 'pin_settings' && (
                          <div className="space-y-4 py-1 flex flex-col h-full justify-between">
                            <div className="space-y-4">
                              <div className="flex items-center space-x-2 border-b border-slate-800 pb-2">
                                <button onClick={() => setPhoneScreen('home')} className="text-xs text-indigo-400 hover:underline">←</button>
                                <span className="text-xs font-bold text-white">Keamanan PIN</span>
                              </div>

                              <div className="space-y-3">
                                <div className="space-y-1">
                                  <label className="text-[10px] text-slate-400 uppercase font-mono">PIN Lama (Default: 123456)</label>
                                  <input 
                                    type="password" 
                                    maxLength={6}
                                    value={currentPinInput}
                                    onChange={(e) => setCurrentPinInput(e.target.value)}
                                    placeholder="******"
                                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1 text-xs text-center font-mono tracking-widest text-slate-100"
                                  />
                                </div>

                                <div className="space-y-1">
                                  <label className="text-[10px] text-slate-400 uppercase font-mono">PIN Baru (6 Angka)</label>
                                  <input 
                                    type="password" 
                                    maxLength={6}
                                    value={newPinInput}
                                    onChange={(e) => setNewPinInput(e.target.value)}
                                    placeholder="******"
                                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1 text-xs text-center font-mono tracking-widest text-slate-100"
                                  />
                                </div>

                                <div className="space-y-1">
                                  <label className="text-[10px] text-slate-400 uppercase font-mono">Konfirmasi PIN Baru</label>
                                  <input 
                                    type="password" 
                                    maxLength={6}
                                    value={confirmPinInput}
                                    onChange={(e) => setConfirmPinInput(e.target.value)}
                                    placeholder="******"
                                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-2.5 py-1 text-xs text-center font-mono tracking-widest text-slate-100"
                                  />
                                </div>

                                {pinSettingsError && <p className="text-[10px] text-red-400 font-semibold">{pinSettingsError}</p>}
                                {pinSettingsSuccess && <p className="text-[10px] text-emerald-400 font-semibold">{pinSettingsSuccess}</p>}
                              </div>
                            </div>

                            <button
                              onClick={handleUpdatePinEmulator}
                              className="w-full bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2 rounded-xl text-xs cursor-pointer mt-4"
                            >
                              Simpan PIN Baru
                            </button>
                          </div>
                        )}

                      </div>

                      {/* Phone Home Button bar */}
                      <div className="bg-slate-950 h-10 flex items-center justify-center select-none z-20">
                        <button 
                          onClick={() => { if (phoneScreen !== 'splash' && phoneScreen !== 'login') setPhoneScreen('home'); }}
                          className="w-16 h-1 bg-slate-800 rounded-full hover:bg-slate-600 transition"
                        ></button>
                      </div>

                    </div>
                  </div>

                </div>
              </div>
            )}

            {/* 2. API SANDBOX VIEW */}
            {activeTab === 'backend' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
                <div>
                  <h3 className="text-xl font-bold text-white flex items-center gap-2">
                    <Terminal className="w-5.5 h-5.5 text-indigo-400" />
                    API Sandbox & PostgreSQL Ledger Logs
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">Uji REST API server-side dan tonton simulasi penanganan deadlock, mutasi SQL locking, dan audit logs secara real-time.</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-12 gap-6">
                  
                  {/* Endpoint Select and Payload */}
                  <div className="md:col-span-5 space-y-4">
                    <div className="space-y-1.5">
                      <label className="text-xs font-bold text-slate-300">Pilih Endpoint API:</label>
                      <select 
                        value={sandboxEndpoint}
                        onChange={(e: any) => setSandboxEndpoint(e.target.value)}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg p-2.5 text-xs text-indigo-300 font-mono font-bold focus:ring-1 focus:ring-indigo-500 focus:outline-none"
                      >
                        <option value="register">POST /api/auth/register</option>
                        <option value="login">POST /api/auth/login</option>
                        <option value="transfer">POST /api/wallet/transfer</option>
                        <option value="pin-verify">POST /api/wallet/pin/verify</option>
                        <option value="payment-pay">POST /api/wallet/payment-request/:id/pay</option>
                        <option value="webhook">POST /api/payment-gateway/webhook</option>
                      </select>
                    </div>

                    <div className="space-y-1.5">
                      <div className="flex justify-between items-center">
                        <label className="text-xs font-bold text-slate-300">Request Body (JSON):</label>
                        <span className="text-[10px] text-slate-500 font-mono">Editable Payload</span>
                      </div>
                      <textarea
                        value={sandboxPayload}
                        onChange={(e) => setSandboxPayload(e.target.value)}
                        rows={10}
                        className="w-full bg-slate-900 border border-slate-800 rounded-lg p-3 text-xs font-mono text-slate-200 focus:ring-1 focus:ring-indigo-500 focus:outline-none"
                      />
                    </div>

                    <button
                      onClick={handleExecuteSandbox}
                      disabled={isSandboxExecuting}
                      className="w-full bg-indigo-600 hover:bg-indigo-500 disabled:bg-indigo-800 text-white font-bold py-2.5 px-4 rounded-xl text-xs flex items-center justify-center gap-2 cursor-pointer transition"
                    >
                      {isSandboxExecuting ? (
                        <>
                          <RotateCw className="w-3.5 h-3.5 animate-spin" />
                          <span>Executing Query...</span>
                        </>
                      ) : (
                        <>
                          <Play className="w-3.5 h-3.5 fill-white" />
                          <span>Execute API Route</span>
                        </>
                      )}
                    </button>
                  </div>

                  {/* Sandbox Logs & JSON Output */}
                  <div className="md:col-span-7 space-y-4">
                    
                    {/* Database Log Operations */}
                    <div className="bg-slate-900 rounded-xl border border-slate-800 p-4 space-y-3">
                      <div className="flex justify-between items-center border-b border-slate-800 pb-2">
                        <h4 className="text-[11px] font-bold text-slate-300 uppercase tracking-wider flex items-center gap-1.5 font-mono">
                          <Database className="w-3.5 h-3.5 text-indigo-400" /> PostgreSQL Lock Sequence
                        </h4>
                        <span className="text-[10px] text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded font-mono font-bold">MUTATION</span>
                      </div>
                      
                      <div className="font-mono text-[10.5px] text-slate-300 space-y-1 bg-slate-950 p-2.5 rounded-lg border border-slate-850 max-h-[140px] overflow-y-auto">
                        {sandboxSqlLogs.length > 0 ? (
                          sandboxSqlLogs.map((logStr, i) => (
                            <p 
                              key={i} 
                              className={
                                logStr.startsWith('--') 
                                  ? 'text-slate-500 italic' 
                                  : logStr.includes('FOR UPDATE') 
                                  ? 'text-amber-400 font-bold' 
                                  : logStr.includes('COMMIT') 
                                  ? 'text-emerald-400' 
                                  : 'text-indigo-200'
                              }
                            >
                              {logStr}
                            </p>
                          ))
                        ) : (
                          <p className="text-slate-500 italic">Pemicu interaksi SQL akan tampil di sini saat route API dieksekusi.</p>
                        )}
                      </div>
                    </div>

                    {/* JSON API output */}
                    <div className="bg-slate-900 rounded-xl border border-slate-800 p-4 space-y-2">
                      <div className="flex justify-between items-center">
                        <h4 className="text-[11px] font-bold text-slate-300 uppercase tracking-wider font-mono">
                          HTTP Response JSON
                        </h4>
                        <button 
                          onClick={() => { if(sandboxResponse) handleCopy(JSON.stringify(sandboxResponse, null, 2), 'json_res'); }}
                          className="text-[10px] text-indigo-400 hover:text-white flex items-center gap-1 font-semibold"
                        >
                          <Copy className="w-3 h-3" /> {copiedText === 'json_res' ? 'Copied!' : 'Copy'}
                        </button>
                      </div>

                      <pre className="bg-slate-950 p-3 rounded-lg border border-slate-850 text-[10.5px] font-mono text-emerald-400 overflow-x-auto max-h-[190px]">
                        {sandboxResponse ? JSON.stringify(sandboxResponse, null, 2) : '// Response output dari request REST API'}
                      </pre>
                    </div>

                  </div>

                </div>

                {/* Audit Logs Screen Console */}
                <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 space-y-3">
                  <div className="flex justify-between items-center">
                    <h4 className="text-xs font-bold text-white uppercase tracking-wider font-mono flex items-center gap-1.5">
                      <Terminal className="w-3.5 h-3.5 text-emerald-400" /> LIVE COMPLIANCE AUDIT_LOGS TABLE
                    </h4>
                    <span className="text-[9px] bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 px-1.5 py-0.5 rounded font-mono">IMMUTABLE ENGINE</span>
                  </div>

                  <div className="max-h-[160px] overflow-y-auto space-y-2 pr-1">
                    {state.auditLogs.map((log) => (
                      <div key={log.id} className="bg-slate-950 p-2.5 rounded-lg border border-slate-850 flex flex-col md:flex-row md:items-center justify-between gap-2 text-xs font-mono">
                        <div className="flex items-start md:items-center space-x-2.5">
                          <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded ${log.status === 'SUCCESS' ? 'bg-emerald-500/10 text-emerald-400' : 'bg-red-500/10 text-red-400'}`}>{log.action}</span>
                          <p className="text-slate-200 mt-0.5 md:mt-0">{log.details}</p>
                        </div>
                        <div className="text-right text-[10px] text-slate-500">
                          <span>{log.timestamp.replace('T', ' ').substring(0, 19)}</span> | <span>{log.ipAddress}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

              </div>
            )}

            {/* 3. POSTGRES SCHEMAS */}
            {activeTab === 'schema' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                  <div>
                    <h3 className="text-xl font-bold text-white flex items-center gap-2">
                      <Database className="w-5.5 h-5.5 text-emerald-400" />
                      PostgreSQL Relational Schema Studio
                    </h3>
                    <p className="text-xs text-slate-400 mt-1">Struktur tabel relasional database dengan tipe UUID, constraint immutable audit logs, dan indexes.</p>
                  </div>
                  <div>
                    <button 
                      onClick={() => handleCopy(pgsqlSchema, 'sql_schema')}
                      className="bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-2 px-3.5 rounded-xl text-xs flex items-center gap-2 cursor-pointer transition"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      <span>{copiedText === 'sql_schema' ? 'Copied Full Script!' : 'Copy SQL Script'}</span>
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                  
                  {/* Select Table Detail */}
                  <div className="lg:col-span-4 space-y-2">
                    <span className="text-xs font-bold text-slate-300 uppercase tracking-widest block mb-1">Daftar Tabel (Postgres):</span>
                    {[
                      { id: 'users', desc: 'Detail data kredensial otentikasi & OTP.' },
                      { id: 'wallets', desc: 'Saldo terverifikasi klien, terikat mutlak user_id.' },
                      { id: 'wallet_pins', desc: 'Penyimpanan hash PIN eWallet yang aman.' },
                      { id: 'wallet_transactions', desc: 'Buku besar utama seluruh transaksi immutable.' },
                      { id: 'wallet_payment_requests', desc: 'Invoice pembayaran orders dari website luar.' },
                      { id: 'audit_logs', desc: 'Pelacak compliance, log security IP, dan deteksi abuse.' }
                    ].map((tbl) => (
                      <button
                        key={tbl.id}
                        onClick={() => setSelectedSchemaTable(tbl.id)}
                        className={`w-full text-left p-3 rounded-xl transition border text-xs leading-snug flex flex-col gap-1 ${
                          selectedSchemaTable === tbl.id 
                            ? 'bg-slate-100 text-slate-900 border-white' 
                            : 'bg-slate-900 text-slate-300 border-slate-800/80 hover:bg-slate-850'
                        }`}
                      >
                        <span className="font-bold font-mono text-sm">{tbl.id}</span>
                        <span className={`text-[11px] ${selectedSchemaTable === tbl.id ? 'text-slate-700' : 'text-slate-400'}`}>{tbl.desc}</span>
                      </button>
                    ))}
                  </div>

                  {/* Schema SQL Representation Column */}
                  <div className="lg:col-span-8 space-y-3">
                    <div className="bg-slate-900 p-4 rounded-xl border border-slate-800">
                      <h4 className="text-sm font-bold text-slate-200 uppercase tracking-wider flex items-center gap-2 font-mono">
                        <Terminal className="w-4 h-4 text-emerald-400" />
                        SQL DDL : {selectedSchemaTable}
                      </h4>
                      <div className="mt-3 bg-slate-950 p-3 rounded-lg border border-slate-850 text-xs font-mono text-emerald-400 overflow-x-auto max-h-[350px]">
                        {selectedSchemaTable === 'users' && (
                          <pre>{`CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    phone_number VARCHAR(15) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    is_verified BOOLEAN DEFAULT FALSE,
    otp_code VARCHAR(6),
    otp_expiry TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);`}</pre>
                        )}
                        {selectedSchemaTable === 'wallets' && (
                          <pre>{`CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_number VARCHAR(16) UNIQUE NOT NULL,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) DEFAULT 'IDR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraint: Mencegah Saldo Minus (Guard Utama DB)
    CONSTRAINT chk_positive_balance CHECK (balance >= 0.00)
);

CREATE INDEX idx_wallets_number ON wallets(wallet_number);`}</pre>
                        )}
                        {selectedSchemaTable === 'wallet_pins' && (
                          <pre>{`CREATE TABLE wallet_pins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID UNIQUE NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    pin_hash VARCHAR(255) NOT NULL, -- PIN ter-hash (Argon2 / Bcrypt)
    attempts_count INT DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);`}</pre>
                        )}
                        {selectedSchemaTable === 'wallet_transactions' && (
                          <pre>{`CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_id VARCHAR(50) UNIQUE NOT NULL, -- Idempotency key reference
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    type transaction_type NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_amount_positive CHECK (amount > 0.00)
);

CREATE INDEX idx_transactions_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX idx_transactions_created ON wallet_transactions(created_at DESC);`}</pre>
                        )}
                        {selectedSchemaTable === 'wallet_payment_requests' && (
                          <pre>{`CREATE TABLE wallet_payment_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID REFERENCES wallet_transactions(id) ON DELETE SET NULL,
    merchant_order_id VARCHAR(100) NOT NULL, -- ID order dari website e-commerce utama
    amount DECIMAL(15, 2) NOT NULL,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    callback_url VARCHAR(255) NOT NULL, -- Endpoint webhook di website Anda
    customer_wallet_id UUID REFERENCES wallets(id) ON DELETE RESTRICT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_payment_requests_order ON wallet_payment_requests(merchant_order_id);`}</pre>
                        )}
                        {selectedSchemaTable === 'audit_logs' && (
                          <pre>{`CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL, -- cth: "AUTH_LOGIN", "TRANSFER_PIN_SUCCESS"
    details TEXT,
    ip_address VARCHAR(45) NOT NULL,
    status VARCHAR(10) NOT NULL, -- SUCCESS, FAILED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);`}</pre>
                        )}
                      </div>
                    </div>

                    <div className="bg-slate-900/60 p-4 rounded-xl border border-slate-800 space-y-2 text-xs">
                      <h4 className="font-bold text-white uppercase tracking-wider flex items-center gap-1">
                        <Shield className="w-3.5 h-3.5 text-amber-500" /> Penjelasan Atomic Row-Locking (SELECT FOR UPDATE)
                      </h4>
                      <p className="text-slate-300 leading-relaxed">
                        Jika dua transaksi pengisian saldo atau penarikan berjalan sekaligus, membaca saldo menggunakan kueri dasar rentan terhadap <span className="text-amber-400 font-semibold font-mono">Race Conditions (Double Spend)</span>. 
                        Wajib gunakan klausa <strong className="font-mono text-amber-400">FOR UPDATE</strong> pada kueri SELECT baris dompet/wallet milik pengirim dan penerima di dalam blok transaksi PostgreSQL (BEGIN ... COMMIT) agar transaksi dijalankan berurutan.
                      </p>
                    </div>

                  </div>

                </div>

              </div>
            )}

            {/* 4. KOTLIN SOURCE CODE */}
            {activeTab === 'kotlin' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                  <div>
                    <h3 className="text-xl font-bold text-white flex items-center gap-2">
                      <Code className="w-5.5 h-5.5 text-indigo-400" />
                      Kotlin Project Source Code Explorer
                    </h3>
                    <p className="text-xs text-slate-400 mt-1">Struktur folder proyek Android Studio Native menggunakan Jetpack Compose, Retrofit, dan Encrypted Preferences.</p>
                  </div>
                  <div>
                    <button 
                      onClick={() => handleCopy(selectedKotlinFile.content, selectedKotlinFile.name)}
                      className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2 px-3.5 rounded-xl text-xs flex items-center gap-2 cursor-pointer transition"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      <span>{copiedText === selectedKotlinFile.name ? 'Copied File!' : `Copy ${selectedKotlinFile.name}`}</span>
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
                  
                  {/* File Tree Column */}
                  <div className="lg:col-span-4 space-y-2 font-mono text-xs">
                    <span className="text-xs font-sans font-bold text-slate-400 uppercase tracking-wider block mb-1">Struktur Berkas Proyek:</span>
                    
                    <div className="bg-slate-900/90 p-4 rounded-xl border border-slate-800 space-y-3">
                      <div className="space-y-1">
                        <p className="text-slate-400 font-semibold text-[11px]">📁 App Module Config</p>
                        <button 
                          onClick={() => setSelectedKotlinFile(kotlinFiles[0])}
                          className={`w-full text-left pl-3 py-1 rounded transition text-[11px] ${selectedKotlinFile.name.includes('gradle') ? 'bg-indigo-500/10 text-indigo-300 font-bold' : 'text-slate-300 hover:text-white'}`}
                        >
                          📄 build.gradle.kts (App)
                        </button>
                      </div>

                      <div className="space-y-1.5">
                        <p className="text-slate-400 font-semibold text-[11px]">📁 com.ewallet.app.security</p>
                        <button 
                          onClick={() => setSelectedKotlinFile(kotlinFiles[1])}
                          className={`w-full text-left pl-3 py-1 rounded transition text-[11px] ${selectedKotlinFile.name === 'SecurityManager.kt' ? 'bg-indigo-500/10 text-indigo-300 font-bold' : 'text-slate-300 hover:text-white'}`}
                        >
                          📄 SecurityManager.kt
                        </button>
                      </div>

                      <div className="space-y-1.5">
                        <p className="text-slate-400 font-semibold text-[11px]">📁 com.ewallet.app.network</p>
                        <button 
                          onClick={() => setSelectedKotlinFile(kotlinFiles[2])}
                          className={`w-full text-left pl-3 py-1 rounded transition text-[11px] ${selectedKotlinFile.name === 'NetworkModule.kt' ? 'bg-indigo-500/10 text-indigo-300 font-bold' : 'text-slate-300 hover:text-white'}`}
                        >
                          📄 NetworkModule.kt
                        </button>
                        <button 
                          onClick={() => setSelectedKotlinFile(kotlinFiles[3])}
                          className={`w-full text-left pl-3 py-1 rounded transition text-[11px] ${selectedKotlinFile.name === 'TokenAuthenticator.kt' ? 'bg-indigo-500/10 text-indigo-300 font-bold' : 'text-slate-300 hover:text-white'}`}
                        >
                          📄 TokenAuthenticator.kt
                        </button>
                      </div>

                      <div className="space-y-1.5">
                        <p className="text-slate-400 font-semibold text-[11px]">📁 com.ewallet.app.ui.dashboard</p>
                        <button 
                          onClick={() => setSelectedKotlinFile(kotlinFiles[4])}
                          className={`w-full text-left pl-3 py-1 rounded transition text-[11px] ${selectedKotlinFile.name === 'DashboardScreen.kt' ? 'bg-indigo-500/10 text-indigo-300 font-bold' : 'text-slate-300 hover:text-white'}`}
                        >
                          📄 DashboardScreen.kt
                        </button>
                      </div>
                    </div>
                  </div>

                  {/* File Code Viewer Column */}
                  <div className="lg:col-span-8 space-y-3">
                    <div className="bg-slate-900 rounded-xl border border-slate-800 overflow-hidden">
                      <div className="bg-slate-950 px-4 py-2 border-b border-slate-800 flex justify-between items-center text-xs text-slate-400 font-mono">
                        <span>{selectedKotlinFile.path}</span>
                        <span className="text-indigo-400">{selectedKotlinFile.language}</span>
                      </div>
                      <pre className="p-4 bg-slate-950 text-slate-100 text-xs font-mono overflow-auto max-h-[460px] leading-relaxed">
                        {selectedKotlinFile.content}
                      </pre>
                    </div>
                  </div>

                </div>

              </div>
            )}

            {/* 5. BACKEND SOURCE CODE */}
            {activeTab === 'backend' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
                <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 border-b border-slate-800 pb-4">
                  <div>
                    <h3 className="text-xl font-bold text-white flex items-center gap-2">
                      <Code className="w-5.5 h-5.5 text-indigo-400" />
                      Express Server API Source Code
                    </h3>
                    <p className="text-xs text-slate-400 mt-1">Contoh implementasi endpoint transaksi Atomic dari backend Node.js / Express utama.</p>
                  </div>
                  <div>
                    <button 
                      onClick={() => handleCopy(expressCode, 'express_code')}
                      className="bg-indigo-600 hover:bg-indigo-500 text-white font-bold py-2 px-3.5 rounded-xl text-xs flex items-center gap-2 cursor-pointer transition"
                    >
                      <Copy className="w-3.5 h-3.5" />
                      <span>{copiedText === 'express_code' ? 'Copied Server Code!' : 'Copy Server Code'}</span>
                    </button>
                  </div>
                </div>

                <div className="bg-slate-900 rounded-xl border border-slate-800 overflow-hidden">
                  <div className="bg-slate-900 px-4 py-3 border-b border-slate-800 flex justify-between items-center text-xs text-slate-400 font-mono">
                    <span>server.ts (Node.js + Express + PG Client)</span>
                    <span className="text-indigo-400">typescript</span>
                  </div>
                  <pre className="p-4 bg-slate-950 text-slate-300 text-xs font-mono overflow-auto max-h-[440px] leading-relaxed">
                    {expressCode}
                  </pre>
                </div>
              </div>
            )}

            {/* 6. SECURITY AUDIT CHECKLIST */}
            {activeTab === 'security' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
                <div>
                  <h3 className="text-xl font-bold text-white flex items-center gap-2">
                    <ShieldCheck className="w-5.5 h-5.5 text-indigo-400" />
                    Security Architecture Audit & Production Checklist
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">Cegah kebocoran token, manipulasi dana, dan Man-in-the-Middle attacks sebelum rilis ke production.</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  {[
                    {
                      title: "Encrypted Storage (Device Level)",
                      desc: "Jangan simpan Access Token atau Refresh Token di SharedPreferences biasa (plaintext xml). Gunakan EncryptedSharedPreferences dari Android Jetpack Security SDK yang didasari enkripsi hardware (AES-256-SIV dan AES-256-GCM).",
                      status: "HIGH CRITICAL"
                    },
                    {
                      title: "SSL Pinning (Mitre MITM Prevention)",
                      desc: "Konfigurasikan OkHttpClient menggunakan CertificatePinner yang berisi hash SHA-256 dari sertifikat SSL production Anda. Ini memblokir router jahat yang menggunakan sertifikat SSL palsu dalam meretas traffic REST API.",
                      status: "HIGH CRITICAL"
                    },
                    {
                      title: "Bcrypt & Argon2 PIN Hashing (Cloud level)",
                      desc: "Jangan simpan PIN wallet (6 digit) dalam bentuk digit teks biasa (plaintext) di database PostgreSQL. Jalankan hash menggunakan Bcrypt / Argon2 sebelum disimpan di tabel wallet_pins.",
                      status: "HIGH CRITICAL"
                    },
                    {
                      title: "Double Spending (Pessimistic SELECT FOR UPDATE)",
                      desc: "Sertakan operator 'FOR UPDATE' di backend ketika membaca baris saldo user agar baris terlock secara penuh (Pessimistic Row Locking). Hal ini menjamin integritas saldo bahkan ketika pengguna menekan tombol transaksi berkali-kali secara simultan.",
                      status: "MEDIUM IMPORTANT"
                    },
                    {
                      title: "Idempotency-Key (Double Submit Guard)",
                      desc: "Setiap transaksi transfer dari Kotlin wajib menyertakan UUID yang dihasilkan client sebagai reference_id unik. Jika request terkirim dua kali karena kendala lag koneksi, backend secara aman menolak request kedua.",
                      status: "MEDIUM IMPORTANT"
                    },
                    {
                      title: "Rate Limiter & Audit Database Logs",
                      desc: "Terapkan perlindungan rate-limiting (maksimal 5 attempt salah per 5 menit) di backend pada endpoint verifikasi PIN dan login untuk menghentikan serangan brute-force, serta catat IP mencurigakan ke tabel audit_logs.",
                      status: "LOW RECOMMENDED"
                    }
                  ].map((chk, idx) => (
                    <div key={idx} className="bg-slate-900 p-4 rounded-xl border border-slate-800 flex flex-col justify-between gap-3">
                      <div className="space-y-1.5">
                        <div className="flex items-center justify-between">
                          <span className="text-xs font-bold text-white font-mono">{chk.title}</span>
                          <span className={`text-[9px] font-extrabold px-1.5 py-0.5 rounded ${
                            chk.status === 'HIGH CRITICAL' ? 'bg-red-500/10 text-red-400' : 'bg-amber-500/10 text-amber-400'
                          }`}>{chk.status}</span>
                        </div>
                        <p className="text-xs text-slate-300 leading-relaxed text-[11.5px]">{chk.desc}</p>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* 7. APK COMPILATION GUIDE */}
            {activeTab === 'guide' && (
              <div className="bg-slate-950 border border-slate-800 rounded-2xl p-6 shadow-2xl space-y-6">
                <div>
                  <h3 className="text-xl font-bold text-white flex items-center gap-2">
                    <Layers className="w-5.5 h-5.5 text-indigo-400" />
                    Instruksi Build Proyek & Kompilasi File .APK
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">Langkah demi langkah mendeploy proyek Android Studio, menyetel SDK, hingga menghasilkan installer file APK.</p>
                </div>

                <div className="space-y-4">
                  {[
                    {
                      step: "Langkah 1: Inisialisasi Proyek Baru di Android Studio",
                      desc: "Buka Android Studio Flamingo/Hedgehog atau versi terbaru. Pilih template **'Empty Compose Activity'**. Masukkan Package Name sesuai domain Anda (contoh: com.ewallet.app) dan pastikan memilih bahasa pengkodean Kotlin."
                    },
                    {
                      step: "Langkah 2: Menambahkan Dependensi build.gradle.kts",
                      desc: "Salin dependensi Jetpack Security Crypto, Retrofit, OkHttp Logging, & Gson Converter yang telah kami rakit di tab 'Kotlin Source Explorer' ke berkas app/build.gradle.kts internal proyek baru Anda. Lakukan 'Sync Project with Gradle Files' kemudian tunggu hingga sukses."
                    },
                    {
                      step: "Langkah 3: Pembuatan Berkas Kredensial dan Logika Kelas",
                      desc: "Buat package baru sesuai susunan yang terlampir (security, network, ui). Buat file SecurityManager.kt, NetworkModule.kt, TokenAuthenticator.kt, dan tempelkan isinya persis seperti kode contoh demi menunjang keamanan di atas."
                    },
                    {
                      step: "Langkah 4: Konfigurasi AndroidManifest.xml (Internet Access)",
                      desc: "Tambahkan izin akses internet di manifest utama: \n\n <uses-permission android:name=\"android.permission.INTERNET\" />\n\nUntuk server localhost pengembangan debug, tambahkan flag android:usesCleartextTraffic=\"true\" di dalam tag <application>."
                    },
                    {
                      step: "Langkah 5: Kompilasi Release APK / Debug APK",
                      desc: "Untuk keperluan uji coba lokal Emulator: Pasang perangkat lalu jalankan run langsung (Shift + F10) atau klik menu Build -> Build Bundle(s) / APK(s) -> **'Build APK(s)'**. File .APK siap pasang akan keluar di folder: app/build/outputs/apk/debug/"
                    },
                    {
                      step: "Langkah 6: Generate Signed Production APK",
                      desc: "Bila siap rilis, klik Build -> **Generate Signed Bundle / APK**. Pilih APK, buat file Keystore (.jks) baru berupa master key enkripsi Anda beserta isi password pengaman. Tentukan rilis Build Type: 'release' dengan minifyEnabled = true untuk proguard obfuscation."
                    }
                  ].map((stepItem, idx) => (
                    <div key={idx} className="bg-slate-900 p-4 rounded-xl border border-slate-850 flex items-start gap-4">
                      <div className="w-6 h-6 shrink-0 bg-indigo-500/10 text-indigo-400 border border-indigo-500/20 rounded-lg flex items-center justify-center font-bold text-xs">
                        {idx + 1}
                      </div>
                      <div className="space-y-1">
                        <h4 className="text-xs font-bold text-white font-mono">{stepItem.step}</h4>
                        <p className="text-slate-300 text-[11.5px] leading-relaxed whitespace-pre-line">{stepItem.desc}</p>
                      </div>
                    </div>
                  ))}
                </div>

              </div>
            )}

          </div>

          {/* Right Architecture Guidelines Sidebar (4 cols) */}
          <div className="lg:col-span-4 space-y-6">
            
            {/* System Topology Diagram */}
            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-4">
              <h3 className="font-bold text-white text-sm flex items-center gap-1.5 uppercase tracking-wider text-indigo-400">
                <Layers className="w-4 h-4" /> System Integration Flow
              </h3>
              
              <div className="space-y-3.5 text-xs">
                
                {/* Step Flow 1 */}
                <div className="bg-slate-900 p-3 rounded-xl border border-slate-850 space-y-1">
                  <div className="flex items-center space-x-2">
                    <span className="w-4 h-4 rounded px-1.5 bg-emerald-500/15 text-emerald-400 font-bold font-mono text-[9px] flex items-center justify-center">1</span>
                    <strong className="text-[11px] text-white">Website Orders Dispatch</strong>
                  </div>
                  <p className="text-slate-400 text-[10px] leading-relaxed">
                    Website utama memesan tagihan dengan request <code className="text-amber-400 font-mono">POST /api/wallet/payment-request</code>. Status order saat awal adalah <strong className="text-amber-400 font-mono">PENDING</strong>.
                  </p>
                </div>

                {/* Step Flow 2 */}
                <div className="bg-slate-900 p-3 rounded-xl border border-slate-850 space-y-1">
                  <div className="flex items-center space-x-2">
                    <span className="w-4 h-4 rounded px-1.5 bg-emerald-500/15 text-emerald-400 font-bold font-mono text-[9px] flex items-center justify-center">2</span>
                    <strong className="text-[11px] text-white">App Confirm & Draw balance</strong>
                  </div>
                  <p className="text-slate-400 text-[10px] leading-relaxed">
                    User membuka aplikasi Android eWallet, memasukkan PIN 6 digit. Server Express mengunci baris dompet di Postgres <code className="text-amber-400 font-mono">SELECT FOR UPDATE</code> dan mengurangi saldo.
                  </p>
                </div>

                {/* Step Flow 3 */}
                <div className="bg-slate-900 p-3 rounded-xl border border-slate-850 space-y-1">
                  <div className="flex items-center space-x-2">
                    <span className="w-4 h-4 rounded px-1.5 bg-emerald-500/15 text-emerald-400 font-bold font-mono text-[9px] flex items-center justify-center">3</span>
                    <strong className="text-[11px] text-white">Dispatch IPN Webhook Callback</strong>
                  </div>
                  <p className="text-slate-400 text-[10px] leading-relaxed">
                    Secara live, backend API menembakkan webhook terverifikasi signature kriptografis ke <code className="text-indigo-400 font-mono">callback_url</code> website utama untuk otomatis set status order ke <strong className="text-emerald-400 font-mono">PAID/SUCCESS</strong>.
                  </p>
                </div>

              </div>
            </div>

            {/* Production Tuning & Adjustment points */}
            <div className="bg-slate-950 border border-slate-805 rounded-2xl p-5 shadow-xl space-y-4">
              <h3 className="font-bold text-white text-sm flex items-center gap-1.5 uppercase tracking-wider text-amber-400">
                <Info className="w-4 h-4" /> Production Adjustments
              </h3>

              <div className="space-y-4 text-xs text-slate-300">
                <p className="leading-normal">
                  Bagian-bagian kode berikut wajib disesuaikan sebelum dideploy ke infrastruktur nyata server-production:
                </p>

                <div className="space-y-3">
                  <div className="space-y-1">
                    <strong className="font-mono text-[11px] text-indigo-400 block">1. JWT & Webhook Secret Keys</strong>
                    <p className="text-[10px] text-slate-400">Ubah variabel rahasia <code className="text-slate-300 font-mono">JWT_SECRET_KEY</code> dan <code className="text-slate-300 font-mono">WEBHOOK_SECRET_KEY</code> di konfigurasi environment backend Anda.</p>
                  </div>

                  <div className="space-y-1">
                    <strong className="font-mono text-[11px] text-indigo-400 block">2. Android Build Base-URL</strong>
                    <p className="text-[10px] text-slate-400">Edit string BuildConfig <code className="text-slate-300 font-mono">BASE_URL</code> di dalam <code className="text-slate-300 font-mono">app/build.gradle.kts</code> agar mengarah ke secure domain HTTPS website utama Anda (https://api.domainanda.com/).</p>
                  </div>

                  <div className="space-y-1">
                    <strong className="font-mono text-[11px] text-indigo-400 block">3. SSL Pinning Key hashes</strong>
                    <p className="text-[10px] text-slate-400">Ganti string placeholder fingerprint sertifikat sha256 di <code className="text-slate-300 font-mono">NetworkModule.kt</code> dengan SHA-256 fingerprint sertifikat domain web asli Anda untuk perlindungan MITM maksimal.</p>
                  </div>

                  <div className="space-y-1">
                    <strong className="font-mono text-[11px] text-indigo-400 block">4. PG Client Pooling Credentials</strong>
                    <p className="text-[10px] text-slate-400">Sesuaikan properti Postgres pool di backend server agar menunjuk ke kredensial Host, User, Port, dan Password server PostgreSQL asli Anda.</p>
                  </div>
                </div>
              </div>
            </div>

            {/* Quick configuration instructions for the .env.example file */}
            <div className="bg-slate-950 border border-slate-800 rounded-2xl p-5 shadow-xl space-y-3.5">
              <h3 className="font-bold text-white text-sm uppercase tracking-wider text-emerald-400">
                📋 Environment Variables
              </h3>
              <p className="text-xs text-slate-300 leading-normal">Berikut merupakan template isi berkas env yang dipasang di server backend utama:</p>
              
              <div className="bg-slate-900 p-2.5 rounded-lg border border-slate-850 text-[10px] font-mono text-indigo-300 space-y-1">
                <p>DATABASE_URL="postgresql://user:pass@host:5432/walletdb"</p>
                <p>JWT_SECRET_KEY="A_VERY_SECURE_LONG_SECRET"</p>
                <p>WEBHOOK_SECRET_KEY="WEBSITE_CALLBACK_HMAC_SECRET"</p>
                <p>RATE_LIMIT_MAX_ATTEMPTS=100</p>
                <p>PORT=3000</p>
              </div>
            </div>

          </div>

        </div>

      </main>

    </div>
  );
}

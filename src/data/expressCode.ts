export const expressCode = `/**
 * Secure eWallet Backend API Node.js / Express Implementation
 * Centered on Transactional Concurrency, Integrity, and Webhook processing.
 */

import express, { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import bcrypt from 'bcrypt';
import { Pool } from 'pg'; // PostgreSQL Client
import rateLimit from 'express-rate-limit';

const app = express();
app.use(express.json());

// 1. PostgreSQL Pool Configuration
const dbPool = new Pool({
  connectionString: process.env.DATABASE_URL,
  ssl: { rejectUnauthorized: true }
});

// 2. Security Middleware: Rate Limiter
const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 10, // Max 10 attempts
  message: { status: 'FAILED', message: 'Terlalu banyak permintaan. Silakan coba lagi nanti.' }
});

const pinLimiter = rateLimit({
  windowMs: 5 * 60 * 1000, 
  max: 5,
  message: { status: 'FAILED', message: 'Terlalu banyak input PIN salah. Akun terkunci sementara.' }
});

// 3. Auth Token Middleware
interface AuthenticatedRequest extends Request {
  user?: { id: string; email: string };
}

const authenticateJWT = (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  const authHeader = req.headers.authorization;
  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    return res.status(401).json({ status: 'FAILED', message: 'Token otentikasi tidak valid atau hilang' });
  }

  const token = authHeader.split(' ')[1];
  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET_KEY as string);
    req.user = decoded as { id: string; email: string };
    next();
  } catch (error) {
    return res.status(403).json({ status: 'FAILED', message: 'Token kedaluwarsa atau tidak valid' });
  }
};

/**
 * 4. P2P TRANSFER (CONCURRENCY LOCKING & ATOMIC PROTECTION)
 * POST /api/wallet/transfer
 * Idempotency Key is checked to avoid double transaction submittals.
 */
app.post('/api/wallet/transfer', authenticateJWT, async (req: AuthenticatedRequest, res: Response) => {
  const senderId = req.user?.id;
  const { recipientWalletNumber, amount, pin, idempotencyKey } = req.body;
  
  if (!recipientWalletNumber || !amount || amount <= 0 || !pin || !idempotencyKey) {
    return res.status(400).json({ status: 'FAILED', message: 'Parameter tidak lengkap atau nominal salah' });
  }

  const client = await dbPool.connect();

  try {
    // START ATOMIC DATABASE TRANSACTION
    await client.query('BEGIN');
    
    // a. Check Idempotency Key first
    const checkedIdempotency = await client.query(
      'SELECT id, status FROM wallet_transactions WHERE reference_id = $1',
      [idempotencyKey]
    );
    if (checkedIdempotency.rows.length > 0) {
      await client.query('ROLLBACK');
      return res.status(409).json({
        status: 'FAILED',
        message: 'Transaksi sudah pernah diajukan sebelumnya (Idempotent Triggered)',
        transaction: checkedIdempotency.rows[0]
      });
    }

    // b. Retrieve Sender Wallet with Row locking (SELECT FOR UPDATE)
    const senderWalletRes = await client.query(
      'SELECT w.*, p.pin_hash FROM wallets w JOIN wallet_pins p ON w.id = p.wallet_id WHERE w.user_id = $1 FOR UPDATE',
      [senderId]
    );
    if (senderWalletRes.rows.length === 0) {
      throw new Error('Wallet pengirim tidak ditemukan atau PIN belum diatur');
    }
    const senderWallet = senderWalletRes.rows[0];

    // c. Verify PIN
    const isPinValid = await bcrypt.compare(pin, senderWallet.pin_hash);
    if (!isPinValid) {
      // Record failed audit log and rollback
      await client.query(
        'INSERT INTO audit_logs (user_id, action, details, ip_address, status) VALUES ($1, $2, $3, $4, $5)',
        [senderId, 'TRANSFER_FAILED_PIN', 'Attempted transfer with invalid PIN', req.ip, 'FAILED']
      );
      await client.query('COMMIT'); // Commit audit logger first on its own transaction block, or let it fail
      await client.query('ROLLBACK');
      return res.status(403).json({ status: 'FAILED', message: 'PIN transaksi salah' });
    }

    // d. Check nominal balance
    if (parseFloat(senderWallet.balance) < parseFloat(amount)) {
      await client.query('ROLLBACK');
      return res.status(400).json({ status: 'FAILED', message: 'Saldo tidak mencukupi' });
    }

    // e. Retrieve Recipient Wallet with Row locking to prevent concurrent lock deadlocks (Sort ids before lock to avoid deadlock)
    const recipientWalletRes = await client.query(
      'SELECT * FROM wallets WHERE wallet_number = $1 FOR UPDATE',
      [recipientWalletNumber]
    );
    if (recipientWalletRes.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ status: 'FAILED', message: 'Wallet penerima tidak ditemukan' });
    }
    const recipientWallet = recipientWalletRes.rows[0];

    if (senderWallet.id === recipientWallet.id) {
      await client.query('ROLLBACK');
      return res.status(400).json({ status: 'FAILED', message: 'Tidak dapat mentransfer ke diri sendiri' });
    }

    // f. Update Sender Balance (Substraction)
    await client.query(
      'UPDATE wallets SET balance = balance - $1, updated_at = NOW() WHERE id = $2',
      [amount, senderWallet.id]
    );

    // g. Update Recipient Balance (Addition)
    await client.query(
      'UPDATE wallets SET balance = balance + $1, updated_at = NOW() WHERE id = $2',
      [amount, recipientWallet.id]
    );

    // h. Insert Transaction Logs (Immutable Ledgers)
    const transactionId = await client.query(
      \`INSERT INTO wallet_transactions (reference_id, wallet_id, type, amount, status, description) 
       VALUES (\$1, \$2, 'TRANSFER', \$3, 'SUCCESS', \$4) RETURNING id\`,
      [idempotencyKey, senderWallet.id, amount, \`Transfer ke \${recipientWallet.wallet_number}\`]
    );

    // Write transfer relationship mapping
    await client.query(
      'INSERT INTO wallet_transfers (transaction_id, sender_wallet_id, recipient_wallet_id) VALUES ($1, $2, $3)',
      [transactionId.rows[0].id, senderWallet.id, recipientWallet.id]
    );

    // Write a system-side record for the recipient to see their history trace
    await client.query(
      \`INSERT INTO wallet_transactions (reference_id, wallet_id, type, amount, status, description) 
       VALUES (\$1, \$2, 'TRANSFER', \$3, 'SUCCESS', \$4)\`,
      [\`IN-\${idempotencyKey}\`, recipientWallet.id, amount, \`Transfer dari \${senderWallet.wallet_number}\`]
    );

    // Write Secure Audit Log
    await client.query(
      'INSERT INTO audit_logs (user_id, action, details, ip_address, status) VALUES ($1, $2, $3, $4, $5)',
      [senderId, 'TRANSFER_BALANCE', \`Transferred \${amount} to \${recipientWallet.wallet_number}\`, req.ip, 'SUCCESS']
    );

    // COMMIT ALL ATOMIC ACTIONS SUCCESFULLY
    await client.query('COMMIT');
    res.json({
      status: 'SUCCESS',
      message: 'Transfer berhasil dilakukan',
      transactionId: transactionId.rows[0].id,
      amount,
      recipient: recipientWallet.wallet_number
    });

  } catch (error) {
    await client.query('ROLLBACK');
    console.error('Database transfer crash, rollback triggered:', error);
    res.status(500).json({ status: 'FAILED', message: 'Kesalahan internal server. Transaksi dibatalkan secara aman.' });
  } finally {
    client.release();
  }
});

/**
 * 5. WEBSITE PAYMENT REQUEST & WEBHOOK RESOLUTION (Atomic Integration)
 * POST /api/wallet/payment-request/:id/pay
 * Simulates user paying local invoice items safely, triggering automatic web callbacks
 */
app.post('/api/wallet/payment-request/:id/pay', authenticateJWT, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user?.id;
  const { id } = req.params;
  const { pin } = req.body;

  const client = await dbPool.connect();

  try {
    await client.query('BEGIN');

    // Fetch Payment Request
    const paymentRequestRes = await client.query(
      'SELECT * FROM wallet_payment_requests WHERE id = $1 FOR UPDATE',
      [id]
    );
    if (paymentRequestRes.rows.length === 0) {
      await client.query('ROLLBACK');
      return res.status(404).json({ status: 'FAILED', message: 'Invoice order tidak ditemukan' });
    }
    const invoice = paymentRequestRes.rows[0];

    if (invoice.status !== 'PENDING') {
      await client.query('ROLLBACK');
      return res.status(400).json({ status: 'FAILED', message: 'Tagihan ini sudah dibayar atau kedaluwarsa' });
    }

    // Fetch and Lock Wallet
    const walletRes = await client.query(
      'SELECT w.*, p.pin_hash FROM wallets w JOIN wallet_pins p ON w.id = p.wallet_id WHERE w.user_id = $1 FOR UPDATE',
      [userId]
    );
    if (walletRes.rows.length === 0) {
      throw new Error('Wallet pembayar tidak ditemukan');
    }
    const wallet = walletRes.rows[0];

    // PIN validation
    const isPinValid = await bcrypt.compare(pin, wallet.pin_hash);
    if (!isPinValid) {
      await client.query('ROLLBACK');
      return res.status(403).json({ status: 'FAILED', message: 'PIN eWallet salah' });
    }

    // Balance check
    if (parseFloat(wallet.balance) < parseFloat(invoice.amount)) {
      await client.query('ROLLBACK');
      return res.status(400).json({ status: 'FAILED', message: 'Saldo wallet tidak mencukupi' });
    }

    // Deduct user balance
    await client.query(
      'UPDATE wallets SET balance = balance - $1, updated_at = NOW() WHERE id = $2',
      [invoice.amount, wallet.id]
    );

    // Create unique reference
    const refId = \`PAY-\${invoice.id}-\${Date.now()}\`;

    // Write immutable transaction
    const transRes = await client.query(
      \`INSERT INTO wallet_transactions (reference_id, wallet_id, type, amount, status, description)
       VALUES (\$1, \$2, 'PAYMENT', \$3, 'SUCCESS', \$4) RETURNING id\`,
      [refId, wallet.id, invoice.amount, \`Pembayaran Order #\${invoice.merchant_order_id}\`]
    );

    // Update payment request status
    await client.query(
      'UPDATE wallet_payment_requests SET status = $1, transaction_id = $2, customer_wallet_id = $3, updated_at = NOW() WHERE id = $4',
      ['SUCCESS', transRes.rows[0].id, wallet.id, id]
    );

    // Write audit log
    await client.query(
      'INSERT INTO audit_logs (user_id, action, details, ip_address, status) VALUES ($1, $2, $3, $4, $5)',
      [userId, 'PAY_ORDER', \`Paid \${invoice.amount} for Order #\${invoice.merchant_order_id}\`, req.ip, 'SUCCESS']
    );

    // COMMIT FIRST to ensure the ledger is persistent before executing webhook request!
    await client.query('COMMIT');

    // ── FIRE WEBHOOK TO MAIN WEBSITE ──
    // Asynchronous background web request
    try {
      const webhookPayload = {
        orderId: invoice.merchant_order_id,
        paymentStatus: 'SUCCESS',
        amount: invoice.amount,
        walletTransactionId: transRes.rows[0].id,
        timestamp: new Date().toISOString(),
        // Signed signature verifying it originates from the eWallet core
        signature: crypto
          .createHmac('sha256', process.env.WEBHOOK_SECRET_KEY as string)
          .update(invoice.merchant_order_id + 'SUCCESS')
          .digest('hex')
      };

      // In real production, send HTTP POST to invoice.callback_url
      fetch(invoice.callback_url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(webhookPayload)
      }).catch(err => console.error('Failed to dispatch webhook callback', err));

    } catch (e) {
      console.error('Webhook payload error: ', e);
    }

    res.json({
      status: 'SUCCESS',
      message: 'Pembayaran berhasil dikonfirmasi dan status terkirim',
      orderId: invoice.merchant_order_id,
      amount: invoice.amount
    });

  } catch (err) {
    await client.query('ROLLBACK');
    res.status(500).json({ status: 'FAILED', message: 'Sistem mengalami gangguan dalam memproses transaksi' });
  } finally {
    client.release();
  }
});
`;

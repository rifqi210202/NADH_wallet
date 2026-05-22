export const pgsqlSchema = `-- PostgreSQL Schema for Secure eWallet System
-- Enforces absolute atomic integrity, immutable transaction histories, and secure PIN structures.

-- Enable UUID Extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. USERS TABLE
CREATE TABLE users (
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
);

-- 2. WALLETS TABLE
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    wallet_number VARCHAR(16) UNIQUE NOT NULL, -- Format: 8899 + phone suffix or random
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) DEFAULT 'IDR',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- Constraint: Prevent negative balance at the database engine level (Hard Rule)
    CONSTRAINT chk_positive_balance CHECK (balance >= 0.00)
);

-- 3. WALLET PINS TABLE (PIN security)
CREATE TABLE wallet_pins (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    wallet_id UUID UNIQUE NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
    pin_hash VARCHAR(255) NOT NULL, -- Hashed PIN (e.g., argon2 or bcrypt)
    attempts_count INT DEFAULT 0,
    locked_until TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. REFRESH TOKENS TABLE (Token rotation)
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_string VARCHAR(512) UNIQUE NOT NULL,
    is_revoked BOOLEAN DEFAULT FALSE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 5. TYPE DEFINITIONS FOR TRANSACTIONS
CREATE TYPE transaction_type AS ENUM ('TOPUP', 'TRANSFER', 'PAYMENT', 'REFUND');
CREATE TYPE transaction_status AS ENUM ('PENDING', 'SUCCESS', 'FAILED', 'CANCELLED');

-- 6. WALLET TRANSACTIONS TABLE (Immutable ledger)
CREATE TABLE wallet_transactions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reference_id VARCHAR(50) UNIQUE NOT NULL, -- Idempotency key / Reference transaction number
    wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    type transaction_type NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    description VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT chk_amount_positive CHECK (amount > 0.00)
);

-- 7. WALLET TOPUPS TABLE (Payment Gateway linked)
CREATE TABLE wallet_topups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID NOT NULL REFERENCES wallet_transactions(id) ON DELETE RESTRICT,
    payment_method VARCHAR(50) NOT NULL,
    payment_gateway_ref VARCHAR(100) UNIQUE, -- Xendit/Midtrans Order ID
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. WALLET TRANSFERS TABLE (Peer-to-Peer linking)
CREATE TABLE wallet_transfers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID NOT NULL REFERENCES wallet_transactions(id) ON DELETE RESTRICT,
    sender_wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    recipient_wallet_id UUID NOT NULL REFERENCES wallets(id) ON DELETE RESTRICT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. WALLET PAYMENT REQUESTS TABLE (Integration with website order payment)
CREATE TABLE wallet_payment_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    transaction_id UUID REFERENCES wallet_transactions(id) ON DELETE SET NULL,
    merchant_order_id VARCHAR(100) NOT NULL, -- Order ID from the main website
    amount DECIMAL(15, 2) NOT NULL,
    status transaction_status NOT NULL DEFAULT 'PENDING',
    callback_url VARCHAR(255) NOT NULL, -- Webhook target url
    customer_wallet_id UUID REFERENCES wallets(id) ON DELETE RESTRICT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. AUDIT LOGS TABLE (Compliance logging)
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL, -- e.g. "AUTH_LOGIN", "TRANSFER_PIN_SUCCESS", "ATTEMPT_DOUBLE_SPEND"
    details TEXT,
    ip_address VARCHAR(45) NOT NULL,
    status VARCHAR(10) NOT NULL, -- SUCCESS, FAILED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

--------------------------------------------------------------------------------
-- INDEXES FOR HIGH-THROUGHPUT SEARCH & SECURITY INTEGITY
--------------------------------------------------------------------------------
CREATE INDEX idx_wallets_number ON wallets(wallet_number);
CREATE INDEX idx_transactions_wallet_id ON wallet_transactions(wallet_id);
CREATE INDEX idx_transactions_created ON wallet_transactions(created_at DESC);
CREATE INDEX idx_audit_logs_user ON audit_logs(user_id);
CREATE INDEX idx_payment_requests_order ON wallet_payment_requests(merchant_order_id);

--------------------------------------------------------------------------------
-- CONCURRENCY SAFEGUARD EXAMPLE (DB PROCESS TRANSACTION EXAMPLE)
--------------------------------------------------------------------------------
-- Explanation: When modifying user balance, standard SELECT can cause race conditions.
-- Always lock rows with "SELECT balance FROM wallets WHERE id = $1 FOR UPDATE;"
-- Inside NestJS/Node.js Sequelize or TypeORM, ensure running inside TRANSACTION.
`;

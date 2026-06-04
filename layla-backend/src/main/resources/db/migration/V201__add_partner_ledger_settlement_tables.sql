-- Add idempotency_key to transactions
ALTER TABLE transactions ADD COLUMN idempotency_key VARCHAR(36) UNIQUE AFTER reference_number;

-- Add payin_partner_id to transactions
ALTER TABLE transactions ADD COLUMN payin_partner_id BIGINT AFTER payout_partner_id;

-- Add relation_id to beneficiaries
ALTER TABLE beneficiaries ADD COLUMN relation_id BIGINT AFTER id_type;
ALTER TABLE beneficiaries ADD COLUMN email VARCHAR(255) AFTER full_name;
ALTER TABLE beneficiaries ADD COLUMN date_of_birth DATE AFTER email;
ALTER TABLE beneficiaries ADD COLUMN address VARCHAR(500) AFTER date_of_birth;
ALTER TABLE beneficiaries ADD COLUMN currency VARCHAR(3) AFTER country;

-- Transaction ledger (double-entry bookkeeping)
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT NOT NULL,
    debit_account VARCHAR(100) NOT NULL,
    credit_account VARCHAR(100) NOT NULL,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(10),
    entry_type VARCHAR(30) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ledger_txn (transaction_id),
    INDEX idx_ledger_type (entry_type),
    FOREIGN KEY (transaction_id) REFERENCES transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Payout partners
CREATE TABLE IF NOT EXISTS payout_partners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_name VARCHAR(255) NOT NULL,
    user_id BIGINT,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Payout partner countries
CREATE TABLE IF NOT EXISTS payout_partner_countries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    country_code VARCHAR(3) NOT NULL,
    country_name VARCHAR(100),
    currency VARCHAR(3),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (partner_id) REFERENCES payout_partners(id),
    INDEX idx_partner_country (partner_id, country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Pay-in partners
CREATE TABLE IF NOT EXISTS payin_partners (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_name VARCHAR(255) NOT NULL,
    user_id BIGINT,
    contact_email VARCHAR(255),
    contact_phone VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Corridor-partner mappings
CREATE TABLE IF NOT EXISTS corridor_partner_mappings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    partner_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_corridor_mapping (from_currency, to_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Corridor fee config (fee sharing between partners)
CREATE TABLE IF NOT EXISTS corridor_fee_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_currency VARCHAR(3) NOT NULL,
    to_currency VARCHAR(3) NOT NULL,
    payin_partner_id BIGINT,
    payin_share_type VARCHAR(20) DEFAULT 'PERCENTAGE',
    payin_share_value DECIMAL(19,4) DEFAULT 0,
    payout_partner_id BIGINT,
    payout_share_type VARCHAR(20) DEFAULT 'PERCENTAGE',
    payout_share_value DECIMAL(19,4) DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fee_config_corridor (from_currency, to_currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Payout partner ledger
CREATE TABLE IF NOT EXISTS partner_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    transaction_id BIGINT,
    txn_display_id VARCHAR(50),
    entry_type VARCHAR(20) NOT NULL,
    local_amount DECIMAL(19,4),
    local_currency VARCHAR(3),
    usd_amount DECIMAL(19,4),
    fx_rate_used DECIMAL(19,8),
    balance_usd DECIMAL(19,4),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_partner_ledger_partner (partner_id),
    INDEX idx_partner_ledger_ts (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Pay-in partner ledger
CREATE TABLE IF NOT EXISTS payin_partner_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    transaction_id BIGINT,
    txn_display_id VARCHAR(50),
    entry_type VARCHAR(20) NOT NULL,
    local_amount DECIMAL(19,4),
    local_currency VARCHAR(3),
    usd_amount DECIMAL(19,4),
    fx_rate_used DECIMAL(19,8),
    balance_usd DECIMAL(19,4),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_payin_ledger_partner (partner_id),
    INDEX idx_payin_ledger_ts (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Platform ledger
CREATE TABLE IF NOT EXISTS platform_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id BIGINT,
    txn_display_id VARCHAR(50),
    entry_type VARCHAR(20) NOT NULL,
    local_amount DECIMAL(19,4),
    local_currency VARCHAR(3),
    usd_amount DECIMAL(19,4),
    fx_rate_used DECIMAL(19,8),
    balance_usd DECIMAL(19,4),
    account_type VARCHAR(50),
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_platform_ledger_account (account_type),
    INDEX idx_platform_ledger_ts (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Settlements
CREATE TABLE IF NOT EXISTS settlements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_type VARCHAR(30) NOT NULL,
    from_party VARCHAR(30) NOT NULL,
    from_party_id BIGINT,
    to_party VARCHAR(30) NOT NULL,
    to_party_id BIGINT,
    amount DECIMAL(19,4) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    reference VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING',
    initiated_by VARCHAR(255),
    approved_by VARCHAR(255),
    rejected_reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_settlement_status (status),
    INDEX idx_settlement_type (settlement_type),
    INDEX idx_settlement_ts (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Settlement rates (global)
CREATE TABLE IF NOT EXISTS settlement_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    currency VARCHAR(3) NOT NULL UNIQUE,
    rate_to_usd DECIMAL(19,6) NOT NULL,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Partner-specific settlement rate overrides
CREATE TABLE IF NOT EXISTS partner_settlement_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    rate_to_usd DECIMAL(19,6) NOT NULL,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_partner_currency (partner_id, currency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Bank database
CREATE TABLE IF NOT EXISTS bank_database (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    bank_identifier VARCHAR(50),
    bank_address VARCHAR(500),
    branch_name VARCHAR(255),
    city VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_bank_country (country_code),
    INDEX idx_bank_identifier (bank_identifier),
    INDEX idx_bank_name (bank_name(100))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Country bank configuration
CREATE TABLE IF NOT EXISTS country_bank_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL UNIQUE,
    country_name VARCHAR(100),
    currency VARCHAR(3),
    identifier_name VARCHAR(30),
    identifier_label VARCHAR(50),
    identifier_format VARCHAR(50),
    identifier_length INT,
    auto_lookup BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Payout types per country
CREATE TABLE IF NOT EXISTS payout_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_name VARCHAR(100),
    country_code VARCHAR(3) NOT NULL,
    currency VARCHAR(3),
    payout_type VARCHAR(30) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_payout_country (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Payment methods per country
CREATE TABLE IF NOT EXISTS payment_methods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_name VARCHAR(100),
    country_code VARCHAR(3) NOT NULL,
    currency VARCHAR(3),
    payment_method VARCHAR(30) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_payment_country (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Mobile money services per country
CREATE TABLE IF NOT EXISTS mobile_money_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    country_name VARCHAR(100),
    service_name VARCHAR(100) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_mobile_country (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Cash collection points per country
CREATE TABLE IF NOT EXISTS cash_collection_points (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    country_name VARCHAR(100),
    point_name VARCHAR(255) NOT NULL,
    address VARCHAR(500),
    city VARCHAR(100),
    contact_number VARCHAR(50),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_cash_country (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

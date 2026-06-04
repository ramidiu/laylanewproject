CREATE TABLE IF NOT EXISTS fx_rate_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_currency VARCHAR(3) NOT NULL,
    target_currency VARCHAR(3) NOT NULL,
    rate DECIMAL(18,8) NOT NULL,
    source ENUM('OPEN_EXCHANGE_RATES', 'MANUAL', 'FALLBACK') NOT NULL,
    fetched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_fx_rate_pair (base_currency, target_currency),
    INDEX idx_fx_rate_fetched (fetched_at)
);

CREATE TABLE IF NOT EXISTS fx_margins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    send_country VARCHAR(3),
    receive_country VARCHAR(3),
    send_currency VARCHAR(3) NOT NULL,
    receive_currency VARCHAR(3) NOT NULL,
    delivery_method ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP'),
    margin_percentage DECIMAL(6,4) DEFAULT 0,
    margin_fixed DECIMAL(18,4) DEFAULT 0,
    customer_tier ENUM('TIER_0','TIER_1','TIER_2','TIER_3'),
    min_amount DECIMAL(18,2),
    max_amount DECIMAL(18,2),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_fx_margins_pair (send_currency, receive_currency)
);

CREATE TABLE IF NOT EXISTS nostro_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bank_name VARCHAR(200) NOT NULL,
    account_number VARCHAR(50),
    currency VARCHAR(3) NOT NULL,
    country VARCHAR(3) NOT NULL,
    current_balance DECIMAL(18,4) DEFAULT 0,
    low_balance_threshold DECIMAL(18,4),
    last_reconciled_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS competitor_rates (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    competitor_name VARCHAR(100) NOT NULL,
    send_currency VARCHAR(3) NOT NULL,
    receive_currency VARCHAR(3) NOT NULL,
    customer_rate DECIMAL(18,8) NOT NULL,
    fee DECIMAL(18,4),
    total_cost_per_unit DECIMAL(18,8),
    captured_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_competitor_pair (send_currency, receive_currency)
);

CREATE TABLE IF NOT EXISTS corridors (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    send_country VARCHAR(3) NOT NULL,
    receive_country VARCHAR(3) NOT NULL,
    send_currency VARCHAR(3) NOT NULL,
    receive_currency VARCHAR(3) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    min_amount DECIMAL(18,2) DEFAULT 10,
    max_amount DECIMAL(18,2) DEFAULT 50000,
    daily_limit DECIMAL(18,2),
    monthly_limit DECIMAL(18,2),
    required_kyc_tier ENUM('TIER_0','TIER_1','TIER_2','TIER_3') DEFAULT 'TIER_0',
    risk_level ENUM('LOW', 'MEDIUM', 'HIGH') DEFAULT 'LOW',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_corridors_active (is_active)
);

CREATE TABLE IF NOT EXISTS corridor_fees (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    corridor_id BIGINT NOT NULL,
    delivery_method ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP'),
    fee_type ENUM('FLAT', 'PERCENTAGE', 'TIERED') NOT NULL,
    flat_fee DECIMAL(18,4) DEFAULT 0,
    percentage_fee DECIMAL(6,4) DEFAULT 0,
    tier_rules JSON,
    currency VARCHAR(3) DEFAULT 'GBP',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (corridor_id) REFERENCES corridors(id)
);

CREATE TABLE IF NOT EXISTS corridor_delivery_methods (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    corridor_id BIGINT NOT NULL,
    delivery_method ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP') NOT NULL,
    payout_partner_id BIGINT,
    is_active BOOLEAN DEFAULT TRUE,
    processing_time_minutes INT DEFAULT 1440,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (corridor_id) REFERENCES corridors(id)
);

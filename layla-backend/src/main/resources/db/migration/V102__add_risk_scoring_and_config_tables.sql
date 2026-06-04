-- Country risk tiers for risk scoring
CREATE TABLE IF NOT EXISTS country_risk_tiers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL,
    country_name VARCHAR(100) NOT NULL,
    risk_tier VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    risk_points INT NOT NULL DEFAULT 3,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_country_risk (country_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Transaction limits per risk level
CREATE TABLE IF NOT EXISTS transaction_limits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    risk_level VARCHAR(20) NOT NULL,
    limit_type VARCHAR(30) NOT NULL,
    max_amount DECIMAL(19,4) NOT NULL,
    max_count INT DEFAULT 0,
    currency VARCHAR(3) DEFAULT 'GBP',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_risk_limit (risk_level, limit_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- KYC document types configurable per country
CREATE TABLE IF NOT EXISTS kyc_document_types (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    country_code VARCHAR(3) NOT NULL DEFAULT 'ALL',
    country_name VARCHAR(100),
    category VARCHAR(20) NOT NULL,
    document_name VARCHAR(100) NOT NULL,
    sides INT DEFAULT 1,
    has_id_number BOOLEAN DEFAULT FALSE,
    id_number_label VARCHAR(50),
    id_number_format VARCHAR(50),
    has_expiry BOOLEAN DEFAULT FALSE,
    has_issue_date BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,
    display_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kyc_doc_country (country_code, category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- System configuration (key-value store for Super Admin)
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(100) NOT NULL UNIQUE,
    config_value VARCHAR(500),
    description TEXT,
    updated_by VARCHAR(255),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Relations (beneficiary relationship types)
CREATE TABLE IF NOT EXISTS relations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    relation_name VARCHAR(50) NOT NULL UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS kyc_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    document_type ENUM('PASSPORT', 'DRIVING_LICENCE', 'NATIONAL_ID', 'PROOF_OF_ADDRESS', 'SOURCE_OF_FUNDS') NOT NULL,
    document_number VARCHAR(100),
    file_path VARCHAR(500) NOT NULL,
    file_hash VARCHAR(64),
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED') DEFAULT 'PENDING',
    verified_by BIGINT,
    verified_at TIMESTAMP NULL,
    rejection_reason TEXT,
    expiry_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_kyc_docs_user (user_id),
    INDEX idx_kyc_docs_status (status)
);

CREATE TABLE IF NOT EXISTS kyc_verifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    verification_type ENUM('IDENTITY', 'ADDRESS', 'LIVENESS', 'PEP_CHECK', 'SANCTIONS_CHECK', 'ADVERSE_MEDIA') NOT NULL,
    provider ENUM('MANUAL', 'ONFIDO', 'JUMIO', 'SUMSUB') DEFAULT 'MANUAL',
    provider_reference VARCHAR(255),
    status ENUM('PENDING', 'PASSED', 'FAILED') NOT NULL,
    result_data JSON,
    verified_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id),
    INDEX idx_kyc_verifications_user (user_id)
);

CREATE TABLE IF NOT EXISTS kyc_tier_limits (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tier ENUM('TIER_0', 'TIER_1', 'TIER_2', 'TIER_3') NOT NULL,
    max_per_transaction DECIMAL(18,2) NOT NULL,
    max_daily DECIMAL(18,2) NOT NULL,
    max_weekly DECIMAL(18,2) NOT NULL,
    max_monthly DECIMAL(18,2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'GBP',
    corridor_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kyc_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    action ENUM('DOCUMENT_UPLOADED', 'VERIFICATION_INITIATED', 'STATUS_CHANGED', 'SCREENING_RUN', 'MANUAL_OVERRIDE', 'TIER_UPGRADED') NOT NULL,
    actor_id BIGINT,
    actor_role VARCHAR(50),
    details JSON,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_kyc_audit_user (user_id)
);

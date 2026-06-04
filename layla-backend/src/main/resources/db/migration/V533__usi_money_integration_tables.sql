-- ============================================================================
-- V533: USI Money integration tables — remitters, beneficiaries, transactions
-- ----------------------------------------------------------------------------
-- Mirrors the legacy USI integration's local state. Each Layla-side entity
-- (user / beneficiary / transaction) gets a USI-side counterpart row carrying
-- the USI-assigned id and the latest USI status returned by their XML API.
-- ============================================================================

CREATE TABLE IF NOT EXISTS usi_remitters (
    id               BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id          BIGINT      NOT NULL,
    remitter_id      VARCHAR(64) NOT NULL,
    status           VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    verified         TINYINT(1)  NOT NULL DEFAULT 0,
    created_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usi_remitter_user (user_id),
    UNIQUE KEY uk_usi_remitter_id   (remitter_id),
    INDEX idx_usi_remitter_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usi_beneficiaries (
    id                  BIGINT PRIMARY KEY AUTO_INCREMENT,
    local_beneficiary_id VARCHAR(64) NOT NULL,
    usi_beneficiary_id  VARCHAR(64) NOT NULL,
    status              VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usi_benef_local (local_beneficiary_id),
    UNIQUE KEY uk_usi_benef_id    (usi_beneficiary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS usi_transactions (
    id                       BIGINT PRIMARY KEY AUTO_INCREMENT,
    transaction_id           VARCHAR(64) NOT NULL,
    trans_session_id         VARCHAR(64),
    reference_number         VARCHAR(64),
    remitter_id              VARCHAR(64),
    remitter_name            VARCHAR(255),
    beneficiary_id           VARCHAR(64),
    beneficiary_name         VARCHAR(255),
    trans_type               VARCHAR(50),
    destination_country      VARCHAR(100),
    source_currency          VARCHAR(8),
    destination_currency     VARCHAR(8),
    source_transfer_amount   DECIMAL(18,4),
    destination_amount       DECIMAL(18,4),
    rate                     DECIMAL(18,8),
    commission               DECIMAL(18,4),
    agent_fee                DECIMAL(18,4),
    hq_fee                   DECIMAL(18,4),
    tax                      DECIMAL(18,4),
    remitter_pay_amount      DECIMAL(18,4),
    agent_deduction          DECIMAL(18,4),
    agent_to_pay_hq          DECIMAL(18,4),
    delivery_date            DATETIME,
    payment_token            VARCHAR(255),
    payment_status           VARCHAR(60),
    usi_status               VARCHAR(60),
    status                   VARCHAR(60),
    error_message            TEXT,
    created_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at               TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usi_txn_local       (transaction_id),
    UNIQUE KEY uk_usi_txn_session     (trans_session_id),
    INDEX idx_usi_txn_status (status),
    INDEX idx_usi_txn_usistatus (usi_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

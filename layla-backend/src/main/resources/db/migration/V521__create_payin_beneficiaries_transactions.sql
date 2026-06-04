CREATE TABLE IF NOT EXISTS payin_beneficiaries (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id    VARCHAR(36)  NOT NULL,
    name           VARCHAR(200) NOT NULL,
    bank_name      VARCHAR(200) NOT NULL,
    account_number VARCHAR(100) NOT NULL,
    ifsc_code      VARCHAR(20)  NULL,
    created_at     DATETIME     NOT NULL,
    updated_at     DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payin_ben_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payin_transactions (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    transaction_id        VARCHAR(36)  NOT NULL,
    customer_id           VARCHAR(36)  NOT NULL,
    customer_source       VARCHAR(20)  NOT NULL,
    beneficiary_id        BIGINT       NOT NULL,
    amount                DECIMAL(18,4) NOT NULL,
    currency              VARCHAR(10)  NOT NULL,
    payment_mode          VARCHAR(30)  NOT NULL,
    status                VARCHAR(20)  NOT NULL DEFAULT 'INITIATED',
    external_reference_id VARCHAR(100) NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payin_txn_id       (transaction_id),
    UNIQUE KEY uk_payin_txn_ext_ref  (external_reference_id),
    INDEX idx_payin_txn_customer_id  (customer_id),
    INDEX idx_payin_txn_beneficiary  (beneficiary_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

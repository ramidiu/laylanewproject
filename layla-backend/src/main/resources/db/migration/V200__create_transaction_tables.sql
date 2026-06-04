-- ============================================================
-- V200: Transaction Service Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS beneficiaries (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT          NOT NULL,
    full_name       VARCHAR(255)    NOT NULL,
    country         VARCHAR(100)    NOT NULL,
    delivery_method ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP') NOT NULL,
    bank_name       VARCHAR(255),
    account_number  VARCHAR(100),
    iban            VARCHAR(50),
    swift_bic       VARCHAR(20),
    sort_code       VARCHAR(20),
    mobile_number   VARCHAR(30),
    mobile_provider VARCHAR(100),
    id_number       VARCHAR(100),
    id_type         VARCHAR(50),
    is_favourite    BOOLEAN         NOT NULL DEFAULT FALSE,
    is_blocked      BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_beneficiaries_user_id (user_id),
    INDEX idx_beneficiaries_user_favourite (user_id, is_favourite)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transactions (
    id                      BIGINT AUTO_INCREMENT PRIMARY KEY,
    reference_number        VARCHAR(50)     NOT NULL UNIQUE,
    sender_id               BIGINT          NOT NULL,
    beneficiary_id          BIGINT          NOT NULL,
    corridor_id             BIGINT          NOT NULL,
    status                  ENUM('CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED') NOT NULL DEFAULT 'CREATED',
    delivery_method         ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP') NOT NULL,
    send_amount             DECIMAL(18,4)   NOT NULL,
    send_currency           VARCHAR(3)      NOT NULL,
    receive_amount          DECIMAL(18,4)   NOT NULL,
    receive_currency        VARCHAR(3)      NOT NULL,
    exchange_rate           DECIMAL(18,8)   NOT NULL,
    applied_rate            DECIMAL(18,8)   NOT NULL,
    locked_rate             DECIMAL(18,8),
    rate_locked_at          TIMESTAMP       NULL,
    rate_lock_expires_at    TIMESTAMP       NULL,
    fee_amount              DECIMAL(18,4)   NOT NULL DEFAULT 0,
    fee_currency            VARCHAR(3),
    fx_margin_amount        DECIMAL(18,4)   NOT NULL DEFAULT 0,
    total_debit_amount      DECIMAL(18,4)   NOT NULL,
    compliance_hold_reason  TEXT,
    payout_partner_id       BIGINT,
    payout_reference        VARCHAR(255),
    payout_confirmed_at     TIMESTAMP       NULL,
    payment_method_type     ENUM('CARD','BANK_TRANSFER','OPEN_BANKING','WALLET','AGENT_CASH') NOT NULL,
    payment_reference       VARCHAR(255),
    is_recurring            BOOLEAN         NOT NULL DEFAULT FALSE,
    recurring_schedule_id   BIGINT,
    risk_score              INT,
    notes                   TEXT,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_transactions_reference (reference_number),
    INDEX idx_transactions_sender (sender_id),
    INDEX idx_transactions_sender_status (sender_id, status),
    INDEX idx_transactions_status (status),
    INDEX idx_transactions_corridor (corridor_id),
    INDEX idx_transactions_beneficiary (beneficiary_id),
    INDEX idx_transactions_created_at (created_at),
    INDEX idx_transactions_payout_partner (payout_partner_id),

    CONSTRAINT fk_transactions_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS transaction_status_history (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id  BIGINT          NOT NULL,
    from_status     ENUM('CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED'),
    to_status       ENUM('CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED') NOT NULL,
    actor_id        BIGINT,
    actor_type      ENUM('SYSTEM','USER','ADMIN','COMPLIANCE','PAYOUT_PARTNER','AGENT') NOT NULL DEFAULT 'SYSTEM',
    reason          TEXT,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_status_history_transaction (transaction_id),
    INDEX idx_status_history_created (created_at),

    CONSTRAINT fk_status_history_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS recurring_transfers (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             BIGINT          NOT NULL,
    beneficiary_id      BIGINT          NOT NULL,
    corridor_id         BIGINT          NOT NULL,
    delivery_method     ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP') NOT NULL,
    send_amount         DECIMAL(18,4)   NOT NULL,
    send_currency       VARCHAR(3)      NOT NULL,
    receive_currency    VARCHAR(3)      NOT NULL,
    frequency           ENUM('WEEKLY','BIWEEKLY','MONTHLY','CUSTOM') NOT NULL,
    custom_interval_days INT,
    next_execution_date DATE            NOT NULL,
    last_execution_date DATE,
    status              ENUM('ACTIVE','PAUSED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    payment_method_type ENUM('CARD','BANK_TRANSFER','OPEN_BANKING','WALLET','AGENT_CASH') NOT NULL,
    total_executions    INT             NOT NULL DEFAULT 0,
    max_executions      INT,
    notes               TEXT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_recurring_user (user_id),
    INDEX idx_recurring_status (status),
    INDEX idx_recurring_next_exec (status, next_execution_date),

    CONSTRAINT fk_recurring_beneficiary FOREIGN KEY (beneficiary_id) REFERENCES beneficiaries (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_id      BIGINT          NOT NULL,
    user_id             BIGINT          NOT NULL,
    method_type         ENUM('CARD','BANK_TRANSFER','OPEN_BANKING','WALLET','AGENT_CASH') NOT NULL,
    provider            VARCHAR(100),
    provider_reference  VARCHAR(255),
    amount              DECIMAL(18,4)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    status              ENUM('INITIATED','PENDING','COMPLETED','FAILED','REFUNDED') NOT NULL DEFAULT 'INITIATED',
    payment_data        JSON,
    completed_at        TIMESTAMP       NULL,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_payments_transaction (transaction_id),
    INDEX idx_payments_user (user_id),
    INDEX idx_payments_provider_ref (provider_reference),
    INDEX idx_payments_status (status),

    CONSTRAINT fk_payments_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bank_statements (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id          VARCHAR(100)    NOT NULL,
    statement_date      DATE            NOT NULL,
    reference           VARCHAR(255),
    amount              DECIMAL(18,4)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    counterparty        VARCHAR(255),
    status              ENUM('UNMATCHED','MATCHED','DISPUTED') NOT NULL DEFAULT 'UNMATCHED',
    matched_payment_id  BIGINT,
    imported_at         TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_bank_stmts_status (status),
    INDEX idx_bank_stmts_date (statement_date),
    INDEX idx_bank_stmts_account (account_id),
    INDEX idx_bank_stmts_matched (matched_payment_id),

    CONSTRAINT fk_bank_stmts_payment FOREIGN KEY (matched_payment_id) REFERENCES payments (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

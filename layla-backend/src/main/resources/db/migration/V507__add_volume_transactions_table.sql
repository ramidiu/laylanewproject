CREATE TABLE IF NOT EXISTS volume_transactions (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    user_id             BIGINT         NOT NULL,
    merchant_reference  VARCHAR(100)   NOT NULL,
    volume_transaction_id VARCHAR(100) NULL,
    amount              DECIMAL(19,4)  NOT NULL,
    currency            VARCHAR(10)    NOT NULL DEFAULT 'GBP',
    sending_country     VARCHAR(10)    NOT NULL DEFAULT 'GB',
    status              ENUM('INITIATED','PROCESSING','SUCCESS','FAILED','PENDING')
                                       NOT NULL DEFAULT 'INITIATED',
    redirect_url_success VARCHAR(500)  NULL,
    redirect_url_failure VARCHAR(500)  NULL,
    created_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_volume_merchant_ref (merchant_reference),
    INDEX idx_volume_user_id (user_id),
    INDEX idx_volume_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

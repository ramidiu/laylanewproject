CREATE TABLE IF NOT EXISTS trust_payments (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_ref VARCHAR(100),
    order_reference VARCHAR(100),
    request_reference VARCHAR(100),
    transaction_reference VARCHAR(200),
    amount          DECIMAL(15,4),
    currency_iso    VARCHAR(10)  DEFAULT 'GBP',
    payment_status  VARCHAR(50),
    settle_status   VARCHAR(50),
    error_code      VARCHAR(20),
    raw_params      TEXT,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_trust_order_ref (order_reference),
    INDEX idx_trust_txn_ref   (transaction_ref)
);

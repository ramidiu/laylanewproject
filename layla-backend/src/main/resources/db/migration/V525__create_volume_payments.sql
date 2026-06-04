CREATE TABLE volume_payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id VARCHAR(100),
    merchant_payment_id VARCHAR(100) NOT NULL,
    transaction_id VARCHAR(100),
    transaction_reference VARCHAR(100),
    currency_iso VARCHAR(10) NOT NULL,
    amount DECIMAL(18, 4) NOT NULL,
    payment_status VARCHAR(30) NOT NULL DEFAULT 'INITIATED',
    settle_status VARCHAR(30),
    is_external TINYINT(1) DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_volume_merchant_payment_id UNIQUE (merchant_payment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

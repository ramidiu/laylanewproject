CREATE TABLE IF NOT EXISTS payin_customer_documents (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    customer_id     VARCHAR(36)  NOT NULL,
    doc_side        VARCHAR(20)  NOT NULL,
    doc_category    VARCHAR(50)  NOT NULL,
    document_number VARCHAR(100) NULL,
    issue_date      DATE         NULL,
    expiry_date     DATE         NULL,
    file_path       VARCHAR(500) NOT NULL,
    file_name       VARCHAR(255) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    created_at      DATETIME     NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_payin_doc_customer_id (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

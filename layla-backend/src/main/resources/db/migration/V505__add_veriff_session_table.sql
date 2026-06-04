CREATE TABLE veriff_sessions (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    veriff_session_id VARCHAR(100) UNIQUE,
    veriff_status VARCHAR(50)  NOT NULL DEFAULT 'INITIATED',
    vendor_data   VARCHAR(255),
    session_url   TEXT,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_veriff_sessions_user_id (user_id),
    INDEX idx_veriff_sessions_session_id (veriff_session_id)
);

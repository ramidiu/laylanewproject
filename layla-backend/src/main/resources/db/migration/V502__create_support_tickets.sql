CREATE TABLE support_tickets (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_number VARCHAR(20) NOT NULL UNIQUE,
    user_id VARCHAR(36) NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    user_name VARCHAR(255),
    subject VARCHAR(500) NOT NULL,
    issue_type ENUM('PAYMENT_ISSUE','TRANSFER_DELAY','ACCOUNT_ACCESS','KYC_VERIFICATION','REFUND_REQUEST','TECHNICAL_ISSUE','GENERAL_INQUIRY') NOT NULL,
    priority ENUM('LOW','MEDIUM','HIGH','URGENT') NOT NULL DEFAULT 'MEDIUM',
    status ENUM('OPEN','IN_PROGRESS','AWAITING_CUSTOMER','RESOLVED','CLOSED') NOT NULL DEFAULT 'OPEN',
    assigned_to BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    INDEX idx_ticket_user (user_id),
    INDEX idx_ticket_status (status),
    INDEX idx_ticket_number (ticket_number)
);

CREATE TABLE support_ticket_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    sender_type ENUM('CUSTOMER','AGENT','SYSTEM') NOT NULL,
    sender_id VARCHAR(36) NULL,
    sender_name VARCHAR(255),
    message TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES support_tickets(id),
    INDEX idx_message_ticket (ticket_id)
);

CREATE TABLE support_ticket_attachments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id BIGINT NOT NULL,
    message_id BIGINT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_path VARCHAR(1000) NOT NULL,
    file_size BIGINT,
    content_type VARCHAR(100),
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (ticket_id) REFERENCES support_tickets(id),
    FOREIGN KEY (message_id) REFERENCES support_ticket_messages(id),
    INDEX idx_attachment_ticket (ticket_id)
);

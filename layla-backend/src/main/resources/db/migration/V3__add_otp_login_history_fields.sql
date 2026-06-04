-- Add email_verified flag to users table for 2-stage registration
ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE AFTER mfa_secret;

-- Add nationality, country_of_residence, country_code, gender, occupation for risk scoring
ALTER TABLE users ADD COLUMN nationality VARCHAR(100) AFTER country;
ALTER TABLE users ADD COLUMN country_of_residence VARCHAR(100) AFTER nationality;
ALTER TABLE users ADD COLUMN country_code VARCHAR(10) AFTER country_of_residence;
ALTER TABLE users ADD COLUMN gender VARCHAR(20) AFTER date_of_birth;
ALTER TABLE users ADD COLUMN occupation VARCHAR(100) AFTER gender;

-- Add risk scoring fields
ALTER TABLE users ADD COLUMN risk_score VARCHAR(20) DEFAULT 'MEDIUM' AFTER occupation;
ALTER TABLE users ADD COLUMN risk_points INT DEFAULT 0 AFTER risk_score;
ALTER TABLE users ADD COLUMN risk_override VARCHAR(20) AFTER risk_points;

-- Login history table
CREATE TABLE IF NOT EXISTS login_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    user_email VARCHAR(255),
    user_role VARCHAR(50),
    event_type VARCHAR(50) NOT NULL,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_login_user (user_id),
    INDEX idx_login_ts (created_at),
    INDEX idx_login_event (event_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Access logs table (cross-cutting)
CREATE TABLE IF NOT EXISTS access_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    user_email VARCHAR(255),
    user_role VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    service_name VARCHAR(50),
    http_method VARCHAR(10),
    endpoint VARCHAR(500),
    query_params TEXT,
    response_status INT,
    response_time_ms BIGINT,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_access_user (user_id),
    INDEX idx_access_ts (created_at),
    INDEX idx_access_endpoint (endpoint(255))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Enhanced audit logs table (cross-cutting)
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    user_email VARCHAR(255),
    user_role VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    service_name VARCHAR(50),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100),
    entity_id VARCHAR(100),
    description TEXT,
    old_value JSON,
    new_value JSON,
    metadata JSON,
    status VARCHAR(50),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_ts (created_at),
    INDEX idx_audit_action (action),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Add new roles: SUPPORT, FINANCE, PAYIN_PARTNER
INSERT IGNORE INTO roles (name, description) VALUES
('SUPPORT', 'Support staff with read-only access'),
('FINANCE', 'Finance team with ledger and settlement access'),
('PAYIN_PARTNER', 'Pay-in partner who collects funds');

-- Add new permissions
INSERT IGNORE INTO permissions (code, description) VALUES
('ledger:view', 'View platform and partner ledgers'),
('settlement:manage', 'Manage settlements'),
('settlement:approve', 'Approve settlements'),
('config:manage_system', 'Manage system configuration'),
('config:manage_templates', 'Manage email templates'),
('config:manage_transfer', 'Manage transfer configuration'),
('partner:manage_payin', 'Manage pay-in partners'),
('partner:manage_payout', 'Manage payout partners'),
('partner:view_ledger', 'View partner ledger');

-- SUPPORT permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPPORT' AND p.code IN ('user:view', 'transaction:view_all', 'compliance:view_alerts');

-- FINANCE permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'FINANCE' AND p.code IN ('user:view', 'transaction:view_all', 'ledger:view', 'settlement:manage', 'report:view_financial');

-- PAYIN_PARTNER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PAYIN_PARTNER' AND p.code IN ('transaction:view', 'partner:view_ledger');

-- Add new permissions to ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.code IN ('ledger:view', 'settlement:manage', 'settlement:approve', 'partner:manage_payin', 'partner:manage_payout', 'partner:view_ledger');

-- SUPER_ADMIN gets all new permissions automatically (already has all via wildcard insert)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN' AND p.id NOT IN (SELECT permission_id FROM role_permissions WHERE role_id = r.id);

-- Update default admin email_verified
UPDATE users SET email_verified = TRUE WHERE email = 'admin@forexbridge.com';

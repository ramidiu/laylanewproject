CREATE TABLE IF NOT EXISTS sanctions_lists (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    list_name ENUM('OFAC','EU','UN','HMT') NOT NULL,
    entry_name VARCHAR(500) NOT NULL,
    entry_type ENUM('INDIVIDUAL','ENTITY') NOT NULL,
    aliases JSON,
    country VARCHAR(3),
    date_of_birth DATE,
    additional_info JSON,
    last_updated TIMESTAMP NOT NULL,
    INDEX idx_sanctions_list_name (list_name),
    INDEX idx_sanctions_entry_name (entry_name),
    INDEX idx_sanctions_country (country)
);

CREATE TABLE IF NOT EXISTS screening_results (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type ENUM('CUSTOMER','BENEFICIARY','TRANSACTION') NOT NULL,
    entity_id BIGINT NOT NULL,
    screened_name VARCHAR(500) NOT NULL,
    matched_list ENUM('OFAC','EU','UN','HMT'),
    matched_entry_id BIGINT,
    match_score DECIMAL(5,2),
    status ENUM('CLEAR','POTENTIAL_MATCH','CONFIRMED_MATCH','FALSE_POSITIVE') NOT NULL,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP NULL,
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_screening_entity (entity_type, entity_id),
    INDEX idx_screening_status (status)
);

CREATE TABLE IF NOT EXISTS monitoring_rules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name VARCHAR(200) NOT NULL,
    rule_type ENUM('VELOCITY','AMOUNT_THRESHOLD','PATTERN','CORRIDOR_RISK','STRUCTURING') NOT NULL,
    parameters JSON NOT NULL,
    severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_rules_type (rule_type),
    INDEX idx_rules_active (is_active)
);

CREATE TABLE IF NOT EXISTS compliance_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id BIGINT,
    user_id BIGINT NOT NULL,
    transaction_id BIGINT,
    severity ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    status ENUM('OPEN','UNDER_REVIEW','ESCALATED','CLOSED_NO_ACTION','CLOSED_SAR_FILED','CLOSED_FALSE_POSITIVE') NOT NULL DEFAULT 'OPEN',
    description TEXT,
    details JSON,
    assigned_to BIGINT,
    resolved_by BIGINT,
    resolved_at TIMESTAMP NULL,
    resolution_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (rule_id) REFERENCES monitoring_rules(id),
    INDEX idx_alerts_user (user_id),
    INDEX idx_alerts_status (status),
    INDEX idx_alerts_severity (severity),
    INDEX idx_alerts_transaction (transaction_id)
);

CREATE TABLE IF NOT EXISTS compliance_cases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_reference VARCHAR(50) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    status ENUM('OPEN','INVESTIGATING','ESCALATED','CLOSED') NOT NULL DEFAULT 'OPEN',
    priority ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    assigned_to BIGINT,
    summary TEXT,
    findings TEXT,
    outcome TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    closed_at TIMESTAMP NULL,
    INDEX idx_cases_reference (case_reference),
    INDEX idx_cases_user (user_id),
    INDEX idx_cases_status (status)
);

CREATE TABLE IF NOT EXISTS sar_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    report_type ENUM('SAR','STR','CTR') NOT NULL,
    filing_status ENUM('DRAFT','SUBMITTED','ACKNOWLEDGED') NOT NULL DEFAULT 'DRAFT',
    report_content TEXT,
    filed_by BIGINT,
    filed_at TIMESTAMP NULL,
    acknowledged_at TIMESTAMP NULL,
    external_reference VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (case_id) REFERENCES compliance_cases(id),
    INDEX idx_sar_case (case_id),
    INDEX idx_sar_status (filing_status)
);

CREATE TABLE IF NOT EXISTS risk_scores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type ENUM('CUSTOMER','BENEFICIARY','TRANSACTION') NOT NULL,
    entity_id BIGINT NOT NULL,
    score INT NOT NULL,
    risk_level ENUM('LOW','MEDIUM','HIGH','CRITICAL') NOT NULL,
    factors JSON,
    calculated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_risk_entity (entity_type, entity_id),
    INDEX idx_risk_level (risk_level)
);

CREATE TABLE IF NOT EXISTS audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_name VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(200) NOT NULL,
    actor_id BIGINT,
    actor_role VARCHAR(50),
    old_value JSON,
    new_value JSON,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_entity (entity_type, entity_id),
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_action (action),
    INDEX idx_audit_created (created_at)
);

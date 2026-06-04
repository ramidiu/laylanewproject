-- Phase 4: extra transaction-monitoring rule types + CTR threshold reports.

ALTER TABLE monitoring_rules
    MODIFY rule_type ENUM(
        'VELOCITY','AMOUNT_THRESHOLD','PATTERN','CORRIDOR_RISK','STRUCTURING',
        'BASELINE_ANOMALY','ROUND_NUMBER','RAPID_SUCCESSION'
    ) NOT NULL;

INSERT INTO monitoring_rules (rule_name, rule_type, parameters, severity, is_active) VALUES
('Baseline Anomaly (5x rolling 30d avg)', 'BASELINE_ANOMALY',
 '{"windowDays":30,"multiplier":5,"minBaselineCount":3}', 'HIGH', TRUE),
('Round-Number Send (multiples of 1000)', 'ROUND_NUMBER',
 '{"denominations":[1000,5000,10000],"minAmount":1000}', 'LOW', TRUE),
('Rapid Succession (two sends within 60s)', 'RAPID_SUCCESSION',
 '{"windowSeconds":60}', 'MEDIUM', TRUE);

CREATE TABLE IF NOT EXISTS ctr_reports (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_date DATE NOT NULL,
    user_id BIGINT NOT NULL,
    user_email VARCHAR(255),
    user_name VARCHAR(255),
    transaction_count INT NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    threshold DECIMAL(18,2) NOT NULL,
    transaction_refs JSON,
    filing_status ENUM('DRAFT','SUBMITTED','ACKNOWLEDGED') NOT NULL DEFAULT 'DRAFT',
    filed_by BIGINT,
    filed_at TIMESTAMP NULL,
    acknowledged_at TIMESTAMP NULL,
    external_reference VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_ctr_date_user (report_date, user_id),
    INDEX idx_ctr_filing_status (filing_status),
    INDEX idx_ctr_date (report_date)
);

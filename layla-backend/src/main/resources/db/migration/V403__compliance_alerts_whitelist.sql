-- Phase 3: dedupe index on alerts + whitelist for false-positive clears.

ALTER TABLE compliance_alerts
    ADD COLUMN list_entry_id BIGINT NULL,
    ADD INDEX idx_alerts_list_entry (list_entry_id),
    ADD INDEX idx_alerts_user_list_status (user_id, list_entry_id, status);

CREATE TABLE IF NOT EXISTS compliance_whitelist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    subject_type ENUM('CUSTOMER','BENEFICIARY') NOT NULL,
    subject_id BIGINT NOT NULL,
    list_entry_id BIGINT NOT NULL,
    whitelisted_by_user_id BIGINT NOT NULL,
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE INDEX idx_wl_unique (subject_type, subject_id, list_entry_id),
    INDEX idx_wl_list_entry (list_entry_id)
);

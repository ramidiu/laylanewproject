-- Code added by Naresh: System Controls Phase 3 — immutable audit trail for
-- system_config writes. Populated from SystemConfigService.updateValue() only on
-- successful updates; validation / allowed_values / version-conflict failures
-- never reach this table.

CREATE TABLE IF NOT EXISTS system_config_audit (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    config_key     VARCHAR(100) NOT NULL,
    old_value      VARCHAR(500) NULL,
    new_value      VARCHAR(500) NULL,
    old_version    INT          NULL,
    new_version    INT          NULL,
    changed_by     VARCHAR(255) NOT NULL,
    changed_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    change_source  VARCHAR(50)  NOT NULL DEFAULT 'API',
    PRIMARY KEY (id),
    KEY idx_system_config_audit_key (config_key),
    KEY idx_system_config_audit_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

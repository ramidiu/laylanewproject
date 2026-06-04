-- =============================================================================
-- STEP 1 — Create migration infrastructure tables in remitz database.
--           These are DDL statements (auto-committed, cannot be rolled back).
--           Run ONCE before the migration procedure.
--
-- Execute inside container:
--   docker exec -i layla-mysql mysql -uroot -proot remitz < 01_infrastructure.sql
-- =============================================================================

USE remitz;

SET SESSION sql_mode = '';

-- ---------------------------------------------------------------------------
-- 1A. Recreate remit_one table (identical to old schema)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `remit_one` (
    `id`                        BIGINT(20)      NOT NULL AUTO_INCREMENT,
    `transaction_id`            VARCHAR(100)    DEFAULT NULL,
    `trans_session_id`          VARCHAR(100)    DEFAULT NULL,
    `trans_type`                VARCHAR(50)     DEFAULT NULL,
    `remitter_id`               VARCHAR(100)    DEFAULT NULL,
    `remitter_name`             VARCHAR(200)    DEFAULT NULL,
    `beneficiary_id`            VARCHAR(100)    DEFAULT NULL,
    `beneficiary_name`          VARCHAR(200)    DEFAULT NULL,
    `destination_country`       VARCHAR(5)      DEFAULT NULL,
    `source_currency`           VARCHAR(5)      DEFAULT NULL,
    `source_amount`             DECIMAL(18,2)   DEFAULT NULL,
    `rate`                      DECIMAL(18,6)   DEFAULT NULL,
    `destination_currency`      VARCHAR(5)      DEFAULT NULL,
    `destination_amount`        DECIMAL(18,2)   DEFAULT NULL,
    `commission`                DECIMAL(18,2)   DEFAULT NULL,
    `tax`                       DECIMAL(18,2)   DEFAULT NULL,
    `remitter_pay_amount`       DECIMAL(18,2)   DEFAULT NULL,
    `comments_to_beneficiary`   TEXT,
    `payment_status`            VARCHAR(50)     DEFAULT NULL,
    `created_on`                TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `MESSAGE`                   VARCHAR(255)    DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_transaction_id` (`transaction_id`),
    KEY `idx_payment_status` (`payment_status`),
    KEY `idx_created_on` (`created_on`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Migrated from layla_old.remit_one — RemitOne API integration log';

-- ---------------------------------------------------------------------------
-- 1B. User mapping table (old VARCHAR USER_ID → new bigint users.id)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `migration_user_map` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `old_user_id`   VARCHAR(255)    NOT NULL,
    `new_user_id`   BIGINT          NOT NULL,
    `old_email`     VARCHAR(255)    DEFAULT NULL,
    `migrated_at`   TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uq_old_user_id` (`old_user_id`),
    UNIQUE KEY `uq_new_user_id` (`new_user_id`),
    KEY `idx_old` (`old_user_id`),
    KEY `idx_new` (`new_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Maps old VARCHAR USER_ID to new bigint users.id';

-- ---------------------------------------------------------------------------
-- 1C. Beneficiary mapping table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `migration_beneficiary_map` (
    `old_beneficiary_id`    BIGINT  NOT NULL,
    `new_beneficiary_id`    BIGINT  NOT NULL,
    PRIMARY KEY (`old_beneficiary_id`),
    KEY `idx_new` (`new_beneficiary_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Maps old beneficiary.BENEFICIARY_ID to new beneficiaries.id';

-- ---------------------------------------------------------------------------
-- 1D. Migration log table (persists across runs, not rolled back)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `migration_log` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT,
    `phase`         VARCHAR(50)     DEFAULT NULL,
    `table_name`    VARCHAR(100)    DEFAULT NULL,
    `record_id`     VARCHAR(255)    DEFAULT NULL,
    `status`        ENUM('OK','SKIPPED','FAILED','INFO') NOT NULL DEFAULT 'INFO',
    `message`       TEXT            DEFAULT NULL,
    `logged_at`     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_phase` (`phase`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Persistent migration log — not rolled back in dry-run';

-- ---------------------------------------------------------------------------
-- 1E. Migration summary table
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `migration_summary` (
    `phase`         VARCHAR(50)     NOT NULL,
    `attempted`     INT             NOT NULL DEFAULT 0,
    `succeeded`     INT             NOT NULL DEFAULT 0,
    `skipped`       INT             NOT NULL DEFAULT 0,
    `failed`        INT             NOT NULL DEFAULT 0,
    `run_at`        TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`phase`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Per-phase migration summary';

-- ---------------------------------------------------------------------------
-- Confirmation
-- ---------------------------------------------------------------------------
SELECT 'Infrastructure tables created successfully.' AS status;

SELECT
    table_name          AS `Table`,
    table_comment       AS `Purpose`
FROM information_schema.TABLES
WHERE table_schema = 'remitz'
  AND table_name IN ('remit_one','migration_user_map','migration_beneficiary_map',
                     'migration_log','migration_summary')
ORDER BY table_name;

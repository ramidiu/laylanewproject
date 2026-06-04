-- Code added by Naresh: System Controls Phase 1 — foundation columns on system_config.
-- This migration is strictly additive. No existing data is modified destructively,
-- no columns are dropped, no indexes are rebuilt. Safe to run against a live table.
-- Follow-up phases (service layer, audit history, typed-getter callsites) add behavior;
-- this phase only reshapes the schema and backfills sensible defaults.

-- 1. Add new columns. Flyway guarantees single-run; MySQL 8 rejects IF NOT EXISTS on
--    ADD COLUMN, so we rely on Flyway's schema_history for idempotency.
ALTER TABLE system_config
    ADD COLUMN value_type      VARCHAR(20)  NOT NULL DEFAULT 'STRING'  AFTER config_value,
    ADD COLUMN category        VARCHAR(50)  NOT NULL DEFAULT 'general' AFTER value_type,
    ADD COLUMN allowed_values  TEXT         NULL                        AFTER category,
    ADD COLUMN version         INT          NOT NULL DEFAULT 1          AFTER updated_at;

-- 2. Backfill metadata for the 5 keys seeded by V103.
--    Each row gets a correct value_type + category; allowed_values stays NULL for free-form;
--    version stays 1 — Phase 2 will start bumping it via optimistic locking.
UPDATE system_config SET value_type = 'BOOLEAN', category = 'kyc'
  WHERE config_key = 'ALLOW_PARTIAL_KYC_TRANSACTIONS';

UPDATE system_config SET value_type = 'INT', category = 'security'
  WHERE config_key IN (
      'MAX_LOGIN_ATTEMPTS',
      'LOCKOUT_DURATION_MINUTES',
      'OTP_TTL_MINUTES'
  );

UPDATE system_config SET value_type = 'INT', category = 'fx'
  WHERE config_key = 'RATE_LOCK_TTL_SECONDS';

-- 3. Any other rows inserted before this migration keep value_type='STRING',
--    category='general', version=1 from the column defaults above.

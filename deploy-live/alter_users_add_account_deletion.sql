-- PROD DELTA — Account Deletion feature (Google Play Account Deletion policy).
-- Mirrors Flyway V543__add_account_deletion_support.sql. Deploying the new
-- backend jar runs the migration automatically; run this only if applying the
-- schema change manually ahead of the deploy.
--
--   docker exec -i layla-mysql-prod mysql -uroot -p<pw> remitz < alter_users_add_account_deletion.sql
--
ALTER TABLE users
    ADD COLUMN account_status ENUM('ACTIVE','DELETE_REQUESTED','DELETED')
        NOT NULL DEFAULT 'ACTIVE' AFTER status,
    ADD COLUMN delete_requested_at DATETIME NULL AFTER account_status,
    ADD COLUMN deleted_at          DATETIME NULL AFTER delete_requested_at,
    ADD COLUMN delete_reason       VARCHAR(1000) NULL AFTER deleted_at,
    ADD COLUMN deleted_by          VARCHAR(255) NULL AFTER delete_reason;

UPDATE users SET account_status = 'ACTIVE' WHERE account_status IS NULL;

CREATE INDEX idx_users_account_status ON users (account_status);

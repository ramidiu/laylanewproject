-- Account Deletion support (Google Play Account Deletion policy compliance).
-- Adds soft-delete tracking columns to the users table. Records are NOT hard
-- deleted on request; the account is marked DELETE_REQUESTED, access is disabled,
-- and financial/KYC records are retained for the legally required period.
ALTER TABLE users
    ADD COLUMN account_status ENUM('ACTIVE','DELETE_REQUESTED','DELETED')
        NOT NULL DEFAULT 'ACTIVE' AFTER status,
    ADD COLUMN delete_requested_at DATETIME NULL AFTER account_status,
    ADD COLUMN deleted_at          DATETIME NULL AFTER delete_requested_at,
    ADD COLUMN delete_reason       VARCHAR(1000) NULL AFTER deleted_at,
    ADD COLUMN deleted_by          VARCHAR(255) NULL AFTER delete_reason;

-- Existing accounts are all active.
UPDATE users SET account_status = 'ACTIVE' WHERE account_status IS NULL;

-- Index so the admin "archived / deletion-requested accounts" list filters quickly.
CREATE INDEX idx_users_account_status ON users (account_status);

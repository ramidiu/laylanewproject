-- PROD DELTA — run once against the live remitz DB.
-- Fixes: stale PENDING transactions never archived because the status ENUM
-- column rejected 'ARCHIVED' ("Data truncated for column 'status'"), rolling
-- back the @Transactional archive batch. After this ALTER the
-- PendingTransactionArchiveScheduler (runs every 15 min) will archive all
-- PENDING transactions older than 3 hours.
--
--   docker exec -i layla-mysql-prod mysql -uroot -p<pw> remitz < alter_transactions_add_archived_status.sql
--
ALTER TABLE transactions
  MODIFY COLUMN status ENUM(
    'CREATED',
    'PENDING',
    'COMPLIANCE_HOLD',
    'PROCESSING',
    'FUNDS_RECEIVED',
    'SENT_TO_PAYOUT',
    'PAID',
    'FAILED',
    'CANCELLED',
    'REFUNDED',
    'COMPLETED',
    'ARCHIVED'
  ) NOT NULL;

-- Add ARCHIVED to the transactions.status ENUM so the stale-PENDING archive
-- scheduler can persist the status change. Without ARCHIVED in the column
-- definition, MySQL throws "Data truncated for column 'status'" and the whole
-- @Transactional batch rolls back (nothing gets archived).
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

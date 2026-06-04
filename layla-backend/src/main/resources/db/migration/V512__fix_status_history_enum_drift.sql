-- Flyway migration: reconcile all known enum drift between Java enums and MySQL
-- ENUM columns. Each Java enum listed below had at least one value that the
-- corresponding MySQL column could not accept, producing "Data truncated ..."
-- errors at runtime.
--
-- Fixes included in this migration
--   1. TransactionStatus.COMPLETED   → transactions.status,
--                                      transaction_status_history.from_status,
--                                      transaction_status_history.to_status
--   2. ActorType.PAYIN_PARTNER       → transaction_status_history.actor_type
--   3. DeliveryMethod.UPI            → beneficiaries.delivery_method,
--                                      corridor_delivery_methods.delivery_method,
--                                      corridor_fees.delivery_method,
--                                      fx_margins.delivery_method,
--                                      recurring_transfers.delivery_method,
--                                      transactions.delivery_method
--   4. UserStatus.PENDING_VERIFICATION → users.status
--   5. RiskLevel.CRITICAL            → corridors.risk_level
--
-- Safe to re-run: MODIFY COLUMN reapplies the same ENUM definition idempotently.

-- (1) TransactionStatus on transactions + transaction_status_history
ALTER TABLE transactions
  MODIFY COLUMN status
  ENUM('CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED','COMPLETED') NOT NULL;

ALTER TABLE transaction_status_history
  MODIFY COLUMN from_status
  ENUM('CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED','COMPLETED');

ALTER TABLE transaction_status_history
  MODIFY COLUMN to_status
  ENUM('CREATED','PENDING','COMPLIANCE_HOLD','PROCESSING','FUNDS_RECEIVED','SENT_TO_PAYOUT','PAID','FAILED','CANCELLED','REFUNDED','COMPLETED') NOT NULL;

-- (2) ActorType on transaction_status_history
ALTER TABLE transaction_status_history
  MODIFY COLUMN actor_type
  ENUM('SYSTEM','USER','ADMIN','COMPLIANCE','PAYOUT_PARTNER','PAYIN_PARTNER','AGENT') NOT NULL DEFAULT 'SYSTEM';

-- (3) DeliveryMethod — add UPI across every table that stores it
ALTER TABLE beneficiaries
  MODIFY COLUMN delivery_method
  ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP','UPI');

ALTER TABLE corridor_delivery_methods
  MODIFY COLUMN delivery_method
  ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP','UPI');

ALTER TABLE corridor_fees
  MODIFY COLUMN delivery_method
  ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP','UPI');

ALTER TABLE fx_margins
  MODIFY COLUMN delivery_method
  ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP','UPI');

ALTER TABLE recurring_transfers
  MODIFY COLUMN delivery_method
  ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP','UPI');

ALTER TABLE transactions
  MODIFY COLUMN delivery_method
  ENUM('BANK_DEPOSIT','MOBILE_WALLET','CASH_PICKUP','HOME_DELIVERY','AIRTIME_TOPUP','UPI') NOT NULL;

-- (4) UserStatus — add PENDING_VERIFICATION
ALTER TABLE users
  MODIFY COLUMN status
  ENUM('ACTIVE','PENDING_VERIFICATION','SUSPENDED','LOCKED','CLOSED') NOT NULL DEFAULT 'ACTIVE';

-- (5) RiskLevel — add CRITICAL on corridors
ALTER TABLE corridors
  MODIFY COLUMN risk_level
  ENUM('LOW','MEDIUM','HIGH','CRITICAL');

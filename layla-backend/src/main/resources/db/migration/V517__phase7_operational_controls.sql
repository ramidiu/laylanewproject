-- Code added by Naresh: System Controls Phase 7 — operations, maintenance and
-- compliance master-switch catalog plus optional audit "reason" capture.

-- 1. Optional reason column on the audit trail so dangerous mutations can record
--    why an operator flipped a critical switch. Nullable; older callers ignore it.
ALTER TABLE system_config_audit
    ADD COLUMN reason VARCHAR(500) NULL AFTER change_source;

-- 2. Seed the new runtime control catalog. INSERT IGNORE preserves any rows a
--    previous environment may already hold.

-- ─── operations ────────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('registration.enabled', 'true', 'BOOLEAN', 'operations', NULL, 1,
   'Accept new customer / partner self-registration. When off, existing users can still log in.'),
  ('transactions.enabled', 'true', 'BOOLEAN', 'operations', NULL, 1,
   'Allow customers to create new transactions. When off, transaction history stays readable.'),
  ('beneficiary.enabled', 'true', 'BOOLEAN', 'operations', NULL, 1,
   'Allow customers to add / update beneficiaries. When off, existing beneficiaries stay readable.'),
  ('payin.enabled', 'true', 'BOOLEAN', 'operations', NULL, 1,
   'Allow payin-partner receive-funds / reject / release-compliance actions.'),
  ('payout.enabled', 'true', 'BOOLEAN', 'operations', NULL, 1,
   'Allow payout-partner completion actions.');

-- ─── maintenance ───────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('maintenance.mode.enabled', 'false', 'BOOLEAN', 'maintenance', NULL, 1,
   'Block customer-facing write actions. Admin panel, auth endpoints and /actuator/health stay open.'),
  ('maintenance.message', 'System maintenance is currently in progress. Please try again later.',
   'STRING', 'maintenance', NULL, 1,
   'Message returned to customers while maintenance.mode.enabled is true.');

-- ─── compliance master switches ────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('compliance.enabled', 'true', 'BOOLEAN', 'compliance', NULL, 1,
   'Master switch for ALL compliance (sanctions / PEP / screening) runtime checks. Off = bypass.'),
  ('compliance.transaction_screening.enabled', 'true', 'BOOLEAN', 'compliance', NULL, 1,
   'Transaction-level sender + beneficiary sanctions / PEP screening. Off = skip, KYC review still in effect.'),
  ('compliance.skip_recheck_for_approved_customers', 'true', 'BOOLEAN', 'compliance', NULL, 1,
   'Skip re-screening of senders / beneficiaries already cleared on a prior transaction.'),
  ('compliance.vendor_fallback.allow_transactions', 'false', 'BOOLEAN', 'compliance', NULL, 1,
   'When the compliance vendor call fails, allow the transaction to proceed (with a warning) instead of placing it on COMPLIANCE_HOLD.');

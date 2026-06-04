-- Code added by Naresh: System Controls Phase 4 — seed the runtime control catalog.
-- Every statement uses INSERT IGNORE so existing rows are preserved unchanged.
-- config_key has a UNIQUE constraint, so INSERT IGNORE silently skips duplicates;
-- no UPDATE paths are used, guaranteeing zero-risk replay on existing databases.
--
-- TODO (future cleanup): the legacy UPPER_SNAKE_CASE keys seeded by V103 —
--   MAX_LOGIN_ATTEMPTS, LOCKOUT_DURATION_MINUTES, RATE_LOCK_TTL_SECONDS — are
--   functionally duplicated by the new dot-notation keys added below
--   (security.max_login_attempts, security.lockout_duration_minutes,
--   fx.quote_ttl_seconds). Callsite migration (a later phase) will pick one
--   canonical name per control and drop the legacy row; this phase keeps both
--   so existing code that reads the old keys keeps working.

-- ─── COMPLIANCE ──────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('compliance.screening_threshold', '85', 'INT', 'compliance', NULL, 1,
   'Minimum sanctions/PEP match score (0-100) that triggers a compliance alert.'),
  ('compliance.auto_hold_amount', '10000.00', 'DECIMAL', 'compliance', NULL, 1,
   'Transaction amount (USD equivalent) at or above which the system auto-holds for review.');

-- ─── KYC ─────────────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('kyc.tier1_daily_limit', '2500.00', 'DECIMAL', 'kyc', NULL, 1,
   'Daily send limit for Tier-1 KYC customers (USD equivalent).'),
  ('kyc.tier2_daily_limit', '25000.00', 'DECIMAL', 'kyc', NULL, 1,
   'Daily send limit for Tier-2 KYC customers (USD equivalent).'),
  ('kyc.require_selfie', 'true', 'BOOLEAN', 'kyc', NULL, 1,
   'Whether a live selfie is required during KYC document submission.');

-- ─── TRANSACTION ─────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('transaction.min_send_amount', '10.00', 'DECIMAL', 'transaction', NULL, 1,
   'Minimum send amount accepted when creating a new transaction (USD equivalent).'),
  ('transaction.max_send_amount', '50000.00', 'DECIMAL', 'transaction', NULL, 1,
   'Maximum send amount accepted when creating a new transaction (USD equivalent).'),
  ('transaction.duplicate_submit_window_seconds', '60', 'INT', 'transaction', NULL, 1,
   'Time window within which identical submits from the same customer are treated as duplicates.');

-- ─── ROUTING ─────────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('routing.payin.kuber.enabled', 'true', 'BOOLEAN', 'routing', NULL, 1,
   'Expose Kuber Financial PayTo as a pay-in option to customers.'),
  ('routing.payout.partner_default', '', 'STRING', 'routing', NULL, 1,
   'Default payout partner key used when a corridor has no explicit partner binding. Leave blank to require explicit routing.'),
  ('routing.admin_fallback.enabled', 'false', 'BOOLEAN', 'routing', NULL, 1,
   'Allow admin to manually override routing decisions at checkout.');

-- ─── NOTIFICATIONS ───────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('notifications.email.enabled', 'true', 'BOOLEAN', 'notifications', NULL, 1,
   'Master switch for outbound email notifications.'),
  ('notifications.sms.enabled', 'false', 'BOOLEAN', 'notifications', NULL, 1,
   'Master switch for outbound SMS notifications.'),
  ('notifications.transaction_status.enabled', 'true', 'BOOLEAN', 'notifications', NULL, 1,
   'Send transaction-status update notifications to customers on state changes.');

-- ─── SECURITY ────────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('security.mfa.enforce_staff', 'false', 'BOOLEAN', 'security', NULL, 1,
   'Force MFA challenge on every staff login. Off locally, expected to be ON in production.'),
  ('security.ip_allowlist.enabled', 'false', 'BOOLEAN', 'security', NULL, 1,
   'Enforce the admin IP allowlist at the gateway. Off locally.'),
  ('security.max_login_attempts', '5', 'INT', 'security', NULL, 1,
   'Failed logins before an account is temporarily locked (dot-notation equivalent of legacy MAX_LOGIN_ATTEMPTS).'),
  ('security.lockout_duration_minutes', '30', 'INT', 'security', NULL, 1,
   'Minutes an account stays locked after MAX_LOGIN_ATTEMPTS is reached.');

-- ─── FX ──────────────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('fx.margin_default_bps', '50', 'INT', 'fx', NULL, 1,
   'Default FX margin added to inter-bank rate, in basis points (100 = 1%).'),
  ('fx.quote_ttl_seconds', '60', 'INT', 'fx', NULL, 1,
   'Seconds a locked FX quote stays valid before the customer must re-quote.'),
  ('fx.rate_freeze.enabled', 'false', 'BOOLEAN', 'fx', NULL, 1,
   'Emergency switch: freeze all FX rates at the last known values (no upstream pulls).');

-- ─── JOBS ────────────────────────────────────────────────────
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('jobs.opensanctions_refresh.enabled', 'true', 'BOOLEAN', 'jobs', NULL, 1,
   'Enable the nightly OpenSanctions ingestion job.'),
  ('jobs.notification_retry.enabled', 'true', 'BOOLEAN', 'jobs', NULL, 1,
   'Enable the background retry job for failed email/SMS notifications.');

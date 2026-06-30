-- ============================================================================
-- V548: Make TIER_1 the base KYC tier; retire TIER_0
-- ----------------------------------------------------------------------------
-- New tier model:
--   TIER_1 = base, zero documents (every user starts here)
--   TIER_2 = identity + proof of address approved (verified)
--   TIER_3 = + source of funds
--
-- TIER_0 is no longer assigned by application code. Migrate every existing TIER_0
-- (or NULL) user to the new base TIER_1, and make TIER_1 the column default. The
-- TIER_0 enum value is kept in the column definition for backward compatibility
-- with historic audit rows; it is simply never used going forward.
-- ============================================================================

UPDATE users SET kyc_tier = 'TIER_1' WHERE kyc_tier = 'TIER_0' OR kyc_tier IS NULL;

ALTER TABLE users ALTER COLUMN kyc_tier SET DEFAULT 'TIER_1';

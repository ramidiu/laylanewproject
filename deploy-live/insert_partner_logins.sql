-- ============================================================================
-- Insert PAY-IN / PAY-OUT partner LOGINS created in local into production.
-- Idempotent & email-keyed: existing accounts are left untouched (no password
-- overwrite), missing ones are inserted with auto-increment IDs (no ID clashes).
-- Does NOT touch customers or transactions.
-- Apply on the server:  mysql -uroot -p remitz < insert_partner_logins.sql
-- ============================================================================

-- ── 1. Partner user accounts (skip if the email already exists) ──────────────

INSERT INTO users
  (uuid, email, phone, password_hash, first_name, last_name, risk_score, risk_points,
   user_type, kyc_tier, status, country, country_of_residence, country_code,
   preferred_language, mfa_enabled, email_verified, payin_enabled, created_at, updated_at)
SELECT 'a99b5a02-7189-4e51-9334-8e2170a311e4','uk@gmail.com','9876543210',
       '$2a$10$N4XNbqNj6TUF2cwvpATtJu7m4RpgcSZMgJfKBRSjiBf6Zmbo2gbCq','UK','Partner',
       'MEDIUM',0,'INDIVIDUAL','TIER_0','ACTIVE','GBR','GB','GB','en',0,1,0,NOW(),NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='uk@gmail.com');

INSERT INTO users
  (uuid, email, phone, password_hash, first_name, last_name, risk_score, risk_points,
   user_type, kyc_tier, status, country, country_of_residence, country_code,
   preferred_language, mfa_enabled, email_verified, payin_enabled, created_at, updated_at)
SELECT '0b36bca8-d69f-4725-9ada-5152bda7d684','sudan@gmail.com','9876543210',
       '$2a$10$VRBwssc09llYgsnIRlokfOxw8xC.S5gXAAyu6aN0oPTbKScvehltK','sudan','Partner',
       'MEDIUM',0,'INDIVIDUAL','TIER_0','ACTIVE','GBR','GB','GB','en',0,1,0,NOW(),NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='sudan@gmail.com');

INSERT INTO users
  (uuid, email, phone, password_hash, first_name, last_name, risk_score, risk_points,
   user_type, kyc_tier, status, preferred_language, mfa_enabled, email_verified, payin_enabled, created_at, updated_at)
SELECT '83311176-40d9-4014-abc9-e960e7c46c7f','uk123@gmail.com','9876543210',
       '$2a$10$WObwzrB0nDfUN89m1WEYP.jSHmqbwjO/BlcaOkH7E/LzpmVPYkBTK','UnitedKingdom','Partner',
       'MEDIUM',0,'INDIVIDUAL','TIER_0','ACTIVE','en',0,1,0,NOW(),NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM users WHERE email='uk123@gmail.com');

-- ── 2. Role assignments (PAYIN_PARTNER / PAYOUT_PARTNER) ─────────────────────

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM users WHERE email='uk@gmail.com'), (SELECT id FROM roles WHERE name='PAYIN_PARTNER')
FROM dual WHERE NOT EXISTS (
  SELECT 1 FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email='uk@gmail.com')
    AND role_id=(SELECT id FROM roles WHERE name='PAYIN_PARTNER'));

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM users WHERE email='sudan@gmail.com'), (SELECT id FROM roles WHERE name='PAYOUT_PARTNER')
FROM dual WHERE NOT EXISTS (
  SELECT 1 FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email='sudan@gmail.com')
    AND role_id=(SELECT id FROM roles WHERE name='PAYOUT_PARTNER'));

INSERT INTO user_roles (user_id, role_id)
SELECT (SELECT id FROM users WHERE email='uk123@gmail.com'), (SELECT id FROM roles WHERE name='PAYOUT_PARTNER')
FROM dual WHERE NOT EXISTS (
  SELECT 1 FROM user_roles WHERE user_id=(SELECT id FROM users WHERE email='uk123@gmail.com')
    AND role_id=(SELECT id FROM roles WHERE name='PAYOUT_PARTNER'));

-- ── 3. Partner org records (link user_id by email; skip if email already there) ─

INSERT INTO payin_partners (partner_name, user_id, contact_email, contact_phone, is_active, created_at, updated_at)
SELECT 'UK', (SELECT id FROM users WHERE email='uk@gmail.com'), 'uk@gmail.com', '9876543210', 1, NOW(), NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM payin_partners WHERE contact_email='uk@gmail.com');

INSERT INTO payin_partners (partner_name, user_id, contact_email, contact_phone, is_active, created_at, updated_at)
SELECT 'sudan', NULL, 'sudan@gmail.com', '9876543210', 1, NOW(), NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM payin_partners WHERE contact_email='sudan@gmail.com');

INSERT INTO payout_partners (partner_name, user_id, contact_email, contact_phone, is_active, created_at, updated_at)
SELECT 'sudan', (SELECT id FROM users WHERE email='sudan@gmail.com'), 'sudan@gmail.com', '9876543210', 1, NOW(), NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM payout_partners WHERE contact_email='sudan@gmail.com');

INSERT INTO payout_partners (partner_name, user_id, contact_email, contact_phone, is_active, created_at, updated_at)
SELECT 'UnitedKingdom', (SELECT id FROM users WHERE email='uk123@gmail.com'), 'uk123@gmail.com', '9876543210', 1, NOW(), NOW()
FROM dual WHERE NOT EXISTS (SELECT 1 FROM payout_partners WHERE contact_email='uk123@gmail.com');

-- Verify
SELECT 'payin_partners' tbl, partner_name, contact_email, user_id FROM payin_partners
UNION ALL
SELECT 'payout_partners', partner_name, contact_email, user_id FROM payout_partners;

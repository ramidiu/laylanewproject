-- Seed default ADMIN user (password: Admin@123456)
INSERT IGNORE INTO users (uuid, email, phone, password_hash, first_name, last_name, user_type, kyc_tier, status, email_verified, mfa_enabled)
VALUES ('00000000-0000-0000-0000-000000000002', 'platformadmin@remitz.co.uk', '+44000000001',
        '$2b$10$919IbDvDpA1y.RArsaetZerLZsD/dt6R1mg/OHzVXtshgvhduKFa.',
        'Platform', 'Admin', 'INDIVIDUAL', 'TIER_3', 'ACTIVE', TRUE, FALSE);

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'platformadmin@remitz.co.uk' AND r.name = 'ADMIN';

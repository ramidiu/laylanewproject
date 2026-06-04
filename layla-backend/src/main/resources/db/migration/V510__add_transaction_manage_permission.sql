-- Adds the `transaction:manage` permission required by the Amal Express
-- admin process endpoint (POST /api/amal/process/{id}).
-- This permission was manually seeded during the 2026-04 Amal integration
-- testing session; this migration makes it permanent for all environments.

INSERT IGNORE INTO permissions (code, description) VALUES
    ('transaction:manage', 'Trigger third-party transaction processing (e.g. Amal Express dispatch)');

-- Grant to ADMIN and SUPER_ADMIN roles.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code = 'transaction:manage';

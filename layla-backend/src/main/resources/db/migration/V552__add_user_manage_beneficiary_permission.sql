-- Permission for admins to edit a user's beneficiaries on their behalf (admin recipient edit).
-- Granted to ADMIN and SUPER_ADMIN, mirroring user:suspend. ADMIN also auto-receives it via the
-- V6 cross-join, but we grant explicitly so SUPER_ADMIN and any future ADMIN-only seed stays correct.
INSERT IGNORE INTO permissions (code, description) VALUES
    ('user:manage_beneficiary', 'Edit beneficiaries (recipients) on behalf of users');

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ADMIN', 'SUPER_ADMIN')
  AND p.code = 'user:manage_beneficiary';

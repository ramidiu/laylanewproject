-- Grant ADMIN every permission that SUPER_ADMIN has.
-- Previously ADMIN was missing: config:manage_system, compliance:file_sar,
-- compliance:override_screening, compliance:manage_cases, payout:upload_proof,
-- payin:*, agent:*, and several V5 additions.
-- This migration makes ADMIN a full-access role identical to SUPER_ADMIN.

INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
CROSS JOIN permissions p
WHERE r.name = 'ADMIN';

-- Closes gaps discovered during the 2026-04 codebase audit:
--   1. Eight permission codes are referenced in @PreAuthorize annotations
--      across the services but were never seeded in V2, so the endpoints
--      are effectively unreachable even for SUPER_ADMIN.
--   2. The PAYIN_PARTNER role is referenced in the Angular routing for the
--      pay-in partner portal but was missing from V2.
--
-- This migration is idempotent: it uses INSERT IGNORE so re-running against
-- a partially-seeded database is safe.

-- ─── New permissions ────────────────────────────────────────────────────────
INSERT IGNORE INTO permissions (code, description) VALUES
    ('config:manage_system',    'Manage platform-wide system configuration (compliance admin, notification admin, infra)'),
    ('config:manage_transfer',  'Manage transfer config and bank database entries'),
    ('ledger:view',             'View the double-entry ledger and financial journal'),
    ('settlement:manage',       'Manage settlements, partner settlement schedules and settlement rates'),
    ('partner:manage_payout',   'Manage payout partner accounts, credentials and routing'),
    ('partner:manage_payin',    'Manage pay-in partner accounts, credentials and routing'),
    ('compliance:manage_alerts','Update, assign and disposition compliance alerts'),
    ('compliance:manage_cases', 'Update compliance cases and SAR workflow'),
    ('payin:view_assigned',     'View pay-in deposits assigned to this partner'),
    ('payin:mark_received',     'Mark customer deposits as received'),
    ('payin:view_ledger',       'View the pay-in partner ledger and settlement history');

-- ─── New PAYIN_PARTNER role ─────────────────────────────────────────────────
INSERT IGNORE INTO roles (name, description) VALUES
    ('PAYIN_PARTNER', 'Pay-in partner that collects customer deposits and settles to the platform');

-- Grant PAYIN_PARTNER its own minimal permission set.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PAYIN_PARTNER'
  AND p.code IN ('payin:view_assigned', 'payin:mark_received', 'payin:view_ledger', 'transaction:view');

-- ─── Grant the missing permissions to existing roles ───────────────────────
-- ADMIN: gets operational access to ledger, settlement, transfer config, partner admin, and alert management.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN'
  AND p.code IN (
    'config:manage_transfer',
    'ledger:view',
    'settlement:manage',
    'partner:manage_payout',
    'partner:manage_payin',
    'compliance:manage_alerts'
  );

-- TREASURY_MANAGER: needs the ledger view and settlement management for reconciliation.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'TREASURY_MANAGER'
  AND p.code IN (
    'ledger:view',
    'settlement:manage',
    'report:view_operational'
  );

-- COMPLIANCE_OFFICER: gets full alert and case workflow capabilities.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPLIANCE_OFFICER'
  AND p.code IN (
    'compliance:manage_alerts',
    'compliance:manage_cases'
  );

-- SUPER_ADMIN: gets every newly created permission. Unlike V2 where the seed
-- was unfiltered (and therefore automatically captured every permission at that
-- time), we must explicitly enumerate here because the SUPER_ADMIN row-set
-- in V2 has already been applied on existing databases.
INSERT IGNORE INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN'
  AND p.code IN (
    'config:manage_system',
    'config:manage_transfer',
    'ledger:view',
    'settlement:manage',
    'partner:manage_payout',
    'partner:manage_payin',
    'compliance:manage_alerts',
    'compliance:manage_cases',
    'payin:view_assigned',
    'payin:mark_received',
    'payin:view_ledger'
  );

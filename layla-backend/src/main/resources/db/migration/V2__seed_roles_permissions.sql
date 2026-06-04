-- Roles
INSERT INTO roles (name, description) VALUES
('CUSTOMER', 'End customer who sends money'),
('AGENT', 'Agent who processes transactions on behalf of customers'),
('COMPLIANCE_OFFICER', 'Reviews compliance alerts and cases'),
('PAYOUT_PARTNER', 'Payout partner who processes payouts'),
('TREASURY_MANAGER', 'Manages FX rates and nostro accounts'),
('ADMIN', 'Platform administrator'),
('SUPER_ADMIN', 'Super administrator with full access');

-- Permissions
INSERT INTO permissions (code, description) VALUES
('transaction:create', 'Create new transactions'),
('transaction:view', 'View own transactions'),
('transaction:view_all', 'View all transactions'),
('transaction:cancel', 'Cancel transactions'),
('transaction:refund', 'Process refunds'),
('user:view', 'View user profiles'),
('user:edit', 'Edit user profiles'),
('user:suspend', 'Suspend user accounts'),
('user:approve_kyc', 'Approve KYC documents'),
('compliance:view_alerts', 'View compliance alerts'),
('compliance:file_sar', 'File suspicious activity reports'),
('compliance:override_screening', 'Override screening results'),
('fx:manage_rates', 'Manage FX rates'),
('fx:set_margins', 'Set FX margins'),
('fx:view_nostro', 'View nostro account balances'),
('payout:view_assigned', 'View assigned payouts'),
('payout:mark_paid', 'Mark payouts as paid'),
('payout:upload_proof', 'Upload proof of payment'),
('report:view_operational', 'View operational reports'),
('report:view_financial', 'View financial reports'),
('report:export', 'Export reports'),
('config:manage_corridors', 'Manage corridor configuration'),
('config:manage_fees', 'Manage fee configuration'),
('config:manage_partners', 'Manage payout partners'),
('agent:create_transaction', 'Create transactions as agent'),
('agent:view_float', 'View agent float balance'),
('agent:view_commissions', 'View agent commissions');

-- CUSTOMER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CUSTOMER' AND p.code IN ('transaction:create', 'transaction:view', 'transaction:cancel', 'user:view', 'user:edit');

-- AGENT permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'AGENT' AND p.code IN ('agent:create_transaction', 'agent:view_float', 'agent:view_commissions', 'transaction:view');

-- COMPLIANCE_OFFICER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'COMPLIANCE_OFFICER' AND p.code IN ('compliance:view_alerts', 'compliance:file_sar', 'compliance:override_screening', 'user:view', 'user:approve_kyc', 'transaction:view_all');

-- PAYOUT_PARTNER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'PAYOUT_PARTNER' AND p.code IN ('payout:view_assigned', 'payout:mark_paid', 'payout:upload_proof');

-- TREASURY_MANAGER permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'TREASURY_MANAGER' AND p.code IN ('fx:manage_rates', 'fx:set_margins', 'fx:view_nostro', 'report:view_financial');

-- ADMIN permissions (almost everything)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ADMIN' AND p.code IN (
    'transaction:create', 'transaction:view', 'transaction:view_all', 'transaction:cancel', 'transaction:refund',
    'user:view', 'user:edit', 'user:suspend', 'user:approve_kyc',
    'compliance:view_alerts', 'compliance:file_sar',
    'fx:manage_rates', 'fx:set_margins', 'fx:view_nostro',
    'payout:view_assigned', 'payout:mark_paid',
    'report:view_operational', 'report:view_financial', 'report:export',
    'config:manage_corridors', 'config:manage_fees', 'config:manage_partners'
);

-- SUPER_ADMIN permissions (everything)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'SUPER_ADMIN';

-- Default super admin user (password: Admin@123456)
INSERT INTO users (uuid, email, phone, password_hash, first_name, last_name, user_type, kyc_tier, status, mfa_enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin@forexbridge.com', '+44000000000',
        '$2b$10$919IbDvDpA1y.RArsaetZerLZsD/dt6R1mg/OHzVXtshgvhduKFa.',
        'Super', 'Admin', 'INDIVIDUAL', 'TIER_3', 'ACTIVE', FALSE);

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r
WHERE u.email = 'admin@forexbridge.com' AND r.name = 'SUPER_ADMIN';

-- Rebrand: replace all remaining "Forex Bridge" / "forexbridge" occurrences in DB data with "Remitz".
-- Covers notification_templates, system_config, and the seeded admin user email.

-- Notification template bodies and subjects
UPDATE notification_templates
SET body_template = REPLACE(REPLACE(REPLACE(body_template, 'Forex Bridge', 'Remitz'), 'ForexBridge', 'Remitz'), 'forexbridge', 'remitz'),
    subject       = REPLACE(REPLACE(REPLACE(subject,       'Forex Bridge', 'Remitz'), 'ForexBridge', 'Remitz'), 'forexbridge', 'remitz')
WHERE body_template LIKE '%Forex Bridge%' OR body_template LIKE '%ForexBridge%' OR body_template LIKE '%forexbridge%'
   OR subject       LIKE '%Forex Bridge%' OR subject       LIKE '%ForexBridge%' OR subject       LIKE '%forexbridge%';

-- Email domain references inside template bodies
UPDATE notification_templates
SET body_template = REPLACE(REPLACE(body_template, 'contact@forexbridge.com.au', 'support@remitz.co.uk'), 'forexbridge.com.au', 'remitz.co.uk')
WHERE body_template LIKE '%forexbridge.com%';

-- system_config brand settings
UPDATE system_config
SET config_value = REPLACE(REPLACE(REPLACE(config_value, 'Forex Bridge', 'Remitz'), 'ForexBridge', 'Remitz'), 'forexbridge', 'remitz')
WHERE config_key IN ('brand.name', 'brand.support-email', 'app.name', 'email.from', 'email.sender-name')
  AND (config_value LIKE '%Forex Bridge%' OR config_value LIKE '%ForexBridge%' OR config_value LIKE '%forexbridge%');

-- Update the seeded admin email address if it still uses the old domain
UPDATE users
SET email = REPLACE(email, '@forexbridge.com', '@remitz.co.uk')
WHERE email LIKE '%@forexbridge.com%';

-- Forex Bridge rebranding — applied to notification_templates.
-- Code added by Naresh: Forex Bridge rebranding.
--
-- On fresh deploys, V501..V503 seed template rows with the legacy ForexBridge / ForexBridge
-- brand text + the legacy support@forexbridge.com / forexbridge.com domain. This migration
-- rewrites them so all email / SMS / push / in-app bodies render with Forex Bridge
-- branding out of the box.
--
-- Idempotent: REPLACE is safe to re-run; strings that are already correct remain unchanged.

UPDATE notification_templates
SET subject = REPLACE(
                REPLACE(
                  REPLACE(subject, 'ForexBridge', 'Forex Bridge'),
                  'ForexBridge',    'Forex Bridge'),
                'ForexBridge',    'Forex Bridge'),
    body_template = REPLACE(
                      REPLACE(
                        REPLACE(
                          REPLACE(
                            REPLACE(body_template, 'ForexBridge', 'Forex Bridge'),
                            'ForexBridge',    'Forex Bridge'),
                          'ForexBridge',    'Forex Bridge'),
                        'support@forexbridge.com', 'contact@forexbridge.com.au'),
                      'forexbridge.com', 'forexbridge.com.au');

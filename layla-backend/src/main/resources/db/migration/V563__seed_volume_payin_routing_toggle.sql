-- Expose Volume (Open Banking / "Pay by Bank") as a customer pay-in option toggle.
-- Mirrors routing.payin.kuber.enabled so it renders as a BOOLEAN switch in the
-- superadmin System Controls page. Seeded disabled per product decision; the
-- super admin can flip it on in System Controls.
-- INSERT IGNORE keeps replay safe (config_key is UNIQUE).
INSERT IGNORE INTO system_config
  (config_key, config_value, value_type, category, allowed_values, version, description)
VALUES
  ('routing.payin.volume.enabled', 'false', 'BOOLEAN', 'routing', NULL, 1,
   'Expose Volume (Open Banking / Pay by Bank) as a pay-in option to customers.');

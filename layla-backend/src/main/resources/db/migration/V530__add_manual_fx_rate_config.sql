INSERT INTO system_config (config_key, config_value, value_type, category, description, version)
VALUES
  ('fx.manual_rate_mode', 'false', 'BOOLEAN', 'fx',
   'When true, exchange rates are taken from admin-set manual overrides instead of the live API', 1),
  ('fx.manual_rates', '{}', 'JSON', 'fx',
   'JSON map of manual exchange rate overrides, e.g. {"GBP_SDG":"605.50","GBP_INR":"100.25"}', 1)
ON DUPLICATE KEY UPDATE config_key = config_key;

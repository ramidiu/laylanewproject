-- Add Sudan (SD / SDG) to the per-sending-country payment methods config.
-- Inserted DISABLED (is_active = FALSE) so a super-admin can enable it from
-- Transfer Config → Payment Methods. Idempotent: skipped if Sudan already exists.

INSERT INTO payment_methods (country_name, country_code, currency, payment_method, is_active)
SELECT v.country_name, v.country_code, v.currency, v.payment_method, v.is_active
FROM (
    SELECT 'Sudan' AS country_name, 'SD' AS country_code, 'SDG' AS currency, 'BANK_TRANSFER'     AS payment_method, FALSE AS is_active
    UNION ALL SELECT 'Sudan', 'SD', 'SDG', 'CREDIT_DEBIT_CARD', FALSE
    UNION ALL SELECT 'Sudan', 'SD', 'SDG', 'PAY_WITH_BANK',     FALSE
    UNION ALL SELECT 'Sudan', 'SD', 'SDG', 'WALLET',            FALSE
) v
WHERE NOT EXISTS (SELECT 1 FROM payment_methods pm WHERE pm.country_code = 'SD');

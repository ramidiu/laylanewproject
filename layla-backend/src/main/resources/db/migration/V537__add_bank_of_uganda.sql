-- ============================================================================
-- V537: Add "Bank of Uganda" to the Uganda bank list (USI Money sandbox uses
-- it as the test payout bank for UG/UGX bank deposits).
-- ============================================================================

INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT 'UG', 'Bank of Uganda', NULL, 'Kampala', 'Any Branch', 'Kampala', 1
WHERE NOT EXISTS (
    SELECT 1 FROM bank_database
    WHERE country_code = 'UG' AND bank_name = 'Bank of Uganda'
);

-- ECOBANK was also listed in the USI sandbox spec — add it too if missing.
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT 'UG', 'ECOBANK', NULL, 'Kampala', 'Any Branch', 'Kampala', 1
WHERE NOT EXISTS (
    SELECT 1 FROM bank_database
    WHERE country_code = 'UG' AND bank_name = 'ECOBANK'
);

-- ============================================================================
-- V535: Add "Central Bank of Egypt" to the Egypt bank list (USI sandbox uses
-- it as the test payout bank) and drop the SWIFT/BIC requirement for Egypt
-- (USI's Egypt corridor does not need SWIFT — account number is sufficient).
-- ============================================================================

INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT 'EG', 'Central Bank of Egypt', NULL, 'Cairo', 'Any Branch', 'Cairo', 1
WHERE NOT EXISTS (
    SELECT 1 FROM bank_database
    WHERE country_code = 'EG' AND bank_name = 'Central Bank of Egypt'
);

-- Mark Egypt as not requiring a SWIFT/BIC — frontend reads identifier_name
-- to decide whether to render the SWIFT field; setting it to NONE hides it.
UPDATE country_bank_config
SET identifier_name   = 'NONE',
    identifier_label  = NULL,
    identifier_format = NULL,
    identifier_length = NULL
WHERE country_code = 'EG';

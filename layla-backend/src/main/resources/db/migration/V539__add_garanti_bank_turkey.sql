-- ============================================================================
-- V539: Add "Garanti Bank" to the Turkey bank list (USI Money sandbox uses
-- this exact name for the test payout bank; "Garanti BBVA" is the modern
-- consumer-facing name but USI hasn't synced — keep both).
-- ============================================================================

INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT 'TR', 'Garanti Bank', NULL, 'Istanbul', 'Any Branch', 'Istanbul', 1
WHERE NOT EXISTS (
    SELECT 1 FROM bank_database
    WHERE country_code = 'TR' AND bank_name = 'Garanti Bank'
);

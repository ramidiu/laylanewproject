-- ============================================================================
-- V538: Drop the SWIFT/BIC requirement for Uganda — USI Money's Uganda
-- corridor does not need SWIFT (account number + bank name + branch name +
-- state + city is sufficient).
-- ============================================================================

UPDATE country_bank_config
SET identifier_name   = 'NONE',
    identifier_label  = NULL,
    identifier_format = NULL,
    identifier_length = NULL
WHERE country_code = 'UG';

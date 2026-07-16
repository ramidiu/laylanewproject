-- ============================================================================
-- V536: Widen beneficiaries.sort_code from VARCHAR(20) → VARCHAR(150)
-- ----------------------------------------------------------------------------
-- The column originally held UK sort codes (6 digits). Now it doubles as the
-- bank Branch Name in the USI Money form ("Central Bank of Egypt",
-- "First Abu Dhabi Bank", etc.) which can exceed 20 characters.
-- ============================================================================

ALTER TABLE beneficiaries
    MODIFY COLUMN sort_code VARCHAR(150) NULL;

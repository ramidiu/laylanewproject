-- ============================================================================
-- V534: Add bank branch state + city to beneficiaries
-- ----------------------------------------------------------------------------
-- USI Money's createBeneficiary requires bank_branch_state and bank_branch_city
-- per beneficiary (defaults to "Any Branch" in their sandbox spec). Adding two
-- nullable columns so the customer Add Recipient form can collect them when
-- the destination corridor is USI-routed.
-- ============================================================================

ALTER TABLE beneficiaries
    ADD COLUMN branch_state VARCHAR(100) NULL AFTER sort_code,
    ADD COLUMN branch_city  VARCHAR(100) NULL AFTER branch_state;

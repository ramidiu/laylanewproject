-- ============================================================================
-- V540: Add payin_beneficiaries.linked_regular_beneficiary_id
-- ----------------------------------------------------------------------------
-- The PayIn Partner create-transaction flow now also mirrors each new
-- beneficiary into the regular beneficiaries table (so the USI Money admin
-- page's join works). This column stores the mirrored row's id so we can
-- look it up when creating the linked TransactionEntity.
-- ============================================================================

ALTER TABLE payin_beneficiaries
    ADD COLUMN linked_regular_beneficiary_id BIGINT NULL AFTER ifsc_code,
    ADD INDEX idx_payin_ben_linked_reg (linked_regular_beneficiary_id);

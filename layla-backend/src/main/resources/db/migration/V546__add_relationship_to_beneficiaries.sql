-- ============================================================================
-- V546: Dedicated relationship column on beneficiaries
-- ----------------------------------------------------------------------------
-- The customer "Add Recipient" form has always sent a `relationship`
-- (Family / Friend / ...), but it was being stored in the `id_type` column
-- because no dedicated column existed. This adds a first-class `relationship`
-- column and migrates the existing values out of `id_type`.
--
-- Safe backfill: `id_type` on this table was only ever populated with the
-- relationship string (the Add Recipient form always sends idType=null), so
-- every existing id_type value IS a relationship — copy it across, then clear
-- id_type so it can resume its true meaning (an ID document type).
-- ============================================================================

ALTER TABLE beneficiaries
    ADD COLUMN relationship VARCHAR(100) NULL AFTER id_type;

UPDATE beneficiaries
    SET relationship = id_type
    WHERE id_type IS NOT NULL AND id_type <> '';

UPDATE beneficiaries
    SET id_type = NULL
    WHERE relationship IS NOT NULL;

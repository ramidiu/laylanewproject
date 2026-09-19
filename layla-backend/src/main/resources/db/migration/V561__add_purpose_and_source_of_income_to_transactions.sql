-- Capture the remitter's declared Purpose of Transaction and Source of Income at
-- send-money time. These feed the USI-required CSV report (previously not stored
-- per-transaction, so the report defaulted them). Nullable — historical rows and
-- non-USI flows may leave them blank.
ALTER TABLE transactions
    ADD COLUMN purpose_of_transaction VARCHAR(100) NULL AFTER notes,
    ADD COLUMN source_of_income       VARCHAR(100) NULL AFTER purpose_of_transaction;

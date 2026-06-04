ALTER TABLE payin_transactions
    ADD COLUMN receive_currency VARCHAR(10)  NULL AFTER currency,
    ADD COLUMN receive_amount   DECIMAL(18,4) NULL AFTER receive_currency,
    ADD COLUMN delivery_method  VARCHAR(30)   NULL AFTER receive_amount;

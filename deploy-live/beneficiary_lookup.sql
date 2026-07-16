-- Inspect the beneficiary behind a transaction (change the reference if needed).
SET @ref = 'TXN4045';
SET @bid = (SELECT beneficiary_id FROM transactions WHERE reference_number = @ref);

SELECT '--- transaction ---' s;
SELECT reference_number, sender_id, beneficiary_id, delivery_method, status, receive_currency
FROM transactions WHERE reference_number = @ref;

SELECT '--- beneficiary record ---' s;
SELECT id, full_name, country, delivery_method, bank_name, account_number,
       iban, swift_bic, sort_code AS branch, branch_state, branch_city,
       mobile_number, mobile_provider, address
FROM beneficiaries WHERE id = @bid;

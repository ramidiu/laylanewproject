-- Full history of a transaction + its beneficiary + sender + how it was created.
SET @ref = 'TXN4045';
SET @tid = (SELECT id FROM transactions WHERE reference_number = @ref);
SET @bid = (SELECT beneficiary_id FROM transactions WHERE reference_number = @ref);
SET @sid = (SELECT sender_id FROM transactions WHERE reference_number = @ref);

SELECT '--- 1. TRANSACTION (payin_partner_id set => created via a pay-in partner) ---' s;
SELECT id, reference_number, created_at, status, send_amount, send_currency,
       receive_amount, receive_currency, delivery_method,
       sender_id, sender_name, beneficiary_id, payin_partner_id, payout_partner_id,
       (payin_partner_id IS NOT NULL) AS created_via_payin
FROM transactions WHERE id = @tid;

SELECT '--- 2. BENEFICIARY (is_imported=1 => legacy import) ---' s;
SELECT id, full_name, country, delivery_method, bank_name, account_number, swift_bic,
       sort_code AS branch, mobile_number, address, user_id, created_at,
       (id IN (SELECT new_beneficiary_id FROM migration_beneficiary_map)) AS is_imported
FROM beneficiaries WHERE id = @bid;

SELECT '--- 3. SENDER ---' s;
SELECT id, email, first_name, last_name, created_at, kyc_tier, status,
       (id IN (SELECT new_user_id FROM migration_user_map)) AS is_imported
FROM users WHERE id = @sid;

SELECT '--- 4. PAY-IN TRANSACTION linked to this one (if created via payin) ---' s;
SELECT transaction_id, customer_id, customer_source, beneficiary_id, linked_transaction_id, created_at
FROM payin_transactions WHERE linked_transaction_id = @tid;

SELECT '--- 5. PAY-IN BENEFICIARY linked to this beneficiary (source data captured) ---' s;
SELECT id, customer_id, name, bank_name, account_number, linked_regular_beneficiary_id, created_at
FROM payin_beneficiaries WHERE linked_regular_beneficiary_id = @bid;

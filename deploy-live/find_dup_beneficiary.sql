-- Show all beneficiaries that share SANA's name (to pick good vs null) + their tx counts.
SET @name = (SELECT full_name FROM beneficiaries WHERE id = 2861);
SET @owner = (SELECT user_id FROM beneficiaries WHERE id = 2861);

SELECT b.id, b.full_name, b.user_id, b.delivery_method,
       b.bank_name, b.account_number, b.swift_bic, b.created_at,
       (SELECT COUNT(*) FROM transactions t WHERE t.beneficiary_id = b.id) AS txn_count,
       CASE WHEN b.bank_name IS NULL OR b.bank_name = '' THEN 'NULL (delete candidate)' ELSE 'HAS DETAILS (keep)' END AS verdict
FROM beneficiaries b
WHERE b.user_id = @owner AND b.full_name = @name
ORDER BY (b.bank_name IS NULL), b.created_at;

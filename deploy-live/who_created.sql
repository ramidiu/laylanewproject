-- Who created/owns beneficiary 2861 (SANA) and its transactions.
SET @bid = 2861;

SELECT '--- beneficiary owner (the customer this recipient belongs to) ---' s;
SELECT u.id, u.email, u.first_name, u.last_name, u.phone, u.kyc_tier, u.status, u.created_at,
       (u.id IN (SELECT new_user_id FROM migration_user_map)) AS is_imported
FROM users u
WHERE u.id = (SELECT user_id FROM beneficiaries WHERE id = @bid);

SELECT '--- transactions to this beneficiary (sender + which partner created it) ---' s;
SELECT t.reference_number, t.created_at, t.status, t.send_amount, t.send_currency,
       t.sender_id, t.sender_name, t.sender_email,
       t.payin_partner_id, p.partner_name AS payin_partner,
       (t.payin_partner_id IS NOT NULL) AS created_via_payin
FROM transactions t
LEFT JOIN payin_partners p ON p.id = t.payin_partner_id
WHERE t.beneficiary_id = @bid
ORDER BY t.created_at DESC;

-- Copy bank details from the good same-name beneficiary into the blank one (id 2861).
SET @tgt := 2861;

-- Pick the most recent same-owner, same-name beneficiary that HAS a bank name.
SET @src := (SELECT b.id FROM beneficiaries b
  WHERE b.user_id   = (SELECT user_id   FROM beneficiaries WHERE id = @tgt)
    AND b.full_name = (SELECT full_name FROM beneficiaries WHERE id = @tgt)
    AND b.id <> @tgt
    AND b.bank_name IS NOT NULL AND b.bank_name <> ''
  ORDER BY b.created_at DESC LIMIT 1);

SELECT '--- source picked ---' s, @src AS src_id;
SELECT id, full_name, bank_name, account_number, swift_bic, sort_code AS branch, branch_state, branch_city
FROM beneficiaries WHERE id = @src;

-- Copy details into the blank one (only runs if a source was found).
UPDATE beneficiaries tgt
JOIN beneficiaries src ON src.id = @src
SET tgt.bank_name     = src.bank_name,
    tgt.account_number= src.account_number,
    tgt.iban          = src.iban,
    tgt.swift_bic     = src.swift_bic,
    tgt.sort_code     = src.sort_code,
    tgt.branch_state  = src.branch_state,
    tgt.branch_city   = src.branch_city
WHERE tgt.id = @tgt;

SELECT '--- result (beneficiary 2861 after) ---' s;
SELECT id, full_name, bank_name, account_number, swift_bic, sort_code AS branch, branch_state, branch_city
FROM beneficiaries WHERE id = @tgt;

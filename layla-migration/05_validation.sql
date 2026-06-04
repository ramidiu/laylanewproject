-- =============================================================================
-- STEP 5 — Post-migration validation queries.
--           Run AFTER the live migration to confirm data integrity.
--
-- Execute inside container:
--   docker exec -i layla-mysql mysql -uroot -proot remitz < 05_validation.sql
-- =============================================================================

USE remitz;

SELECT '=== POST-MIGRATION VALIDATION REPORT ===' AS report, NOW() AS run_at;

-- ---------------------------------------------------------------------------
-- 1. Record count comparison
-- ---------------------------------------------------------------------------
SELECT
    '1. RECORD COUNTS' AS section,
    tbl.table_name,
    tbl.old_count,
    tbl.new_count,
    CASE WHEN tbl.new_count >= tbl.old_count THEN 'PASS' ELSE 'FAIL' END AS result
FROM (
    SELECT 'remit_one'          AS table_name,
        (SELECT COUNT(*) FROM layla_old.remit_one) AS old_count,
        (SELECT COUNT(*) FROM remitz.remit_one)    AS new_count
    UNION ALL
    SELECT 'users (migrated)',
        (SELECT COUNT(*) FROM layla_old.`user` WHERE USER_EMAIL IS NOT NULL),
        (SELECT COUNT(*) FROM remitz.users WHERE id > 6)
    UNION ALL
    SELECT 'migration_user_map',
        (SELECT COUNT(*) FROM layla_old.`user` WHERE USER_EMAIL IS NOT NULL),
        (SELECT COUNT(*) FROM remitz.migration_user_map)
    UNION ALL
    SELECT 'wallets',
        (SELECT COUNT(*) FROM remitz.migration_user_map),
        (SELECT COUNT(*) FROM remitz.wallets
         WHERE user_id IN (SELECT new_user_id FROM remitz.migration_user_map))
    UNION ALL
    SELECT 'kyc_documents (identity)',
        (SELECT COUNT(*) FROM layla_old.user_identity),
        (SELECT COUNT(*) FROM remitz.kyc_documents
         WHERE user_id > 6 AND document_type != 'PROOF_OF_ADDRESS'
           AND COALESCE(document_number,'') NOT LIKE '%_BACK')
    UNION ALL
    SELECT 'kyc_verifications',
        (SELECT COUNT(DISTINCT USER_ID) FROM layla_old.user_identity),
        (SELECT COUNT(*) FROM remitz.kyc_verifications WHERE user_id > 6)
    UNION ALL
    SELECT 'beneficiaries',
        (SELECT COUNT(*) FROM layla_old.beneficiary
         WHERE SENDING_CUST_USER_ID IN (SELECT old_user_id FROM remitz.migration_user_map)),
        (SELECT COUNT(*) FROM remitz.beneficiaries)
    UNION ALL
    SELECT 'transactions',
        (SELECT COUNT(*) FROM layla_old.`transaction`
         WHERE SENDING_CUST_USER_ID IN (SELECT old_user_id FROM remitz.migration_user_map)),
        (SELECT COUNT(*) FROM remitz.transactions WHERE reference_number LIKE 'TXN%')
) tbl;

-- ---------------------------------------------------------------------------
-- 2. Financial reconciliation (PAID transactions)
-- ---------------------------------------------------------------------------
SELECT '2. FINANCIAL RECONCILIATION' AS section;

SELECT
    'PAID transactions'                 AS metric,
    COUNT(*)                            AS count,
    ROUND(SUM(send_amount), 2)          AS total_send_amount_gbp,
    ROUND(SUM(receive_amount), 2)       AS total_receive_amount,
    ROUND(SUM(total_debit_amount), 2)   AS total_debit_gbp
FROM remitz.transactions
WHERE status = 'PAID'
  AND reference_number LIKE 'TXN%';

SELECT
    'Old DB PAID total (SENDING_AMOUNT)' AS metric,
    COUNT(*)                             AS count,
    ROUND(SUM(SENDING_AMOUNT), 2)        AS total_gbp
FROM layla_old.`transaction`
WHERE LOWER(STATUS) = 'paid';

-- ---------------------------------------------------------------------------
-- 3. Transaction status distribution
-- ---------------------------------------------------------------------------
SELECT '3. TRANSACTION STATUS DISTRIBUTION' AS section;

SELECT
    status,
    COUNT(*)                    AS count,
    ROUND(SUM(send_amount), 2)  AS total_gbp
FROM remitz.transactions
WHERE reference_number LIKE 'TXN%'
GROUP BY status
ORDER BY count DESC;

-- ---------------------------------------------------------------------------
-- 4. KYC tier breakdown
-- ---------------------------------------------------------------------------
SELECT '4. KYC TIER BREAKDOWN' AS section;

SELECT
    kyc_tier,
    COUNT(*) AS user_count
FROM remitz.users
WHERE id > 6
GROUP BY kyc_tier
ORDER BY kyc_tier;

-- ---------------------------------------------------------------------------
-- 5. Orphan checks (foreign key violations)
-- ---------------------------------------------------------------------------
SELECT '5. ORPHAN CHECKS' AS section;

-- Transactions with no matching sender
SELECT
    'Transactions → no sender' AS orphan_type,
    COUNT(*) AS count
FROM remitz.transactions t
LEFT JOIN remitz.users u ON t.sender_id = u.id
WHERE u.id IS NULL AND t.reference_number LIKE 'TXN%';

-- Transactions with no beneficiary (NULL is ok — LEFT JOIN was used)
SELECT
    'Transactions with NULL beneficiary_id' AS note,
    COUNT(*) AS count
FROM remitz.transactions
WHERE beneficiary_id IS NULL
  AND reference_number LIKE 'TXN%';

-- kyc_documents with no matching user
SELECT
    'kyc_documents → no user' AS orphan_type,
    COUNT(*) AS count
FROM remitz.kyc_documents kd
LEFT JOIN remitz.users u ON kd.user_id = u.id
WHERE u.id IS NULL AND kd.user_id > 6;

-- ---------------------------------------------------------------------------
-- 6. Duplicate checks
-- ---------------------------------------------------------------------------
SELECT '6. DUPLICATE CHECKS' AS section;

SELECT
    'Duplicate transaction reference_number' AS check_name,
    COUNT(*) AS duplicate_groups
FROM (
    SELECT reference_number
    FROM remitz.transactions
    GROUP BY reference_number
    HAVING COUNT(*) > 1
) dup;

SELECT
    'Duplicate user email' AS check_name,
    COUNT(*) AS duplicate_groups
FROM (
    SELECT email
    FROM remitz.users
    GROUP BY email
    HAVING COUNT(*) > 1
) dup;

-- ---------------------------------------------------------------------------
-- 7. remit_one status distribution
-- ---------------------------------------------------------------------------
SELECT '7. REMIT_ONE STATUS DISTRIBUTION' AS section;

SELECT
    payment_status,
    COUNT(*) AS count
FROM remitz.remit_one
GROUP BY payment_status;

-- ---------------------------------------------------------------------------
-- 8. Migration log summary
-- ---------------------------------------------------------------------------
SELECT '8. MIGRATION LOG (last 20 entries)' AS section;

SELECT phase, table_name, status, message, logged_at
FROM remitz.migration_log
ORDER BY logged_at DESC
LIMIT 20;

-- ---------------------------------------------------------------------------
-- 9. Migration summary table
-- ---------------------------------------------------------------------------
SELECT '9. MIGRATION SUMMARY' AS section;

SELECT phase, attempted, succeeded, skipped, failed, run_at
FROM remitz.migration_summary
ORDER BY run_at;

-- ---------------------------------------------------------------------------
-- 10. Sample migrated users
-- ---------------------------------------------------------------------------
SELECT '10. SAMPLE MIGRATED USERS (first 5)' AS section;

SELECT
    u.id,
    u.email,
    u.first_name,
    u.last_name,
    u.phone,
    u.status,
    u.kyc_tier,
    u.email_verified,
    u.city,
    u.postcode,
    u.created_at,
    m.old_user_id
FROM remitz.users u
JOIN remitz.migration_user_map m ON m.new_user_id = u.id
ORDER BY u.id
LIMIT 5;

SELECT '=== VALIDATION COMPLETE ===' AS status;

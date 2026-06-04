-- ============================================================================
-- Full customer dump. Change the email below if needed.
-- Run: docker compose -f docker-compose.production.yml exec -T mysql \
--        sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" remitz' < customer_lookup.sql
-- ============================================================================
SET @em = 'abdallahishag1988@gmail.com';
SET @uid = (SELECT id FROM users WHERE email = @em);

SELECT '--- 1. PROFILE ---' AS section;
SELECT id, uuid, email, phone, first_name, last_name, status, kyc_tier,
       email_verified, country, country_of_residence, city, postcode, created_at,
       (id IN (SELECT new_user_id FROM migration_user_map)) AS is_imported
FROM users WHERE id = @uid;

SELECT '--- 2. KYC DOCUMENTS (file_hash NULL = imported/legacy, not a real upload) ---' AS section;
SELECT id, document_type, status, document_number,
       (file_hash IS NOT NULL) AS real_upload, file_path, created_at
FROM kyc_documents WHERE user_id = @uid ORDER BY created_at;

SELECT '--- 2b. WHAT IS PRESENT (real uploads only) ---' AS section;
SELECT
  SUM(file_hash IS NOT NULL AND document_type IN ('PASSPORT','DRIVING_LICENCE','NATIONAL_ID')) AS real_ID_docs,
  SUM(file_hash IS NOT NULL AND document_type = 'PROOF_OF_ADDRESS')                            AS real_address_proof,
  SUM(file_hash IS NULL)                                                                        AS imported_placeholders
FROM kyc_documents WHERE user_id = @uid;

SELECT '--- 3. KYC VERIFICATIONS ---' AS section;
SELECT id, verification_type, status, provider, created_at
FROM kyc_verifications WHERE user_id = @uid ORDER BY created_at;

SELECT '--- 4. BENEFICIARIES ---' AS section;
SELECT id, full_name, country, delivery_method, bank_name, account_number,
       mobile_number, mobile_provider, is_blocked, created_at
FROM beneficiaries WHERE user_id = @uid ORDER BY created_at;

SELECT '--- 5. TRANSACTIONS ---' AS section;
SELECT reference_number, status, send_amount, send_currency, receive_amount,
       receive_currency, delivery_method, created_at
FROM transactions WHERE sender_id = @uid ORDER BY created_at DESC;

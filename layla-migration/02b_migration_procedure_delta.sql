-- =============================================================================
-- STEP 2 — Main migration stored procedure.
--           Migrates data from layla_old → remitz.
--
-- Call with dry_run = 1 to test (ROLLBACK at end, shows validation report).
-- Call with dry_run = 0 to commit for real.
--
-- Execute inside container:
--   docker exec -i layla-mysql mysql -uroot -proot remitz < 02_migration_procedure.sql
-- =============================================================================

USE remitz;

DROP PROCEDURE IF EXISTS run_migration;

DELIMITER $$

CREATE PROCEDURE run_migration(IN dry_run TINYINT(1))
    COMMENT 'Full data migration from layla_old to remitz. dry_run=1 rolls back, dry_run=0 commits.'
BEGIN

    -- =========================================================================
    -- Variable declarations
    -- =========================================================================
    DECLARE v_phase         VARCHAR(60)  DEFAULT 'INIT';
    DECLARE v_inserted      INT          DEFAULT 0;
    DECLARE v_skipped       INT          DEFAULT 0;
    DECLARE v_old_count     INT          DEFAULT 0;
    DECLARE v_new_count     INT          DEFAULT 0;
    DECLARE v_start_time    DATETIME     DEFAULT NOW();
    DECLARE v_err_msg       TEXT         DEFAULT '';

    -- Exit handler: log the error, rollback, re-signal
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        GET DIAGNOSTICS CONDITION 1
            @err_state  = RETURNED_SQLSTATE,
            @err_no     = MYSQL_ERRNO,
            @err_text   = MESSAGE_TEXT;

        SET v_err_msg = CONCAT('[', @err_no, '] ', @err_text);

        -- Log to persistent table BEFORE rollback (separate implicit commit)
        INSERT INTO remitz.migration_log (phase, table_name, status, message)
        VALUES (v_phase, 'EXCEPTION', 'FAILED', v_err_msg);

        ROLLBACK;

        SELECT CONCAT('MIGRATION ABORTED at phase: ', v_phase,
                      CHAR(10), 'Error: ', v_err_msg) AS FATAL_ERROR;

        RESIGNAL;
    END;

    -- =========================================================================
    -- Pre-flight: disable strict mode, disable FK checks for duration
    -- =========================================================================
    SET SESSION sql_mode         = '';
    SET SESSION foreign_key_checks = 0;
    SET SESSION unique_checks      = 1;

    -- Log run start (outside transaction — persists regardless of dry_run)
    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES ('START', 'ALL', 'INFO',
            CONCAT('run_migration() started. dry_run=', dry_run,
                   ' | timestamp=', NOW()));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES ('START', 0, 0, 0, 0);

    -- =========================================================================
    -- BEGIN TRANSACTION
    -- =========================================================================
    START TRANSACTION;

    -- =========================================================================
    -- PHASE 1 — remit_one (no FK dependencies, standalone)
    -- =========================================================================
    SET v_phase = 'PHASE_1_REMIT_ONE';

    INSERT IGNORE INTO remitz.remit_one (
        id, transaction_id, trans_session_id, trans_type,
        remitter_id, remitter_name, beneficiary_id, beneficiary_name,
        destination_country, source_currency, source_amount, rate,
        destination_currency, destination_amount, commission, tax,
        remitter_pay_amount, comments_to_beneficiary, payment_status,
        created_on, MESSAGE
    )
    SELECT
        id, transaction_id, trans_session_id, trans_type,
        remitter_id, remitter_name, beneficiary_id, beneficiary_name,
        destination_country, source_currency, source_amount, rate,
        destination_currency, destination_amount, commission, tax,
        remitter_pay_amount, comments_to_beneficiary, payment_status,
        CASE WHEN created_on = '0000-00-00 00:00:00' THEN NOW() ELSE created_on END,
        MESSAGE
    FROM layla_old.remit_one;

    SET v_inserted  = ROW_COUNT();
    SET v_old_count = (SELECT COUNT(*) FROM layla_old.remit_one);

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'remit_one', 'OK',
            CONCAT('Inserted: ', v_inserted, ' / Attempted: ', v_old_count));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES (v_phase, v_old_count, v_inserted,
            v_old_count - v_inserted, 0);

    -- =========================================================================
    -- PHASE 2 — users (core user records)
    -- =========================================================================
    SET v_phase = 'PHASE_2_USERS';

    INSERT INTO remitz.users (
        uuid,
        email,
        phone,
        password_hash,
        first_name,
        last_name,
        status,
        email_verified,
        user_type,
        kyc_tier,
        payin_enabled,
        created_at,
        updated_at
    )
    SELECT
        UUID(),
        u.USER_EMAIL,
        NULLIF(TRIM(u.USER_PHONE), ''),
        u.USER_PASSWORD,
        NULLIF(TRIM(u.USER_FIRST_NAME), ''),
        NULLIF(TRIM(u.USER_LAST_NAME), ''),
        -- Status mapping
        CASE UPPER(TRIM(u.STATUS))
            WHEN 'ACTIVE'    THEN 'ACTIVE'
            WHEN 'INACTIVE'  THEN 'SUSPENDED'
            WHEN 'BLOCKED'   THEN 'SUSPENDED'
            WHEN 'SUSPENDED' THEN 'SUSPENDED'
            WHEN 'LOCKED'    THEN 'LOCKED'
            WHEN 'CLOSED'    THEN 'CLOSED'
            ELSE 'ACTIVE'
        END,
        -- Email verified: treat 'yes','1','true','verified' as verified
        CASE WHEN LOWER(TRIM(COALESCE(u.EMAIL_VERIFIED, ''))) IN ('yes','1','true','verified') THEN 1 ELSE 0 END,
        -- User type: user_type_id 3 = CUSTOMER → INDIVIDUAL; all others treated as INDIVIDUAL
        'INDIVIDUAL',
        -- KYC tier: set to TIER_0 now, updated in Phase 5
        'TIER_0',
        0,
        -- Timestamps: replace MySQL zero-dates with NOW()
        CASE WHEN u.CREATED_ON IS NULL OR UNIX_TIMESTAMP(u.CREATED_ON) = 0
             THEN NOW() ELSE u.CREATED_ON END,
        CASE WHEN u.LAST_MODIFIED_ON IS NULL OR UNIX_TIMESTAMP(u.LAST_MODIFIED_ON) = 0 THEN NOW() ELSE u.LAST_MODIFIED_ON END
    FROM layla_old.`user` u
    -- Deduplicate: for emails that appear multiple times in the old DB,
    -- keep only the record with the highest USER_ID (most recent account)
    INNER JOIN (
        SELECT USER_EMAIL, MAX(USER_ID) AS max_user_id
        FROM layla_old.`user`
        WHERE USER_EMAIL IS NOT NULL AND TRIM(USER_EMAIL) != ''
        GROUP BY USER_EMAIL
    ) dedup ON dedup.USER_EMAIL = u.USER_EMAIL AND dedup.max_user_id = u.USER_ID
    -- Skip users whose email already exists in the new DB
    WHERE NOT EXISTS (
        SELECT 1 FROM remitz.users nu WHERE nu.email = u.USER_EMAIL
    );

    SET v_inserted  = ROW_COUNT();
    SET v_old_count = (SELECT COUNT(*) FROM layla_old.`user`
                       WHERE USER_EMAIL IS NOT NULL AND TRIM(USER_EMAIL) != '');

    -- Populate migration_user_map for all old users (including pre-existing emails)
    INSERT IGNORE INTO remitz.migration_user_map (old_user_id, new_user_id, old_email)
    SELECT u.USER_ID, nu.id, u.USER_EMAIL
    FROM layla_old.`user` u
    JOIN remitz.users nu ON nu.email = u.USER_EMAIL
    WHERE u.USER_EMAIL IS NOT NULL AND TRIM(u.USER_EMAIL) != '';

    SET v_skipped = v_old_count - v_inserted;

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'users', 'OK',
            CONCAT('Inserted: ', v_inserted,
                   ' | Skipped (email existed): ', v_skipped,
                   ' | Mapped: ', (SELECT COUNT(*) FROM remitz.migration_user_map)));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES (v_phase, v_old_count, v_inserted, v_skipped, 0);

    -- =========================================================================
    -- PHASE 3 — wallets (one wallet per migrated user)
    -- =========================================================================
    SET v_phase = 'PHASE_3_WALLETS';

    INSERT IGNORE INTO remitz.wallets (
        user_id,
        balance,
        currency,
        is_active,
        created_at,
        updated_at,
        wallet_number,
        version
    )
    SELECT
        m.new_user_id,
        0.0000,
        COALESCE(NULLIF(TRIM(u.CURRENCY_NM), ''), 'GBP'),
        b'1',
        NOW(),
        NOW(),
        CONCAT('WAL', LPAD(m.new_user_id, 10, '0')),
        0
    FROM remitz.migration_user_map m
    JOIN layla_old.`user` u ON u.USER_ID = m.old_user_id
    WHERE NOT EXISTS (
        SELECT 1 FROM remitz.wallets w WHERE w.user_id = m.new_user_id
    );

    SET v_inserted = ROW_COUNT();

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'wallets', 'OK', CONCAT('Inserted: ', v_inserted));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES (v_phase, (SELECT COUNT(*) FROM remitz.migration_user_map), v_inserted, 0, 0);

    -- =========================================================================
    -- PHASE 4A — user_addr → UPDATE users (address fields inline)
    -- =========================================================================
    SET v_phase = 'PHASE_4A_ADDRESSES';

    -- Use the most recent address record per user (MAX USER_ADDR_ID)
    UPDATE remitz.users nu
    JOIN remitz.migration_user_map m ON m.new_user_id = nu.id
    JOIN (
        SELECT
            ua.USER_ID,
            ua.USER_ADDR1       AS addr1,
            ua.USER_ADDR2       AS addr2,
            ua.USER_CITY        AS city,
            ua.USER_POSTAL_CD   AS postcode,
            ua.USER_COUNTRY     AS country,
            ua.USER_STATE       AS state
        FROM layla_old.user_addr ua
        INNER JOIN (
            SELECT USER_ID, MAX(USER_ADDR_ID) AS max_id
            FROM layla_old.user_addr
            GROUP BY USER_ID
        ) latest ON latest.USER_ID = ua.USER_ID AND latest.max_id = ua.USER_ADDR_ID
    ) addr ON addr.USER_ID = m.old_user_id
    SET
        nu.address_line_1       = NULLIF(TRIM(COALESCE(addr.addr1, '')), ''),
        nu.address_line_2       = NULLIF(TRIM(CASE
            WHEN addr.addr2 IS NOT NULL AND addr.state IS NOT NULL
                THEN CONCAT(addr.addr2, ', ', addr.state)
            WHEN addr.state IS NOT NULL THEN addr.state
            ELSE addr.addr2
        END), ''),
        nu.city                 = NULLIF(TRIM(COALESCE(addr.city, '')), ''),
        nu.postcode             = NULLIF(TRIM(COALESCE(addr.postcode, '')), ''),
        nu.country_of_residence = NULLIF(TRIM(COALESCE(addr.country, '')), '')
    WHERE nu.id > 6;  -- never touch the 6 seed/admin users

    SET v_inserted = ROW_COUNT();

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'users (address fields)', 'OK',
            CONCAT('Updated: ', v_inserted, ' user address rows'));

    -- =========================================================================
    -- PHASE 4B — user_addr → kyc_documents (address proof documents)
    -- =========================================================================
    SET v_phase = 'PHASE_4B_ADDR_PROOF_DOCS';

    INSERT INTO remitz.kyc_documents (
        user_id,
        document_type,
        file_path,
        status,
        created_at,
        updated_at
    )
    SELECT
        m.new_user_id,
        'PROOF_OF_ADDRESS',
        COALESCE(NULLIF(TRIM(ua.ADDRESS_PROOF), ''), 'MIGRATED_PATH_PENDING_REMAP'),
        CASE LOWER(TRIM(COALESCE(ua.ADDR_VERIFIED, 'no')))
            WHEN 'yes' THEN 'APPROVED'
            ELSE 'PENDING'
        END,
        CASE WHEN ua.CREATED_ON IS NULL OR UNIX_TIMESTAMP(ua.CREATED_ON) = 0 THEN NOW() ELSE ua.CREATED_ON END,
        NOW()
    FROM layla_old.user_addr ua
    INNER JOIN (
        SELECT USER_ID, MAX(USER_ADDR_ID) AS max_id
        FROM layla_old.user_addr
        WHERE ADDRESS_PROOF IS NOT NULL AND TRIM(ADDRESS_PROOF) != ''
        GROUP BY USER_ID
    ) latest ON latest.USER_ID = ua.USER_ID AND latest.max_id = ua.USER_ADDR_ID
    JOIN remitz.migration_user_map m ON m.old_user_id = ua.USER_ID
    WHERE NOT EXISTS (
        SELECT 1 FROM remitz.kyc_documents kd
        WHERE kd.user_id = m.new_user_id
          AND kd.document_type = 'PROOF_OF_ADDRESS'
    );

    SET v_inserted = ROW_COUNT();

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'kyc_documents (address)', 'OK',
            CONCAT('Inserted: ', v_inserted, ' address proof records'));

    -- =========================================================================
    -- PHASE 5A — user_identity → kyc_documents (ID front + back)
    -- =========================================================================
    SET v_phase = 'PHASE_5A_KYC_DOCUMENTS';

    -- Front of identity document
    INSERT INTO remitz.kyc_documents (
        user_id,
        document_type,
        document_number,
        file_path,
        status,
        verified_at,
        expiry_date,
        created_at,
        updated_at
    )
    SELECT
        m.new_user_id,
        CASE ui.IDENTITY_TYPE_ID
            WHEN 3  THEN 'DRIVING_LICENCE'
            WHEN 5  THEN 'PASSPORT'
            WHEN 6  THEN 'PASSPORT'
            WHEN 7  THEN 'NATIONAL_ID'
            WHEN 8  THEN 'NATIONAL_ID'
            ELSE         'NATIONAL_ID'
        END,
        NULLIF(TRIM(COALESCE(ui.IDENTITY_DOC_NUM, '')), ''),
        COALESCE(NULLIF(TRIM(ui.ID_PROOF), ''), 'MIGRATED_PATH_PENDING_REMAP'),
        -- Status mapping
        CASE LOWER(TRIM(COALESCE(ui.ID_VERIFIED_STATUS, '')))
            WHEN 'full'     THEN 'APPROVED'
            WHEN 'verified' THEN 'APPROVED'
            WHEN 'partial'  THEN 'PENDING'
            ELSE                 'PENDING'
        END,
        -- verified_at
        CASE WHEN ui.VERIFIED_ON IS NULL OR UNIX_TIMESTAMP(ui.VERIFIED_ON) = 0
             THEN NULL ELSE ui.VERIFIED_ON END,
        -- expiry_date: try common date formats
        CASE
            WHEN ui.VALID_TO IS NULL OR TRIM(ui.VALID_TO) IN ('', '0000-00-00', 'N/A', 'null')
                 THEN NULL
            ELSE DATE(STR_TO_DATE(ui.VALID_TO, '%Y-%m-%d'))
        END,
        CASE WHEN ui.CREATED_ON IS NULL OR UNIX_TIMESTAMP(ui.CREATED_ON) = 0 THEN NOW() ELSE ui.CREATED_ON END,
        NOW()
    FROM layla_old.user_identity ui
    JOIN remitz.migration_user_map m ON m.old_user_id = ui.USER_ID
    WHERE NOT EXISTS (
        SELECT 1 FROM remitz.kyc_documents kd
        WHERE kd.user_id          = m.new_user_id
          AND kd.document_type    = CASE ui.IDENTITY_TYPE_ID
                WHEN 3 THEN 'DRIVING_LICENCE'
                WHEN 5 THEN 'PASSPORT'
                WHEN 6 THEN 'PASSPORT'
                WHEN 7 THEN 'NATIONAL_ID'
                WHEN 8 THEN 'NATIONAL_ID'
                ELSE 'NATIONAL_ID'
              END
          AND kd.document_number  = NULLIF(TRIM(COALESCE(ui.IDENTITY_DOC_NUM,'')), '')
    );

    SET v_inserted  = ROW_COUNT();
    SET v_old_count = (SELECT COUNT(*) FROM layla_old.user_identity);

    -- Back of identity document (separate row where ID_PROOF_BACK is not null)
    INSERT INTO remitz.kyc_documents (
        user_id,
        document_type,
        document_number,
        file_path,
        status,
        created_at,
        updated_at
    )
    SELECT
        m.new_user_id,
        CASE ui.IDENTITY_TYPE_ID
            WHEN 3  THEN 'DRIVING_LICENCE'
            WHEN 5  THEN 'PASSPORT'
            WHEN 6  THEN 'PASSPORT'
            WHEN 7  THEN 'NATIONAL_ID'
            WHEN 8  THEN 'NATIONAL_ID'
            ELSE         'NATIONAL_ID'
        END,
        CONCAT(COALESCE(NULLIF(TRIM(ui.IDENTITY_DOC_NUM),''), 'UNKNOWN'), '_BACK'),
        ui.ID_PROOF_BACK,
        CASE LOWER(TRIM(COALESCE(ui.ID_VERIFIED_STATUS, '')))
            WHEN 'full'     THEN 'APPROVED'
            WHEN 'verified' THEN 'APPROVED'
            ELSE                 'PENDING'
        END,
        CASE WHEN ui.CREATED_ON IS NULL OR UNIX_TIMESTAMP(ui.CREATED_ON) = 0 THEN NOW() ELSE ui.CREATED_ON END,
        NOW()
    FROM layla_old.user_identity ui
    JOIN remitz.migration_user_map m ON m.old_user_id = ui.USER_ID
    WHERE ui.ID_PROOF_BACK IS NOT NULL
      AND TRIM(ui.ID_PROOF_BACK) != ''
      -- DELTA GUARD: don't re-insert a back-of-ID doc that already exists for this user
      AND NOT EXISTS (
          SELECT 1 FROM remitz.kyc_documents kd
          WHERE kd.user_id = m.new_user_id
            AND kd.document_number = CONCAT(COALESCE(NULLIF(TRIM(ui.IDENTITY_DOC_NUM),''), 'UNKNOWN'), '_BACK')
      );

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'kyc_documents (identity)', 'OK',
            CONCAT('Inserted (front): ', v_inserted, ' / Old records: ', v_old_count));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES (v_phase, v_old_count, v_inserted, v_old_count - v_inserted, 0);

    -- =========================================================================
    -- PHASE 5B — user_identity → kyc_verifications (one per user)
    -- =========================================================================
    SET v_phase = 'PHASE_5B_KYC_VERIFICATIONS';

    INSERT INTO remitz.kyc_verifications (
        user_id,
        verification_type,
        provider,
        status,
        result_data,
        created_at
    )
    SELECT
        m.new_user_id,
        'IDENTITY',
        'MANUAL',
        -- Derive overall verification status from best record per user
        CASE LOWER(TRIM(COALESCE(ui_agg.best_status, '')))
            WHEN 'full'     THEN 'PASSED'
            WHEN 'verified' THEN 'PASSED'
            WHEN 'partial'  THEN 'PENDING'
            ELSE                 'FAILED'
        END,
        JSON_OBJECT(
            'migrated_from',       'layla_old',
            'old_user_id',         ui_agg.USER_ID,
            'id_verified_status',  COALESCE(ui_agg.best_status, ''),
            'identity_verified',   COALESCE(ui_agg.identity_verified, ''),
            'sanction_flag',       COALESCE(ui_agg.sanction_flag, 'no'),
            'blocked',             COALESCE(ui_agg.blocked, ''),
            'blocked_reason',      COALESCE(ui_agg.blocked_reason, ''),
            'doc_count',           ui_agg.doc_count
        ),
        CASE WHEN ui_agg.created_on IS NULL OR UNIX_TIMESTAMP(ui_agg.created_on) = 0 THEN NOW() ELSE ui_agg.created_on END
    FROM (
        SELECT
            USER_ID,
            -- Pick the most advanced verification status
            CASE
                WHEN SUM(CASE WHEN LOWER(ID_VERIFIED_STATUS) IN ('full','verified') THEN 1 ELSE 0 END) > 0
                    THEN 'full'
                WHEN SUM(CASE WHEN LOWER(ID_VERIFIED_STATUS) = 'partial' THEN 1 ELSE 0 END) > 0
                    THEN 'partial'
                ELSE 'Not-Verified'
            END AS best_status,
            MAX(IDENTITY_VERIFIED)  AS identity_verified,
            MAX(SANCTION_FLAG)      AS sanction_flag,
            MAX(BLOCKED)            AS blocked,
            MAX(BLOCKED_REASON)     AS blocked_reason,
            COUNT(*)                AS doc_count,
            MIN(CREATED_ON)         AS created_on
        FROM layla_old.user_identity
        GROUP BY USER_ID
    ) ui_agg
    JOIN remitz.migration_user_map m ON m.old_user_id = ui_agg.USER_ID
    WHERE NOT EXISTS (
        SELECT 1 FROM remitz.kyc_verifications kv
        WHERE kv.user_id           = m.new_user_id
          AND kv.verification_type = 'IDENTITY'
    );

    SET v_inserted = ROW_COUNT();

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'kyc_verifications', 'OK',
            CONCAT('Inserted: ', v_inserted));

    -- =========================================================================
    -- PHASE 5C — Update kyc_tier on users based on identity verification
    -- =========================================================================
    SET v_phase = 'PHASE_5C_KYC_TIER';

    UPDATE remitz.users nu
    JOIN remitz.migration_user_map m ON m.new_user_id = nu.id
    JOIN (
        SELECT
            USER_ID,
            CASE
                WHEN SUM(CASE WHEN LOWER(ID_VERIFIED_STATUS) IN ('full','verified') THEN 1 ELSE 0 END) > 0
                     THEN 'TIER_2'
                WHEN SUM(CASE WHEN LOWER(ID_VERIFIED_STATUS) = 'partial' THEN 1 ELSE 0 END) > 0
                     THEN 'TIER_1'
                ELSE 'TIER_0'
            END AS derived_tier
        FROM layla_old.user_identity
        GROUP BY USER_ID
    ) tier_src ON tier_src.USER_ID = m.old_user_id
    SET nu.kyc_tier = tier_src.derived_tier
    WHERE nu.id > 6;

    SET v_inserted = ROW_COUNT();

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'users.kyc_tier', 'OK',
            CONCAT('Updated kyc_tier for ', v_inserted, ' users'));

    -- =========================================================================
    -- PHASE 6 — beneficiaries
    -- =========================================================================
    SET v_phase = 'PHASE_6_BENEFICIARIES';

    INSERT INTO remitz.beneficiaries (
        user_id,
        full_name,
        email,
        address,
        country,
        delivery_method,
        mobile_number,
        created_at,
        updated_at
    )
    SELECT
        m.new_user_id,
        TRIM(CONCAT(
            COALESCE(b.BENIF_FN, ''), ' ', COALESCE(b.BENIF_LN, '')
        )),
        NULLIF(TRIM(COALESCE(b.EMAIL_ID, '')), ''),
        NULLIF(TRIM(COALESCE(b.ADDRESS,  '')), ''),
        -- Map country_id → ISO2 code from old country table
        COALESCE(
            (SELECT TRIM(c.COUNTRY_ISO) FROM layla_old.country c
             WHERE c.COUNTRY_ID = b.COUNTRY_ID LIMIT 1),
            'SD'
        ),
        -- Payment type
        CASE UPPER(TRIM(COALESCE(b.PAYMENT_TYPE, '')))
            WHEN 'ACCOUNT'          THEN 'BANK_DEPOSIT'
            WHEN 'BANK DEPOSIT'     THEN 'BANK_DEPOSIT'
            WHEN 'CASH'             THEN 'CASH_PICKUP'
            WHEN 'CASH COLLECTION'  THEN 'CASH_PICKUP'
            WHEN 'MOBILE'           THEN 'MOBILE_WALLET'
            WHEN 'WALLET'           THEN 'MOBILE_WALLET'
            ELSE                         'BANK_DEPOSIT'
        END,
        NULLIF(TRIM(COALESCE(b.MSSIDN, '')), ''),
        CASE WHEN b.CREATED_ON IS NULL OR UNIX_TIMESTAMP(b.CREATED_ON) = 0 THEN NOW() ELSE b.CREATED_ON END,
        NOW()
    FROM layla_old.beneficiary b
    JOIN remitz.migration_user_map m ON m.old_user_id = b.SENDING_CUST_USER_ID
    -- DELTA GUARD: only insert beneficiaries not already migrated (tracked by old id)
    WHERE b.BENEFICIARY_ID NOT IN (
        SELECT old_beneficiary_id FROM remitz.migration_beneficiary_map
    );

    SET v_inserted  = ROW_COUNT();
    SET v_old_count = (SELECT COUNT(*) FROM layla_old.beneficiary
                       WHERE SENDING_CUST_USER_ID IN
                            (SELECT old_user_id FROM remitz.migration_user_map));

    -- Build beneficiary mapping table
    -- Match: old BENEFICIARY_ID → new beneficiaries.id using user + name combination
    INSERT IGNORE INTO remitz.migration_beneficiary_map
        (old_beneficiary_id, new_beneficiary_id)
    SELECT
        b.BENEFICIARY_ID,
        (
            SELECT nb.id
            FROM remitz.beneficiaries nb
            JOIN remitz.migration_user_map mm2 ON mm2.new_user_id = nb.user_id
            WHERE mm2.old_user_id = b.SENDING_CUST_USER_ID
              AND nb.full_name = TRIM(CONCAT(COALESCE(b.BENIF_FN,''), ' ', COALESCE(b.BENIF_LN,'')))
            ORDER BY nb.id DESC
            LIMIT 1
        ) AS new_benif_id
    FROM layla_old.beneficiary b
    WHERE b.SENDING_CUST_USER_ID IN (SELECT old_user_id FROM remitz.migration_user_map)
      AND (
          SELECT nb.id FROM remitz.beneficiaries nb
          JOIN remitz.migration_user_map mm2 ON mm2.new_user_id = nb.user_id
          WHERE mm2.old_user_id = b.SENDING_CUST_USER_ID
            AND nb.full_name = TRIM(CONCAT(COALESCE(b.BENIF_FN,''), ' ', COALESCE(b.BENIF_LN,'')))
          ORDER BY nb.id DESC LIMIT 1
      ) IS NOT NULL;

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'beneficiaries', 'OK',
            CONCAT('Inserted: ', v_inserted,
                   ' | Mapped: ', (SELECT COUNT(*) FROM remitz.migration_beneficiary_map)));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES (v_phase, v_old_count, v_inserted, 0, 0);

    -- =========================================================================
    -- PHASE 7 — transactions
    -- =========================================================================
    SET v_phase = 'PHASE_7_TRANSACTIONS';

    INSERT INTO remitz.transactions (
        reference_number,
        idempotency_key,
        sender_id,
        beneficiary_id,
        corridor_id,
        status,
        delivery_method,
        send_amount,
        send_currency,
        receive_amount,
        receive_currency,
        exchange_rate,
        applied_rate,
        fee_amount,
        fee_currency,
        fx_margin_amount,
        total_debit_amount,
        payment_method_type,
        payment_reference,
        notes,
        failure_reason,
        is_recurring,
        risk_score,
        version,
        created_at,
        updated_at
    )
    SELECT
        t.TRANSACTION_ID,
        UUID(),
        m.new_user_id,
        bm.new_beneficiary_id,
        -- Corridor: match by send_currency + receive_currency
        COALESCE(
            (SELECT cor.id FROM remitz.corridors cor
             WHERE cor.send_currency    = t.SENDING_CURRENCY
               AND cor.receive_currency = tr.RECEIVING_CURRENCY
             LIMIT 1),
            16  -- fallback: GBP → SDG
        ),
        -- Status mapping
        CASE LOWER(TRIM(COALESCE(t.STATUS, 'pending')))
            WHEN 'paid'         THEN 'PAID'
            WHEN 'pending'      THEN 'PENDING'
            WHEN 'processing'   THEN 'PROCESSING'
            WHEN 'cancelled'    THEN 'CANCELLED'
            WHEN 'compliance'   THEN 'COMPLIANCE_HOLD'
            WHEN 'failed'       THEN 'FAILED'
            WHEN 'aborted'      THEN 'FAILED'
            WHEN 'refunded'     THEN 'REFUNDED'
            WHEN 'completed'    THEN 'COMPLETED'
            ELSE 'PENDING'
        END,
        -- Delivery method from receiving branch payment type
        CASE UPPER(TRIM(COALESCE(tr.BENF_PAYMENT_TYPE, '')))
            WHEN 'BANK DEPOSIT'     THEN 'BANK_DEPOSIT'
            WHEN 'CASH COLLECTION'  THEN 'CASH_PICKUP'
            WHEN 'ACCOUNT'          THEN 'BANK_DEPOSIT'
            WHEN 'CASH'             THEN 'CASH_PICKUP'
            WHEN 'MOBILE'           THEN 'MOBILE_WALLET'
            ELSE                         'BANK_DEPOSIT'
        END,
        t.SENDING_AMOUNT,
        t.SENDING_CURRENCY,
        COALESCE(tr.REC_AMOUNT, 0.0),
        COALESCE(NULLIF(TRIM(tr.RECEIVING_CURRENCY), ''), t.SENDING_CURRENCY),
        COALESCE(t.SELL_RATE,         0.0),
        COALESCE(t.SELL_RATE,         0.0),
        COALESCE(t.TRANSACTION_FEE,   0.0),
        NULLIF(TRIM(COALESCE(t.TRANSACTION_FEE_CURR, '')), ''),
        0.0,  -- fx_margin_amount: not in old schema, default 0
        COALESCE(t.TOTAL_AMOUNT, t.SENDING_AMOUNT + COALESCE(t.TRANSACTION_FEE, 0)),
        -- Payment method type
        CASE UPPER(TRIM(COALESCE(t.COLLECTION_TYPE, '')))
            WHEN 'INTERNET BANK TRANSFER' THEN 'BANK_TRANSFER'
            WHEN 'BANK TRANSFER'          THEN 'BANK_TRANSFER'
            WHEN 'CREDIT/DEBIT CARD'      THEN 'CARD'
            WHEN 'CASH'                   THEN 'AGENT_CASH'
            WHEN 'OPEN BANKING'           THEN 'OPEN_BANKING'
            ELSE                               'BANK_TRANSFER'
        END,
        NULLIF(TRIM(COALESCE(t.PAY_AT_REFERENCE, '')), ''),
        -- Notes: concatenate useful metadata
        TRIM(CONCAT_WS(' | ',
            CASE WHEN t.SOURCE_OF_FUND IS NOT NULL AND TRIM(t.SOURCE_OF_FUND) != ''
                 THEN CONCAT('Source of funds: ', t.SOURCE_OF_FUND) END,
            CASE WHEN t.SENDING_REASON IS NOT NULL AND TRIM(t.SENDING_REASON) != ''
                 THEN CONCAT('Reason ID: ', t.SENDING_REASON) END,
            CASE WHEN t.TRANSACTION_FLAG IS NOT NULL AND TRIM(t.TRANSACTION_FLAG) != ''
                 THEN CONCAT('Flag: ', t.TRANSACTION_FLAG) END,
            CASE WHEN t.COMPLAINCE_REASON IS NOT NULL AND TRIM(t.COMPLAINCE_REASON) != ''
                 THEN CONCAT('Compliance: ', t.COMPLAINCE_REASON) END,
            'Migrated from layla_old'
        )),
        -- Failure reason
        CASE
            WHEN LOWER(TRIM(COALESCE(t.STATUS, ''))) = 'cancelled'
                 AND TRIM(COALESCE(t.CANCEL_REASON, 'NA')) != 'NA'
                 THEN TRIM(t.CANCEL_REASON)
            WHEN t.ABORTED_REASON IS NOT NULL AND TRIM(t.ABORTED_REASON) != ''
                 THEN t.ABORTED_REASON
            ELSE NULL
        END,
        0,   -- is_recurring
        0,   -- risk_score
        0,   -- version (required NOT NULL)
        CASE WHEN t.CREATED_ON IS NULL OR UNIX_TIMESTAMP(t.CREATED_ON) = 0 THEN NOW() ELSE t.CREATED_ON END,
        CASE WHEN t.LAST_MODIFIED_ON IS NULL OR UNIX_TIMESTAMP(t.LAST_MODIFIED_ON) = 0 THEN NOW() ELSE t.LAST_MODIFIED_ON END
    FROM layla_old.`transaction` t
    JOIN  remitz.migration_user_map m ON m.old_user_id = t.SENDING_CUST_USER_ID
    LEFT JOIN layla_old.transaction_receiving_branch tr
           ON tr.TRANSACTION_ID = t.TRANSACTION_ID
    LEFT JOIN remitz.migration_beneficiary_map bm
           ON bm.old_beneficiary_id = tr.BENEFICIARY_ID
    WHERE NOT EXISTS (
        SELECT 1 FROM remitz.transactions nt
        WHERE nt.reference_number = t.TRANSACTION_ID
    );

    SET v_inserted  = ROW_COUNT();
    SET v_old_count = (SELECT COUNT(*) FROM layla_old.`transaction` t
                       WHERE EXISTS (
                           SELECT 1 FROM remitz.migration_user_map m
                           WHERE m.old_user_id = t.SENDING_CUST_USER_ID
                       ));
    SET v_skipped   = (SELECT COUNT(*) FROM layla_old.`transaction`) - v_old_count;

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'transactions', 'OK',
            CONCAT('Inserted: ', v_inserted,
                   ' / Eligible: ', v_old_count,
                   ' / Skipped (no user match): ', v_skipped));

    REPLACE INTO remitz.migration_summary (phase, attempted, succeeded, skipped, failed)
    VALUES (v_phase, (SELECT COUNT(*) FROM layla_old.`transaction`),
            v_inserted, v_skipped, 0);

    -- =========================================================================
    -- PHASE 8 — transaction_status_history (initial status snapshot)
    -- =========================================================================
    SET v_phase = 'PHASE_8_TXN_STATUS_HISTORY';

    INSERT INTO remitz.transaction_status_history (
        transaction_id,
        from_status,
        to_status,
        actor_type,
        reason,
        created_at
    )
    SELECT
        nt.id,
        NULL,  -- no prior status (initial import)
        nt.status,
        'SYSTEM',
        'Migrated initial status from layla_old',
        nt.created_at
    FROM remitz.transactions nt
    WHERE nt.reference_number LIKE 'TXN%'
      AND NOT EXISTS (
          SELECT 1 FROM remitz.transaction_status_history tsh
          WHERE tsh.transaction_id = nt.id
      );

    SET v_inserted = ROW_COUNT();

    INSERT INTO remitz.migration_log (phase, table_name, status, message)
    VALUES (v_phase, 'transaction_status_history', 'OK',
            CONCAT('Inserted: ', v_inserted, ' status history rows'));

    -- =========================================================================
    -- VALIDATION REPORT (runs while transaction is still open, so data is visible)
    -- =========================================================================
    SET v_phase = 'VALIDATION';

    SELECT
        'VALIDATION REPORT' AS `Section`,
        CONCAT('dry_run=', dry_run, ' | ', NOW()) AS `Run Info`,
        '' AS `Old DB`,
        '' AS `New DB`,
        '' AS `Status`;

    SELECT
        chk.`#`         AS `#`,
        chk.metric      AS `Metric`,
        chk.old_val     AS `Old Count`,
        chk.new_val     AS `New Count`,
        CASE
            WHEN chk.old_val = chk.new_val   THEN '✓ MATCH'
            WHEN chk.new_val >= chk.old_val  THEN '✓ OK (new >= old)'
            ELSE '⚠ CHECK'
        END AS `Status`
    FROM (
        SELECT 1 AS `#`, 'remit_one records' AS metric,
            (SELECT COUNT(*) FROM layla_old.remit_one) AS old_val,
            (SELECT COUNT(*) FROM remitz.remit_one) AS new_val
        UNION ALL
        SELECT 2, 'users migrated (excl. seed)',
            (SELECT COUNT(*) FROM layla_old.`user` WHERE USER_EMAIL IS NOT NULL AND TRIM(USER_EMAIL)!=''),
            (SELECT COUNT(*) FROM remitz.users WHERE id > 6)
        UNION ALL
        SELECT 3, 'user map entries',
            (SELECT COUNT(*) FROM layla_old.`user` WHERE USER_EMAIL IS NOT NULL AND TRIM(USER_EMAIL)!=''),
            (SELECT COUNT(*) FROM remitz.migration_user_map)
        UNION ALL
        SELECT 4, 'wallets created',
            (SELECT COUNT(*) FROM remitz.migration_user_map),
            (SELECT COUNT(*) FROM remitz.wallets WHERE user_id IN
                (SELECT new_user_id FROM remitz.migration_user_map))
        UNION ALL
        SELECT 5, 'users with address updated',
            (SELECT COUNT(DISTINCT USER_ID) FROM layla_old.user_addr),
            (SELECT COUNT(*) FROM remitz.users WHERE id > 6 AND address_line_1 IS NOT NULL)
        UNION ALL
        SELECT 6, 'kyc_documents (identity front)',
            (SELECT COUNT(*) FROM layla_old.user_identity),
            (SELECT COUNT(*) FROM remitz.kyc_documents WHERE user_id > 6 AND document_type != 'PROOF_OF_ADDRESS' AND document_number NOT LIKE '%_BACK')
        UNION ALL
        SELECT 7, 'kyc_verifications (identity)',
            (SELECT COUNT(DISTINCT USER_ID) FROM layla_old.user_identity),
            (SELECT COUNT(*) FROM remitz.kyc_verifications WHERE user_id > 6)
        UNION ALL
        SELECT 8, 'users TIER_2 (fully verified)',
            (SELECT COUNT(DISTINCT USER_ID) FROM layla_old.user_identity
             WHERE LOWER(ID_VERIFIED_STATUS) IN ('full','verified')),
            (SELECT COUNT(*) FROM remitz.users WHERE kyc_tier = 'TIER_2' AND id > 6)
        UNION ALL
        SELECT 9, 'beneficiaries migrated',
            (SELECT COUNT(*) FROM layla_old.beneficiary WHERE SENDING_CUST_USER_ID
                IN (SELECT old_user_id FROM remitz.migration_user_map)),
            (SELECT COUNT(*) FROM remitz.beneficiaries)
        UNION ALL
        SELECT 10, 'transactions migrated',
            (SELECT COUNT(*) FROM layla_old.`transaction` WHERE SENDING_CUST_USER_ID
                IN (SELECT old_user_id FROM remitz.migration_user_map)),
            (SELECT COUNT(*) FROM remitz.transactions WHERE reference_number LIKE 'TXN%')
        UNION ALL
        SELECT 11, 'PAID transactions (financial check)',
            (SELECT COUNT(*) FROM layla_old.`transaction` WHERE LOWER(STATUS) = 'paid'),
            (SELECT COUNT(*) FROM remitz.transactions WHERE status = 'PAID'
                AND reference_number LIKE 'TXN%')
        UNION ALL
        SELECT 12, 'GBP send_amount sum (PAID)',
            ROUND(COALESCE((SELECT SUM(SENDING_AMOUNT) FROM layla_old.`transaction`
                            WHERE LOWER(STATUS) = 'paid'), 0), 2),
            ROUND(COALESCE((SELECT SUM(send_amount) FROM remitz.transactions
                            WHERE status = 'PAID' AND reference_number LIKE 'TXN%'), 0), 2)
    ) chk;

    -- Orphan check
    SELECT
        'ORPHAN TRANSACTIONS' AS check_name,
        COUNT(*) AS count,
        CASE WHEN COUNT(*) = 0 THEN '✓ NONE' ELSE '⚠ ORPHANS FOUND' END AS status
    FROM remitz.transactions t
    LEFT JOIN remitz.users u ON t.sender_id = u.id
    WHERE u.id IS NULL
      AND t.reference_number LIKE 'TXN%';

    -- Duplicate reference_number check
    SELECT
        'DUPLICATE REFERENCE NUMBERS' AS check_name,
        COUNT(*) AS count,
        CASE WHEN COUNT(*) = 0 THEN '✓ NONE' ELSE '⚠ DUPLICATES FOUND' END AS status
    FROM (
        SELECT reference_number, COUNT(*) AS cnt
        FROM remitz.transactions
        GROUP BY reference_number
        HAVING cnt > 1
    ) dup;

    -- KYC tier breakdown
    SELECT
        'KYC TIER BREAKDOWN' AS report,
        kyc_tier,
        COUNT(*) AS user_count
    FROM remitz.users
    WHERE id > 6
    GROUP BY kyc_tier
    ORDER BY kyc_tier;

    -- Transaction status breakdown
    SELECT
        'TRANSACTION STATUS BREAKDOWN' AS report,
        status,
        COUNT(*) AS count,
        ROUND(SUM(send_amount), 2) AS total_gbp
    FROM remitz.transactions
    WHERE reference_number LIKE 'TXN%'
    GROUP BY status
    ORDER BY count DESC;

    -- Migration summary
    SELECT
        'MIGRATION SUMMARY' AS report,
        phase,
        attempted,
        succeeded,
        skipped,
        failed
    FROM remitz.migration_summary
    ORDER BY run_at;

    -- =========================================================================
    -- COMMIT or ROLLBACK based on dry_run flag
    -- =========================================================================
    IF dry_run = 1 THEN
        ROLLBACK;
        SELECT CONCAT(
            CHAR(10),
            '════════════════════════════════════════════════════════════',
            CHAR(10),
            '  DRY RUN COMPLETE — ALL CHANGES ROLLED BACK               ',
            CHAR(10),
            '  Review the validation report above.                       ',
            CHAR(10),
            '  When ready to commit, run:  CALL run_migration(0);        ',
            CHAR(10),
            '════════════════════════════════════════════════════════════'
        ) AS `DRY RUN RESULT`;
    ELSE
        COMMIT;
        -- Re-enable FK checks after commit
        SET SESSION foreign_key_checks = 1;
        INSERT INTO remitz.migration_log (phase, table_name, status, message)
        VALUES ('COMMITTED', 'ALL', 'OK',
                CONCAT('Migration committed at ', NOW()));
        SELECT CONCAT(
            CHAR(10),
            '════════════════════════════════════════════════════════════',
            CHAR(10),
            '  MIGRATION COMPLETE — ALL DATA COMMITTED                   ',
            CHAR(10),
            '  Duration: ', TIMESTAMPDIFF(SECOND, v_start_time, NOW()), ' seconds    ',
            CHAR(10),
            '════════════════════════════════════════════════════════════'
        ) AS `LIVE MIGRATION RESULT`;
    END IF;

    SET SESSION foreign_key_checks = 1;

END$$

DELIMITER ;

SELECT 'Stored procedure run_migration() created successfully.' AS status;
SELECT 'To run DRY RUN:  CALL run_migration(1);' AS next_step
UNION ALL
SELECT 'To run LIVE:     CALL run_migration(0);';

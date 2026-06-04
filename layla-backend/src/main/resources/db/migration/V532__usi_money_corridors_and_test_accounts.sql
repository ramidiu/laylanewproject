-- ============================================================================
-- V532: Activate USI Money corridors + seed sandbox test accounts
-- ----------------------------------------------------------------------------
-- Activates the 6 USI Money payout corridors (Uganda, Turkey, Egypt, Qatar,
-- Saudi Arabia, UAE), turns on the right delivery methods per country, adds
-- "USI Money" as a payout partner, maps corridors to it, and seeds the
-- sandbox account details (so PayIn partners can map without re-typing).
-- ============================================================================

-- 1) Activate payout_types ------------------------------------------------
UPDATE payout_types SET is_active = 1
WHERE (country_code, payout_type) IN (
    ('UG', 'BANK_TRANSFER'),
    ('UG', 'MOBILE_MONEY'),
    ('TR', 'BANK_TRANSFER'),
    ('EG', 'BANK_TRANSFER'),
    ('QA', 'CASH_COLLECTION'),
    ('SA', 'BANK_TRANSFER'),
    ('AE', 'BANK_TRANSFER')
);

-- Qatar — cash-only per USI Money spec; explicitly deactivate bank transfer if present.
UPDATE payout_types SET is_active = 0
WHERE country_code = 'QA' AND payout_type = 'BANK_TRANSFER';

-- Ensure rows exist (insert any missing combinations idempotently)
INSERT INTO payout_types (country_name, country_code, currency, payout_type, is_active)
SELECT * FROM (SELECT 'Qatar' AS n, 'QA' AS c, 'QAR' AS cu, 'CASH_COLLECTION' AS pt, 1 AS a) AS t
WHERE NOT EXISTS (
    SELECT 1 FROM payout_types WHERE country_code = 'QA' AND payout_type = 'CASH_COLLECTION'
);

INSERT INTO payout_types (country_name, country_code, currency, payout_type, is_active)
SELECT * FROM (SELECT 'United Arab Emirates' AS n, 'AE' AS c, 'AED' AS cu, 'BANK_TRANSFER' AS pt, 1 AS a) AS t
WHERE NOT EXISTS (
    SELECT 1 FROM payout_types WHERE country_code = 'AE' AND payout_type = 'BANK_TRANSFER'
);

-- 2) Register USI Money as a payout partner ------------------------------
INSERT INTO payout_partners (partner_name, contact_email, contact_phone, is_active)
SELECT 'USI Money', 'support@usimoney.com', '+44-000-000-0000', 1
WHERE NOT EXISTS (SELECT 1 FROM payout_partners WHERE partner_name = 'USI Money');

-- 3) Map corridors GBP→{UGX,TRY,EGP,QAR,SAR,AED} to USI Money --------------
INSERT INTO corridor_partner_mappings (from_currency, to_currency, partner_id, is_active)
SELECT 'GBP', t.cur,
       (SELECT id FROM payout_partners WHERE partner_name = 'USI Money'),
       1
FROM (
    SELECT 'UGX' AS cur UNION ALL
    SELECT 'TRY'        UNION ALL
    SELECT 'EGP'        UNION ALL
    SELECT 'QAR'        UNION ALL
    SELECT 'SAR'        UNION ALL
    SELECT 'AED'
) t
WHERE NOT EXISTS (
    SELECT 1 FROM corridor_partner_mappings cpm
    WHERE cpm.from_currency = 'GBP'
      AND cpm.to_currency = t.cur
      AND cpm.partner_id = (SELECT id FROM payout_partners WHERE partner_name = 'USI Money')
);

-- 4) Sandbox/test account templates table -------------------------------
-- Holds the canned per-country payout-account fixtures supplied by USI Money,
-- so PayIn partners can map a beneficiary to a known-good test record in one
-- click instead of re-typing IBAN / account / mobile / collection-point details.
CREATE TABLE IF NOT EXISTS usi_test_accounts (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    country_code      VARCHAR(3)  NOT NULL,
    country_name      VARCHAR(100) NOT NULL,
    payout_type       VARCHAR(30) NOT NULL,
    bank_name         VARCHAR(150) NULL,
    bank_branch       VARCHAR(150) NULL,
    bank_branch_state VARCHAR(100) NULL,
    bank_branch_city  VARCHAR(100) NULL,
    account_number    VARCHAR(64)  NULL,
    iban              VARCHAR(64)  NULL,
    mobile_network    VARCHAR(60)  NULL,
    mobile_number     VARCHAR(32)  NULL,
    collection_point  VARCHAR(150) NULL,
    notes             VARCHAR(255) NULL,
    is_active         TINYINT(1)   DEFAULT 1,
    created_at        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_usi_test_country (country_code),
    INDEX idx_usi_test_payout  (payout_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5) Seed the per-country sandbox accounts -----------------------------
INSERT INTO usi_test_accounts
    (country_code, country_name, payout_type,
     bank_name, bank_branch, bank_branch_state, bank_branch_city,
     account_number, iban, mobile_network, mobile_number, collection_point, notes)
VALUES
    -- Uganda — Bank Deposit (2 banks)
    ('UG', 'Uganda', 'BANK_TRANSFER',
     'Bank of Uganda', 'Bank of Uganda', 'Any Branch', 'Any Branch',
     '1234567890', NULL, NULL, NULL, NULL,
     'Sandbox: Bank of Uganda — any branch'),

    ('UG', 'Uganda', 'BANK_TRANSFER',
     'ECOBANK', 'ECOBANK', 'Any Branch', 'Any Branch',
     '1234567890', NULL, NULL, NULL, NULL,
     'Sandbox: ECOBANK Uganda — any branch'),

    -- Uganda — Mobile Money
    ('UG', 'Uganda', 'MOBILE_MONEY',
     NULL, NULL, NULL, NULL,
     NULL, NULL, 'MTN Mobile Money', '+256781234567', NULL,
     'Sandbox: MTN Mobile Money'),

    -- Turkey — Garanti Bank (IBAN required)
    ('TR', 'Turkey', 'BANK_TRANSFER',
     'Garanti Bank', 'Garanti Bank', 'Any Branch', 'Any Branch',
     'TR330006100519786457841326', 'TR330006100519786457841326',
     NULL, NULL, NULL,
     'Sandbox: Garanti Bank — IBAN test account'),

    -- Egypt — Central Bank of Egypt (no IBAN)
    ('EG', 'Egypt', 'BANK_TRANSFER',
     'Central Bank of Egypt', 'Central Bank of Egypt', 'Any Branch', 'Any Branch',
     '1234567890', NULL, NULL, NULL, NULL,
     'Sandbox: Central Bank of Egypt'),

    -- Qatar — Cash collection only
    ('QA', 'Qatar', 'CASH_COLLECTION',
     NULL, NULL, NULL, NULL,
     NULL, NULL, NULL, NULL, 'Cash Payout Anywhere',
     'Sandbox: Cash collection — partner-mapped list at go-live'),

    -- Saudi Arabia — Saudi National Bank (IBAN)
    ('SA', 'Saudi Arabia', 'BANK_TRANSFER',
     'Saudi National Bank', 'Saudi National Bank', 'Any Branch', 'Any Branch',
     'SA0380000000608010167519', 'SA0380000000608010167519',
     NULL, NULL, NULL,
     'Sandbox: Saudi National Bank — IBAN test account'),

    -- UAE — First Abu Dhabi Bank (IBAN)
    ('AE', 'United Arab Emirates', 'BANK_TRANSFER',
     'First Abu Dhabi Bank', 'First Abu Dhabi Bank', 'Any Branch', 'Any Branch',
     'AE070331234567890123456', 'AE070331234567890123456',
     NULL, NULL, NULL,
     'Sandbox: First Abu Dhabi Bank — IBAN test account');

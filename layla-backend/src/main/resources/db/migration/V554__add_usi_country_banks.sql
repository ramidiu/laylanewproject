-- ============================================================================
-- V554: Add the major banks for every USI Money payout corridor country
--       (Uganda, Turkey, Egypt, Qatar, Saudi Arabia, UAE) so the customer
--       bank picker lists the real banks — USI matches payouts by bank NAME
--       (prod USI is LIVE), so names are the official English bank names.
--
--       Also adds the missing country_bank_config row for UAE (AE) — the
--       AED corridor was active but had no bank config and no banks.
--
--       Idempotent: each bank is inserted only if (country_code, bank_name)
--       is not already present, so this safely coexists with V202/V528/
--       V535/V537/V539 and can be re-run without duplicates.
-- ============================================================================

-- --- UAE country bank config (was missing) ----------------------------------
INSERT INTO country_bank_config
    (country_code, country_name, currency, identifier_name, identifier_label, identifier_format, identifier_length, auto_lookup)
SELECT 'AE', 'United Arab Emirates', 'AED', 'IBAN', 'IBAN', 'AEXXXXXXXXXXXXXXXXXXXXX', 23, FALSE
WHERE NOT EXISTS (SELECT 1 FROM country_bank_config WHERE country_code = 'AE');

-- --- United Arab Emirates (AE) ----------------------------------------------
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT nb.cc, nb.bn, nb.bi, nb.addr, nb.br, nb.ci, 1
FROM (
    SELECT 'AE' cc, 'First Abu Dhabi Bank (FAB)'            bn, 'NBADAEAA' bi, 'Abu Dhabi' addr, 'Main Branch' br, 'Abu Dhabi' ci
    UNION ALL SELECT 'AE','Emirates NBD',                       'EBILAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','Abu Dhabi Commercial Bank (ADCB)',   'ADCBAEAA','Abu Dhabi','Main Branch','Abu Dhabi'
    UNION ALL SELECT 'AE','Abu Dhabi Islamic Bank (ADIB)',      'ABDIAEAD','Abu Dhabi','Main Branch','Abu Dhabi'
    UNION ALL SELECT 'AE','Dubai Islamic Bank',                 'DUIBAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','Mashreq Bank',                       'BOMLAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','Emirates Islamic Bank',              'MEBLAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','Commercial Bank of Dubai',           'CBDUAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','RAKBANK (National Bank of Ras Al Khaimah)','NRAKAEAK','Ras Al Khaimah','Main Branch','Ras Al Khaimah'
    UNION ALL SELECT 'AE','Sharjah Islamic Bank',               'NBSHAEAS','Sharjah','Main Branch','Sharjah'
    UNION ALL SELECT 'AE','National Bank of Fujairah',          'NBFUAEAF','Fujairah','Main Branch','Fujairah'
    UNION ALL SELECT 'AE','Bank of Sharjah',                    'BOSHAEAX','Sharjah','Main Branch','Sharjah'
    UNION ALL SELECT 'AE','Ajman Bank',                         'AJMNAEAJ','Ajman','Main Branch','Ajman'
    UNION ALL SELECT 'AE','United Arab Bank',                   'UARBAEAA','Sharjah','Main Branch','Sharjah'
    UNION ALL SELECT 'AE','HSBC Bank Middle East',              'BBMEAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','Standard Chartered Bank UAE',        'SCBLAEAD','Dubai','Main Branch','Dubai'
    UNION ALL SELECT 'AE','Citibank UAE',                       'CITIAEAD','Dubai','Main Branch','Dubai'
) nb
WHERE NOT EXISTS (SELECT 1 FROM bank_database b WHERE b.country_code = nb.cc AND b.bank_name = nb.bn);

-- --- Egypt (EG) -------------------------------------------------------------
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT nb.cc, nb.bn, nb.bi, nb.addr, nb.br, nb.ci, 1
FROM (
    SELECT 'EG' cc, 'Abu Dhabi Islamic Bank - Egypt'        bn, 'ABDIEGCA' bi, 'Cairo' addr, 'Main Branch' br, 'Cairo' ci
    UNION ALL SELECT 'EG','Bank of Alexandria (AlexBank)',     'ALEXEGCX','Alexandria','Main Branch','Alexandria'
    UNION ALL SELECT 'EG','Arab African International Bank',   'ARAIEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Banque du Caire',                   'BCAIEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Faisal Islamic Bank of Egypt',      'FIEGEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Housing and Development Bank',      'HDBKEGCA','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Credit Agricole Egypt',             'AGRIEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Emirates NBD Egypt',                'EBILEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Al Baraka Bank Egypt',              'ABRKEGCA','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','HSBC Bank Egypt',                   'EBBKEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Arab Bank - Egypt',                 'ARABEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','National Bank of Kuwait - Egypt (NBK)','NBKAEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Suez Canal Bank',                   'SUCAEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Attijariwafa Bank Egypt',           'BARCEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Export Development Bank of Egypt',   'EXDEEGCX','Cairo','Main Branch','Cairo'
    UNION ALL SELECT 'EG','Egyptian Gulf Bank',                'EGGBEGCX','Cairo','Main Branch','Cairo'
) nb
WHERE NOT EXISTS (SELECT 1 FROM bank_database b WHERE b.country_code = nb.cc AND b.bank_name = nb.bn);

-- --- Saudi Arabia (SA) ------------------------------------------------------
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT nb.cc, nb.bn, nb.bi, nb.addr, nb.br, nb.ci, 1
FROM (
    SELECT 'SA' cc, 'Saudi National Bank (SNB)'             bn, 'NCBKSAJE' bi, 'Riyadh' addr, 'Main Branch' br, 'Riyadh' ci
    UNION ALL SELECT 'SA','Saudi Awwal Bank (SAB)',           'AAALSARI','Riyadh','Main Branch','Riyadh'
    UNION ALL SELECT 'SA','Banque Saudi Fransi',              'BSFRSARI','Riyadh','Main Branch','Riyadh'
    UNION ALL SELECT 'SA','Arab National Bank (ANB)',         'ARNBSARI','Riyadh','Main Branch','Riyadh'
    UNION ALL SELECT 'SA','Bank Albilad',                     'ALBISARI','Riyadh','Main Branch','Riyadh'
    UNION ALL SELECT 'SA','Alinma Bank',                      'INMASARI','Riyadh','Main Branch','Riyadh'
    UNION ALL SELECT 'SA','Bank Aljazira',                    'BJAZSAJE','Jeddah','Main Branch','Jeddah'
    UNION ALL SELECT 'SA','The Saudi Investment Bank (SAIB)', 'SIBCSARI','Riyadh','Main Branch','Riyadh'
    UNION ALL SELECT 'SA','Gulf International Bank Saudi Arabia','GINLSARI','Riyadh','Main Branch','Riyadh'
) nb
WHERE NOT EXISTS (SELECT 1 FROM bank_database b WHERE b.country_code = nb.cc AND b.bank_name = nb.bn);

-- --- Qatar (QA) -------------------------------------------------------------
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT nb.cc, nb.bn, nb.bi, nb.addr, nb.br, nb.ci, 1
FROM (
    SELECT 'QA' cc, 'Qatar Islamic Bank (QIB)'              bn, 'QISBQAQA' bi, 'Doha' addr, 'Main Branch' br, 'Doha' ci
    UNION ALL SELECT 'QA','Qatar International Islamic Bank (QIIB)','QIIBQAQA','Doha','Main Branch','Doha'
    UNION ALL SELECT 'QA','Masraf Al Rayan',                  'MAFRQAQA','Doha','Main Branch','Doha'
    UNION ALL SELECT 'QA','Dukhan Bank',                      'BRWAQAQA','Doha','Main Branch','Doha'
    UNION ALL SELECT 'QA','Ahli Bank Qatar',                  'ABQQQAQA','Doha','Main Branch','Doha'
    UNION ALL SELECT 'QA','Qatar Development Bank',            'QDBKQAQA','Doha','Main Branch','Doha'
) nb
WHERE NOT EXISTS (SELECT 1 FROM bank_database b WHERE b.country_code = nb.cc AND b.bank_name = nb.bn);

-- --- Turkey (TR) ------------------------------------------------------------
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT nb.cc, nb.bn, nb.bi, nb.addr, nb.br, nb.ci, 1
FROM (
    SELECT 'TR' cc, 'Yapi Kredi'                            bn, 'YAPITRIS' bi, 'Istanbul' addr, 'Main Branch' br, 'Istanbul' ci
    UNION ALL SELECT 'TR','Halkbank',                        'TRHBTR2A','Ankara','Main Branch','Ankara'
    UNION ALL SELECT 'TR','VakifBank',                       'TVBATR2A','Ankara','Main Branch','Ankara'
    UNION ALL SELECT 'TR','Denizbank',                       'DENITRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','QNB Finansbank',                  'FNNBTRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','Turk Ekonomi Bankasi (TEB)',      'TEBUTRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','ING Bank Turkey',                 'INGBTRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','Kuveyt Turk',                     'KTEFTRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','Turkiye Finans',                  'AFKBTRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','Albaraka Turk',                   'BTFHTRIS','Istanbul','Main Branch','Istanbul'
    UNION ALL SELECT 'TR','Sekerbank',                       'SEKETR2A','Istanbul','Main Branch','Istanbul'
) nb
WHERE NOT EXISTS (SELECT 1 FROM bank_database b WHERE b.country_code = nb.cc AND b.bank_name = nb.bn);

-- --- Uganda (UG) ------------------------------------------------------------
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city, is_active)
SELECT nb.cc, nb.bn, nb.bi, nb.addr, nb.br, nb.ci, 1
FROM (
    SELECT 'UG' cc, 'Absa Bank Uganda'                      bn, 'BARCUGKX' bi, 'Kampala' addr, 'Main Branch' br, 'Kampala' ci
    UNION ALL SELECT 'UG','Standard Chartered Bank Uganda',  'SCBLUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','Bank of Baroda Uganda',           'BARBUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','Housing Finance Bank',            'HFINUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','KCB Bank Uganda',                 'KCBLUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','Diamond Trust Bank Uganda',       'DTKEUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','Citibank Uganda',                 'CITIUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','United Bank for Africa (UBA) Uganda','UNAFUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','Bank of Africa Uganda',           'AFRIUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','PostBank Uganda',                 'UGPBUGKA','Kampala','Main Branch','Kampala'
    UNION ALL SELECT 'UG','Cairo Bank Uganda',               'CAIRUGKA','Kampala','Main Branch','Kampala'
) nb
WHERE NOT EXISTS (SELECT 1 FROM bank_database b WHERE b.country_code = nb.cc AND b.bank_name = nb.bn);

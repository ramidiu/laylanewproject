-- Settlement rates
INSERT IGNORE INTO settlement_rates (currency, rate_to_usd) VALUES
('SDG', 0.001650),  -- Sudanese Pound
('TRY', 0.031200),  -- Turkish Lira
('EGP', 0.020500),  -- Egyptian Pound
('SAR', 0.266700),  -- Saudi Riyal
('QAR', 0.274700),  -- Qatari Riyal
('UGX', 0.000268);  -- Ugandan Shilling

-- Country bank configurations
INSERT IGNORE INTO country_bank_config (country_code, country_name, currency, identifier_name, identifier_label, identifier_format, identifier_length, auto_lookup) VALUES
('SD', 'Sudan',        'SDG', 'SWIFT', 'SWIFT/BIC Code', 'XXXXXXXX',   8, FALSE),
('TR', 'Turkey',       'TRY', 'IBAN',  'IBAN',           'TRXXXXXXXXXXXXXXXXXXXXXXXX', 26, FALSE),
('EG', 'Egypt',        'EGP', 'SWIFT', 'SWIFT/BIC Code', 'XXXXXXXX',   8, FALSE),
('SA', 'Saudi Arabia', 'SAR', 'IBAN',  'IBAN',           'SAXXXXXXXXXXXXXXXXXXXXXX', 24, FALSE),
('QA', 'Qatar',        'QAR', 'IBAN',  'IBAN',           'QAXXXXXXXXXXXXXXXXXXXXXXXX', 29, FALSE),
('UG', 'Uganda',       'UGX', 'SWIFT', 'SWIFT/BIC Code', 'XXXXXXXX',   8, FALSE);

-- Bank database – Sudan
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('SD', 'Bank of Khartoum',       'BKKHSDKH', 'Al-Amarat, Block 3',         'Khartoum Main',   'Khartoum'),
('SD', 'Omdurman National Bank', 'OMNBSDKH', 'Al-Mawrada Street',          'Omdurman Branch', 'Omdurman'),
('SD', 'Agricultural Bank',      'AGRBSDKH', 'El-Gamhoria Avenue',         'Khartoum Branch', 'Khartoum');

-- Bank database – Turkey
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('TR', 'Ziraat Bankasi',  'TCZBTR2A', 'Atatürk Bulvarı No:8',    'Ankara Main',    'Ankara'),
('TR', 'Garanti BBVA',    'TGBATRIS', 'Levent Nispetiye Cd.',    'Istanbul Main',  'Istanbul'),
('TR', 'İş Bankası',      'ISBKTRIS', 'Atatürk Mah. E-5 Yol',   'Istanbul Branch','Istanbul'),
('TR', 'Akbank',          'AKBKTRIS', 'Sabancı Center, Levent',  'Levent Branch',  'Istanbul');

-- Bank database – Egypt
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('EG', 'National Bank of Egypt', 'NBEGEGCX', '1187 Corniche El Nil',         'Cairo Main',       'Cairo'),
('EG', 'Banque Misr',            'BMISEGCX', '151 Mohamed Farid St',         'Cairo Branch',     'Cairo'),
('EG', 'Commercial International Bank', 'CIBEEGCX', 'Smart Village, Km 28', 'Alexandria Branch','Alexandria'),
('EG', 'QNB Al Ahli',            'QNBAEGCX', '21 Mahmoud Bassiouni St',     'Cairo Branch',     'Cairo');

-- Bank database – Saudi Arabia
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('SA', 'Al Rajhi Bank',            'RJHISARI', 'King Fahd Rd, Al Olaya',        'Riyadh Main',  'Riyadh'),
('SA', 'National Commercial Bank', 'NCBKSAJE', 'NCB Head Office, King Abdulaziz','Jeddah Main',  'Jeddah'),
('SA', 'Riyad Bank',               'RIBLSARI', 'Al Bathaa, Riyadh',             'Riyadh Branch','Riyadh'),
('SA', 'Saudi British Bank (SABB)','SABBSARI', 'Al Takhassousi St',             'Riyadh Branch','Riyadh');

-- Bank database – Qatar
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('QA', 'Qatar National Bank',     'QNBAQAQA', 'Al Corniche Street',      'Doha Main',    'Doha'),
('QA', 'Commercial Bank Qatar',   'CBQAQAQX', 'Grand Hamad Avenue',      'Doha Branch',  'Doha'),
('QA', 'Doha Bank',               'DOHBQAQA', 'Al Matar St, C Ring Rd',  'Doha Branch',  'Doha');

-- Bank database – Uganda
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('UG', 'Stanbic Bank Uganda',   'SBICUGKX', 'Plot 45 Kampala Rd',        'Kampala Main',   'Kampala'),
('UG', 'Centenary Bank',        'CEEBUGKA', 'Plot 7 Entebbe Rd',         'Kampala Branch', 'Kampala'),
('UG', 'DFCU Bank',             'DFCUUGKA', 'Plot 26 Kyadondo Rd',       'Nakasero Branch','Kampala'),
('UG', 'Equity Bank Uganda',    'EQBLUGKA', 'Plot 10 Buganda Rd',        'Kampala Branch', 'Kampala');

-- Payout types
INSERT IGNORE INTO payout_types (country_name, country_code, currency, payout_type, is_active) VALUES
-- Sudan
('Sudan', 'SD', 'SDG', 'BANK_TRANSFER',   TRUE),
('Sudan', 'SD', 'SDG', 'CASH_COLLECTION', TRUE),
-- Turkey
('Turkey', 'TR', 'TRY', 'BANK_TRANSFER',  TRUE),
-- Egypt
('Egypt', 'EG', 'EGP', 'BANK_TRANSFER',   TRUE),
('Egypt', 'EG', 'EGP', 'MOBILE_MONEY',    TRUE),
('Egypt', 'EG', 'EGP', 'CASH_COLLECTION', TRUE),
-- Saudi Arabia
('Saudi Arabia', 'SA', 'SAR', 'BANK_TRANSFER', TRUE),
-- Qatar
('Qatar', 'QA', 'QAR', 'BANK_TRANSFER',   TRUE),
-- Uganda
('Uganda', 'UG', 'UGX', 'BANK_TRANSFER',  TRUE),
('Uganda', 'UG', 'UGX', 'MOBILE_MONEY',   TRUE),
('Uganda', 'UG', 'UGX', 'CASH_COLLECTION',TRUE);

-- Mobile money services
INSERT IGNORE INTO mobile_money_services (country_code, country_name, service_name) VALUES
('EG', 'Egypt',  'Vodafone Cash'),
('EG', 'Egypt',  'Orange Money'),
('EG', 'Egypt',  'Etisalat Cash'),
('UG', 'Uganda', 'MTN Mobile Money'),
('UG', 'Uganda', 'Airtel Money');

-- Cash collection points
INSERT INTO cash_collection_points (country_code, country_name, point_name, address, city, contact_number) VALUES
('SD', 'Sudan',        'Bank of Khartoum – Al-Amarat',        'Al-Amarat, Block 3, Khartoum',              'Khartoum',    '+249155000000'),
('SD', 'Sudan',        'Omdurman National Bank – Main',        'Al-Mawrada Street, Omdurman',               'Omdurman',    '+249187000000'),
('EG', 'Egypt',        'National Bank of Egypt – Tahrir',      '1187 Corniche El Nil, Cairo',               'Cairo',       '+20223919000'),
('EG', 'Egypt',        'Banque Misr – Mohandiseen',            '151 Mohamed Farid St, Cairo',               'Cairo',       '+20237490000'),
('UG', 'Uganda',       'Stanbic Bank – Kampala Road',          'Plot 45 Kampala Rd, Kampala',               'Kampala',     '+256312224600'),
('UG', 'Uganda',       'Centenary Bank – Entebbe Road',        'Plot 7 Entebbe Rd, Kampala',                'Kampala',     '+256312212219');

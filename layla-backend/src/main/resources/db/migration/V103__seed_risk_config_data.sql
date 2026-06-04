-- Country risk tiers
INSERT INTO country_risk_tiers (country_code, country_name, risk_tier, risk_points) VALUES
('GB', 'United Kingdom', 'LOW', 0),
('US', 'United States', 'LOW', 0),
('DE', 'Germany', 'LOW', 0),
('AU', 'Australia', 'LOW', 0),
('CA', 'Canada', 'LOW', 0),
('FR', 'France', 'LOW', 0),
('JP', 'Japan', 'LOW', 0),
('SG', 'Singapore', 'LOW', 0),
('AE', 'United Arab Emirates', 'MEDIUM', 3),
('IN', 'India', 'MEDIUM', 3),
('PH', 'Philippines', 'MEDIUM', 3),
('PK', 'Pakistan', 'MEDIUM', 3),
('BD', 'Bangladesh', 'MEDIUM', 3),
('LK', 'Sri Lanka', 'MEDIUM', 3),
('NP', 'Nepal', 'MEDIUM', 3),
('NG', 'Nigeria', 'HIGH', 5),
('KE', 'Kenya', 'MEDIUM', 3),
('GH', 'Ghana', 'MEDIUM', 3),
('ZA', 'South Africa', 'MEDIUM', 3),
('UG', 'Uganda', 'MEDIUM', 3),
('IRN', 'Iran', 'HIGH', 5),
('PRK', 'North Korea', 'HIGH', 5),
('SYR', 'Syria', 'HIGH', 5),
('AFG', 'Afghanistan', 'HIGH', 5),
('YEM', 'Yemen', 'HIGH', 5),
('LBY', 'Libya', 'HIGH', 5),
('SOM', 'Somalia', 'HIGH', 5),
('SDN', 'Sudan', 'HIGH', 5);

-- Transaction limits per risk level
INSERT INTO transaction_limits (risk_level, limit_type, max_amount, max_count, currency) VALUES
('LOW', 'PER_TRANSACTION', 5000.00, 1, 'GBP'),
('LOW', 'DAILY', 10000.00, 10, 'GBP'),
('LOW', 'WEEKLY', 25000.00, 30, 'GBP'),
('LOW', 'MONTHLY', 50000.00, 100, 'GBP'),
('MEDIUM', 'PER_TRANSACTION', 1000.00, 1, 'GBP'),
('MEDIUM', 'DAILY', 2000.00, 5, 'GBP'),
('MEDIUM', 'WEEKLY', 5000.00, 15, 'GBP'),
('MEDIUM', 'MONTHLY', 10000.00, 30, 'GBP'),
('HIGH', 'PER_TRANSACTION', 200.00, 1, 'GBP'),
('HIGH', 'DAILY', 500.00, 2, 'GBP'),
('HIGH', 'WEEKLY', 1000.00, 5, 'GBP'),
('HIGH', 'MONTHLY', 2000.00, 10, 'GBP');

-- KYC document types - Universal address documents
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, has_expiry, has_issue_date, is_active, display_order) VALUES
('ALL', 'Universal', 'ADDRESS', 'Utility Bill (within 3 months)', 1, FALSE, FALSE, TRUE, TRUE, 1),
('ALL', 'Universal', 'ADDRESS', 'Bank Statement (within 3 months)', 1, FALSE, FALSE, TRUE, TRUE, 2),
('ALL', 'Universal', 'ADDRESS', 'Council Tax Bill', 1, FALSE, FALSE, TRUE, TRUE, 3),
('ALL', 'Universal', 'ADDRESS', 'Lease/Rental Agreement', 1, FALSE, FALSE, TRUE, TRUE, 4),
('ALL', 'Universal', 'ADDRESS', 'Government Letter (within 3 months)', 1, FALSE, FALSE, TRUE, TRUE, 5),
('ALL', 'Universal', 'ADDRESS', 'Credit Card Statement (within 3 months)', 1, FALSE, FALSE, TRUE, TRUE, 6);

-- KYC document types - GB (United Kingdom)
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, has_expiry, has_issue_date, is_active, display_order) VALUES
('GB', 'United Kingdom', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 1),
('GB', 'United Kingdom', 'IDENTITY', 'Driving Licence', 2, TRUE, 'Licence Number', TRUE, TRUE, TRUE, 2),
('GB', 'United Kingdom', 'IDENTITY', 'BRP (Biometric Residence Permit)', 2, TRUE, 'BRP Number', TRUE, TRUE, TRUE, 3);

-- KYC document types - IN (India)
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, id_number_format, has_expiry, has_issue_date, is_active, display_order) VALUES
('IN', 'India', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', 'A1234567', TRUE, TRUE, TRUE, 1),
('IN', 'India', 'IDENTITY', 'Aadhaar Card', 2, TRUE, 'Aadhaar Number', '1234 5678 9012', FALSE, FALSE, TRUE, 2),
('IN', 'India', 'IDENTITY', 'PAN Card', 1, TRUE, 'PAN Number', 'ABCDE1234F', FALSE, FALSE, TRUE, 3),
('IN', 'India', 'IDENTITY', 'Voter ID', 2, TRUE, 'Voter ID Number', NULL, FALSE, FALSE, TRUE, 4),
('IN', 'India', 'IDENTITY', 'Driving Licence', 2, TRUE, 'DL Number', NULL, TRUE, TRUE, TRUE, 5);

-- KYC document types - NG (Nigeria)
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, id_number_format, has_expiry, has_issue_date, is_active, display_order) VALUES
('NG', 'Nigeria', 'IDENTITY', 'NIN (National ID)', 1, TRUE, 'NIN Number', '12345678901', FALSE, FALSE, TRUE, 1),
('NG', 'Nigeria', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', NULL, TRUE, TRUE, TRUE, 2),
('NG', 'Nigeria', 'IDENTITY', 'Voter''s Card', 2, TRUE, 'Voter Card Number', NULL, FALSE, FALSE, TRUE, 3),
('NG', 'Nigeria', 'IDENTITY', 'Driver''s License', 2, TRUE, 'License Number', NULL, TRUE, TRUE, TRUE, 4);

-- KYC document types - US (United States)
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, has_expiry, has_issue_date, is_active, display_order) VALUES
('US', 'United States', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 1),
('US', 'United States', 'IDENTITY', 'Driver''s License', 2, TRUE, 'License Number', TRUE, TRUE, TRUE, 2),
('US', 'United States', 'IDENTITY', 'State ID', 2, TRUE, 'ID Number', TRUE, TRUE, TRUE, 3),
('US', 'United States', 'IDENTITY', 'Green Card', 2, TRUE, 'Card Number', TRUE, FALSE, TRUE, 4);

-- KYC document types - PK (Pakistan)
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, id_number_format, has_expiry, has_issue_date, is_active, display_order) VALUES
('PK', 'Pakistan', 'IDENTITY', 'CNIC (National ID)', 2, TRUE, 'CNIC Number', '12345-1234567-1', TRUE, TRUE, TRUE, 1),
('PK', 'Pakistan', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', NULL, TRUE, TRUE, TRUE, 2);

-- KYC document types - AU (Australia)
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, has_expiry, has_issue_date, is_active, display_order) VALUES
('AU', 'Australia', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 1),
('AU', 'Australia', 'IDENTITY', 'Driver''s Licence', 2, TRUE, 'Licence Number', TRUE, TRUE, TRUE, 2),
('AU', 'Australia', 'IDENTITY', 'Medicare Card', 1, TRUE, 'Medicare Number', FALSE, FALSE, TRUE, 3);

-- KYC document types - PH, BD, KE, GH, ZA
INSERT INTO kyc_document_types (country_code, country_name, category, document_name, sides, has_id_number, id_number_label, has_expiry, has_issue_date, is_active, display_order) VALUES
('PH', 'Philippines', 'IDENTITY', 'PhilID (National ID)', 2, TRUE, 'PhilID Number', FALSE, FALSE, TRUE, 1),
('PH', 'Philippines', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 2),
('PH', 'Philippines', 'IDENTITY', 'Driver''s License', 2, TRUE, 'License Number', TRUE, TRUE, TRUE, 3),
('BD', 'Bangladesh', 'IDENTITY', 'NID (National ID)', 2, TRUE, 'NID Number', FALSE, FALSE, TRUE, 1),
('BD', 'Bangladesh', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 2),
('KE', 'Kenya', 'IDENTITY', 'National ID', 2, TRUE, 'ID Number', FALSE, FALSE, TRUE, 1),
('KE', 'Kenya', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 2),
('GH', 'Ghana', 'IDENTITY', 'Ghana Card', 2, TRUE, 'Card Number', FALSE, FALSE, TRUE, 1),
('GH', 'Ghana', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 2),
('ZA', 'South Africa', 'IDENTITY', 'Smart ID Card', 2, TRUE, 'ID Number', FALSE, FALSE, TRUE, 1),
('ZA', 'South Africa', 'IDENTITY', 'Passport', 1, TRUE, 'Passport Number', TRUE, TRUE, TRUE, 2);

-- Relations (beneficiary relationship types)
INSERT INTO relations (relation_name) VALUES
('Spouse'), ('Parent'), ('Child'), ('Sibling'), ('Friend'),
('Business Partner'), ('Employer'), ('Employee'), ('Relative'), ('Other');

-- System configuration
INSERT INTO system_config (config_key, config_value, description) VALUES
('ALLOW_PARTIAL_KYC_TRANSACTIONS', 'true', 'Allow transactions for users without complete KYC (within TIER_0 limits)'),
('MAX_LOGIN_ATTEMPTS', '5', 'Maximum failed login attempts before lockout'),
('LOCKOUT_DURATION_MINUTES', '30', 'Account lockout duration in minutes'),
('OTP_TTL_MINUTES', '10', 'OTP expiration time in minutes'),
('RATE_LOCK_TTL_SECONDS', '60', 'FX rate lock duration in seconds');

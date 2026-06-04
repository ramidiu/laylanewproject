-- Settlement rates (global)
INSERT INTO settlement_rates (currency, rate_to_usd) VALUES
('USD', 1.000000), ('GBP', 1.260000), ('EUR', 1.090000), ('AED', 0.272300),
('AUD', 0.650000), ('INR', 0.011980), ('PHP', 0.017800), ('NGN', 0.000645),
('PKR', 0.003590), ('BDT', 0.009090), ('KES', 0.006510), ('GHS', 0.065800),
('ZAR', 0.054100), ('LKR', 0.003280), ('NPR', 0.007490);

-- Country bank configurations
INSERT INTO country_bank_config (country_code, country_name, currency, identifier_name, identifier_label, identifier_format, identifier_length, auto_lookup) VALUES
('IN', 'India', 'INR', 'IFSC', 'IFSC Code', 'XXXX0XXXXXX', 11, TRUE),
('US', 'United States', 'USD', 'ROUTING', 'Routing Number', 'XXXXXXXXX', 9, TRUE),
('GB', 'United Kingdom', 'GBP', 'SORT_CODE', 'Sort Code', 'XX-XX-XX', 6, TRUE),
('AU', 'Australia', 'AUD', 'BSB', 'BSB Number', 'XXX-XXX', 6, TRUE),
('NG', 'Nigeria', 'NGN', 'BANK_CODE', 'Bank Code', 'XXX', 3, FALSE),
('PK', 'Pakistan', 'PKR', 'BRANCH_CODE', 'Branch Code', 'XXXX', 4, FALSE),
('BD', 'Bangladesh', 'BDT', 'ROUTING', 'Routing Number', 'XXXXXXXXX', 9, FALSE),
('PH', 'Philippines', 'PHP', 'SWIFT', 'SWIFT/BIC Code', 'XXXXXXXX', 8, FALSE),
('KE', 'Kenya', 'KES', 'BANK_CODE', 'Bank Code', 'XX', 2, FALSE),
('GH', 'Ghana', 'GHS', 'BANK_CODE', 'Bank Code', 'XXX', 3, FALSE),
('ZA', 'South Africa', 'ZAR', 'BRANCH_CODE', 'Branch Code', 'XXXXXX', 6, FALSE),
('LK', 'Sri Lanka', 'LKR', 'BRANCH_CODE', 'Branch Code', 'XXX', 3, FALSE),
('NP', 'Nepal', 'NPR', 'SWIFT', 'SWIFT Code', 'XXXXXXXX', 8, FALSE);

-- Bank database - India (IFSC)
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('IN', 'State Bank of India', 'SBIN0001234', 'Dr DN Road, Fort', 'Mumbai Main', 'Mumbai'),
('IN', 'State Bank of India', 'SBIN0002345', 'Connaught Place', 'New Delhi Main', 'New Delhi'),
('IN', 'HDFC Bank', 'HDFC0001234', 'Bandra West', 'Mumbai Branch', 'Mumbai'),
('IN', 'HDFC Bank', 'HDFC0002345', 'MG Road', 'Bangalore Branch', 'Bangalore'),
('IN', 'ICICI Bank', 'ICIC0001234', 'Andheri East', 'Mumbai Branch', 'Mumbai'),
('IN', 'ICICI Bank', 'ICIC0002345', 'T Nagar', 'Chennai Branch', 'Chennai'),
('IN', 'Axis Bank', 'UTIB0001234', 'Janpath', 'Delhi Main', 'New Delhi'),
('IN', 'Punjab National Bank', 'PUNB0001234', 'Parliament Street', 'Delhi Main', 'New Delhi'),
('IN', 'Bank of Baroda', 'BARB0DBAHYD', 'Banjara Hills', 'Hyderabad Branch', 'Hyderabad'),
('IN', 'Kotak Mahindra Bank', 'KKBK0001234', 'Nariman Point', 'Mumbai Branch', 'Mumbai');

-- Bank database - UK (Sort Code)
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('GB', 'Barclays', '200000', 'Canary Wharf', 'London Main', 'London'),
('GB', 'Barclays', '200001', 'Deansgate', 'Manchester Branch', 'Manchester'),
('GB', 'HSBC', '400000', 'Canada Square', 'London Main', 'London'),
('GB', 'HSBC', '400001', 'Colmore Row', 'Birmingham Branch', 'Birmingham'),
('GB', 'Lloyds Banking Group', '300000', 'Gresham Street', 'London Main', 'London'),
('GB', 'NatWest', '600000', 'Bishopsgate', 'London Main', 'London'),
('GB', 'Santander UK', '090000', 'Triton Square', 'London Main', 'London'),
('GB', 'Metro Bank', '230580', 'One Southampton Row', 'London Holborn', 'London');

-- Bank database - US (Routing Number)
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('US', 'JPMorgan Chase', '021000021', '383 Madison Ave', 'New York Main', 'New York'),
('US', 'Bank of America', '026009593', '100 N Tryon St', 'New York Branch', 'New York'),
('US', 'Wells Fargo', '121000248', '420 Montgomery St', 'San Francisco Main', 'San Francisco'),
('US', 'Citibank', '021000089', '388 Greenwich St', 'New York Main', 'New York');

-- Bank database - Nigeria
INSERT INTO bank_database (country_code, bank_name, bank_identifier, bank_address, branch_name, city) VALUES
('NG', 'GTBank', '058', 'Plot 635 Akin Adesola', 'Victoria Island', 'Lagos'),
('NG', 'FirstBank', '011', 'Samuel Asabia House', 'Marina', 'Lagos'),
('NG', 'Access Bank', '044', 'Plot 999c Danmole St', 'Victoria Island', 'Lagos'),
('NG', 'UBA', '033', '57 Marina', 'Lagos Island', 'Lagos'),
('NG', 'Zenith Bank', '057', '84 Ajose Adeogun', 'Victoria Island', 'Lagos');

-- Payout types per country
INSERT INTO payout_types (country_name, country_code, currency, payout_type, is_active) VALUES
('India', 'IN', 'INR', 'BANK_TRANSFER', TRUE),
('India', 'IN', 'INR', 'MOBILE_MONEY', FALSE),
('India', 'IN', 'INR', 'CASH_COLLECTION', FALSE),
('Philippines', 'PH', 'PHP', 'BANK_TRANSFER', TRUE),
('Philippines', 'PH', 'PHP', 'MOBILE_MONEY', TRUE),
('Philippines', 'PH', 'PHP', 'CASH_COLLECTION', TRUE),
('Nigeria', 'NG', 'NGN', 'BANK_TRANSFER', TRUE),
('Nigeria', 'NG', 'NGN', 'MOBILE_MONEY', TRUE),
('Kenya', 'KE', 'KES', 'BANK_TRANSFER', TRUE),
('Kenya', 'KE', 'KES', 'MOBILE_MONEY', TRUE),
('Ghana', 'GH', 'GHS', 'BANK_TRANSFER', TRUE),
('Ghana', 'GH', 'GHS', 'MOBILE_MONEY', TRUE),
('Pakistan', 'PK', 'PKR', 'BANK_TRANSFER', TRUE),
('Pakistan', 'PK', 'PKR', 'MOBILE_MONEY', TRUE),
('Bangladesh', 'BD', 'BDT', 'BANK_TRANSFER', TRUE),
('Bangladesh', 'BD', 'BDT', 'MOBILE_MONEY', TRUE),
('United States', 'US', 'USD', 'BANK_TRANSFER', TRUE),
('United Kingdom', 'GB', 'GBP', 'BANK_TRANSFER', TRUE),
('Australia', 'AU', 'AUD', 'BANK_TRANSFER', TRUE),
('Germany', 'DE', 'EUR', 'BANK_TRANSFER', TRUE),
('United Arab Emirates', 'AE', 'AED', 'BANK_TRANSFER', TRUE),
('South Africa', 'ZA', 'ZAR', 'BANK_TRANSFER', TRUE),
('Sri Lanka', 'LK', 'LKR', 'BANK_TRANSFER', TRUE),
('Nepal', 'NP', 'NPR', 'BANK_TRANSFER', TRUE);

-- Payment methods per country (sending)
INSERT INTO payment_methods (country_name, country_code, currency, payment_method, is_active) VALUES
('United Kingdom', 'GB', 'GBP', 'BANK_TRANSFER', TRUE),
('United Kingdom', 'GB', 'GBP', 'CREDIT_DEBIT_CARD', TRUE),
('United Kingdom', 'GB', 'GBP', 'PAY_WITH_BANK', TRUE),
('United Kingdom', 'GB', 'GBP', 'WALLET', FALSE),
('United States', 'US', 'USD', 'BANK_TRANSFER', TRUE),
('United States', 'US', 'USD', 'CREDIT_DEBIT_CARD', TRUE),
('United States', 'US', 'USD', 'PAY_WITH_BANK', TRUE),
('United Arab Emirates', 'AE', 'AED', 'BANK_TRANSFER', TRUE),
('United Arab Emirates', 'AE', 'AED', 'CREDIT_DEBIT_CARD', TRUE),
('United Arab Emirates', 'AE', 'AED', 'WALLET', TRUE),
('Australia', 'AU', 'AUD', 'BANK_TRANSFER', TRUE),
('Australia', 'AU', 'AUD', 'CREDIT_DEBIT_CARD', TRUE),
('Australia', 'AU', 'AUD', 'PAY_WITH_BANK', TRUE),
('India', 'IN', 'INR', 'BANK_TRANSFER', TRUE),
('India', 'IN', 'INR', 'PAY_WITH_BANK', TRUE),
('India', 'IN', 'INR', 'WALLET', TRUE),
('India', 'IN', 'INR', 'CREDIT_DEBIT_CARD', FALSE),
('Germany', 'DE', 'EUR', 'BANK_TRANSFER', TRUE),
('Germany', 'DE', 'EUR', 'CREDIT_DEBIT_CARD', TRUE),
('Germany', 'DE', 'EUR', 'PAY_WITH_BANK', TRUE),
('Nigeria', 'NG', 'NGN', 'BANK_TRANSFER', TRUE),
('Nigeria', 'NG', 'NGN', 'WALLET', TRUE),
('Pakistan', 'PK', 'PKR', 'BANK_TRANSFER', TRUE),
('Pakistan', 'PK', 'PKR', 'WALLET', TRUE),
('Philippines', 'PH', 'PHP', 'BANK_TRANSFER', TRUE),
('Philippines', 'PH', 'PHP', 'WALLET', TRUE);

-- Mobile money services
INSERT INTO mobile_money_services (country_code, country_name, service_name) VALUES
('IN', 'India', 'Paytm'), ('IN', 'India', 'PhonePe'), ('IN', 'India', 'Google Pay'),
('PH', 'Philippines', 'GCash'), ('PH', 'Philippines', 'PayMaya'),
('NG', 'Nigeria', 'Airtel Money'), ('NG', 'Nigeria', 'MTN Mobile Money'),
('KE', 'Kenya', 'M-Pesa'), ('KE', 'Kenya', 'Airtel Money'),
('GH', 'Ghana', 'MTN Mobile Money'), ('GH', 'Ghana', 'Vodafone Cash'), ('GH', 'Ghana', 'AirtelTigo Money'),
('BD', 'Bangladesh', 'bKash'), ('BD', 'Bangladesh', 'Nagad'), ('BD', 'Bangladesh', 'Rocket'),
('PK', 'Pakistan', 'JazzCash'), ('PK', 'Pakistan', 'Easypaisa'),
('LK', 'Sri Lanka', 'FriMi'), ('LK', 'Sri Lanka', 'eZ Cash'),
('NP', 'Nepal', 'eSewa'), ('NP', 'Nepal', 'Khalti'),
('ZA', 'South Africa', 'Vodapay'), ('ZA', 'South Africa', 'FNB eWallet'),
('UG', 'Uganda', 'MTN Mobile Money'), ('UG', 'Uganda', 'Airtel Money');

-- Cash collection points
INSERT INTO cash_collection_points (country_code, country_name, point_name, address, city, contact_number) VALUES
('IN', 'India', 'SBI Branch - Mumbai Central', 'Dr DN Road, Fort, Mumbai 400001', 'Mumbai', '+912222620000'),
('IN', 'India', 'HDFC Bank - Connaught Place', 'Block A, Connaught Place, New Delhi 110001', 'New Delhi', '+911123417940'),
('PH', 'Philippines', 'Cebuana Lhuillier - Manila', 'Rizal Avenue, Manila', 'Manila', '+63281234567'),
('PH', 'Philippines', 'MLhuillier - Cebu', 'Colon Street, Cebu City', 'Cebu', '+63322531234'),
('NG', 'Nigeria', 'GTBank - Lagos Mainland', 'Herbert Macaulay Way, Yaba, Lagos', 'Lagos', '+23412800000'),
('NG', 'Nigeria', 'FirstBank - Abuja', 'Wuse Zone 4, Abuja', 'Abuja', '+23494620000'),
('KE', 'Kenya', 'Equity Bank - Nairobi', 'Equity Centre, Hospital Road, Upper Hill', 'Nairobi', '+254763026000'),
('KE', 'Kenya', 'KCB Bank - Mombasa', 'Nkrumah Road, Mombasa', 'Mombasa', '+254412226501'),
('GH', 'Ghana', 'GCB Bank - Accra', 'Thorpe Road, High Street', 'Accra', '+233302664910'),
('PK', 'Pakistan', 'HBL - Karachi', 'Habib Bank Plaza, I.I. Chundrigar Road', 'Karachi', '+922132418000'),
('BD', 'Bangladesh', 'Sonali Bank - Dhaka', 'Motijheel Commercial Area', 'Dhaka', '+88029550426'),
('ZA', 'South Africa', 'Standard Bank - Johannesburg', 'Simmonds Street, Johannesburg', 'Johannesburg', '+27860123456');

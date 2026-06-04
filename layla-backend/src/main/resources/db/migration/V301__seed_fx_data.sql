-- Default corridors
INSERT INTO corridors (send_country, receive_country, send_currency, receive_currency, is_active, min_amount, max_amount, required_kyc_tier) VALUES
('GBR', 'IND', 'GBP', 'INR', TRUE, 10, 50000, 'TIER_0'),
('GBR', 'PAK', 'GBP', 'PKR', TRUE, 10, 50000, 'TIER_0'),
('GBR', 'NGA', 'GBP', 'NGN', TRUE, 10, 50000, 'TIER_0'),
('GBR', 'GHA', 'GBP', 'GHS', TRUE, 10, 50000, 'TIER_0'),
('GBR', 'PHL', 'GBP', 'PHP', TRUE, 10, 50000, 'TIER_0');

-- Default corridor fees (flat fee per corridor)
INSERT INTO corridor_fees (corridor_id, delivery_method, fee_type, flat_fee, currency) VALUES
(1, 'BANK_DEPOSIT', 'FLAT', 1.99, 'GBP'),
(1, 'MOBILE_WALLET', 'FLAT', 0.99, 'GBP'),
(2, 'BANK_DEPOSIT', 'FLAT', 1.99, 'GBP'),
(2, 'CASH_PICKUP', 'FLAT', 2.99, 'GBP'),
(3, 'BANK_DEPOSIT', 'FLAT', 1.99, 'GBP'),
(3, 'MOBILE_WALLET', 'FLAT', 0.99, 'GBP'),
(4, 'BANK_DEPOSIT', 'FLAT', 1.99, 'GBP'),
(4, 'MOBILE_WALLET', 'FLAT', 0.99, 'GBP'),
(5, 'BANK_DEPOSIT', 'FLAT', 1.99, 'GBP'),
(5, 'CASH_PICKUP', 'FLAT', 2.99, 'GBP');

-- Default delivery methods per corridor
INSERT INTO corridor_delivery_methods (corridor_id, delivery_method, is_active, processing_time_minutes) VALUES
(1, 'BANK_DEPOSIT', TRUE, 1440),
(1, 'MOBILE_WALLET', TRUE, 60),
(2, 'BANK_DEPOSIT', TRUE, 1440),
(2, 'CASH_PICKUP', TRUE, 30),
(3, 'BANK_DEPOSIT', TRUE, 1440),
(3, 'MOBILE_WALLET', TRUE, 60),
(4, 'BANK_DEPOSIT', TRUE, 1440),
(4, 'MOBILE_WALLET', TRUE, 60),
(5, 'BANK_DEPOSIT', TRUE, 1440),
(5, 'CASH_PICKUP', TRUE, 30);

-- Default FX margins (global by currency pair)
INSERT INTO fx_margins (send_currency, receive_currency, margin_percentage, is_active) VALUES
('GBP', 'INR', 1.5000, TRUE),
('GBP', 'PKR', 1.8000, TRUE),
('GBP', 'NGN', 2.0000, TRUE),
('GBP', 'GHS', 2.0000, TRUE),
('GBP', 'PHP', 1.5000, TRUE);

-- Default nostro accounts
INSERT INTO nostro_accounts (bank_name, account_number, currency, country, current_balance, low_balance_threshold) VALUES
('HDFC Bank', '001234567890', 'INR', 'IND', 50000000.0000, 5000000.0000),
('HBL Bank', '009876543210', 'PKR', 'PAK', 100000000.0000, 10000000.0000),
('GTBank', '005555666677', 'NGN', 'NGA', 200000000.0000, 20000000.0000),
('GCB Bank', '003333444455', 'GHS', 'GHA', 5000000.0000, 500000.0000),
('BDO', '001111222233', 'PHP', 'PHL', 50000000.0000, 5000000.0000);

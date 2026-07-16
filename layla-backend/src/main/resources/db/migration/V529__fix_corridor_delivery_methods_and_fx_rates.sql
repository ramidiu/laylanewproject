-- Fix corridor_delivery_methods: remove delivery methods that have no matching payout_type
-- AED: only BANK_TRANSFER → remove MOBILE_WALLET, CASH_PICKUP from corridor 19
DELETE FROM corridor_delivery_methods WHERE corridor_id = 19 AND delivery_method IN ('MOBILE_WALLET','CASH_PICKUP');

-- SAR: only BANK_TRANSFER → remove MOBILE_WALLET, CASH_PICKUP from corridor 20
DELETE FROM corridor_delivery_methods WHERE corridor_id = 20 AND delivery_method IN ('MOBILE_WALLET','CASH_PICKUP');

-- TRY: only BANK_TRANSFER → remove MOBILE_WALLET, CASH_PICKUP from corridor 17
DELETE FROM corridor_delivery_methods WHERE corridor_id = 17 AND delivery_method IN ('MOBILE_WALLET','CASH_PICKUP');

-- SDG: BANK_TRANSFER + CASH_COLLECTION → remove only MOBILE_WALLET from corridor 16
DELETE FROM corridor_delivery_methods WHERE corridor_id = 16 AND delivery_method = 'MOBILE_WALLET';

-- QAR: only BANK_TRANSFER → remove MOBILE_WALLET, CASH_PICKUP from all QAR corridors (6-14)
DELETE FROM corridor_delivery_methods
WHERE corridor_id IN (SELECT id FROM corridors WHERE receive_currency = 'QAR')
  AND delivery_method IN ('MOBILE_WALLET','CASH_PICKUP');

-- Fix FX rates: replace 1.0 FALLBACK rates with real market rates (MANUAL)
-- These override the 1.0 fallback inserted during corridor auto-create
INSERT INTO fx_rate_history (base_currency, target_currency, rate, source)
VALUES
  ('GBP', 'EGP',  60.5000, 'MANUAL'),
  ('GBP', 'SDG',  608.000, 'MANUAL'),
  ('GBP', 'TRY',  43.2000, 'MANUAL'),
  ('GBP', 'UGX',  4762.00, 'MANUAL'),
  ('GBP', 'SAR',  4.83000, 'MANUAL'),
  ('GBP', 'QAR',  4.69000, 'MANUAL'),
  ('GBP', 'AED',  4.73000, 'MANUAL');

-- Add missing corridor_fees for CASH_PICKUP delivery method on Sudan and Uganda
-- (auto-create only inserted BANK_DEPOSIT fee; CASH_PICKUP needs a separate entry)
INSERT IGNORE INTO corridor_fees (corridor_id, delivery_method, fee_type, flat_fee, percentage_fee, currency, is_active)
VALUES
  (16, 'CASH_PICKUP', 'FLAT', 2.99, 0.00, 'GBP', 1),  -- Sudan cash pickup
  (18, 'MOBILE_WALLET', 'FLAT', 1.99, 0.00, 'GBP', 1), -- Uganda mobile wallet
  (18, 'CASH_PICKUP',   'FLAT', 2.99, 0.00, 'GBP', 1), -- Uganda cash pickup
  (15, 'MOBILE_WALLET', 'FLAT', 1.99, 0.00, 'GBP', 1), -- Egypt mobile wallet
  (15, 'CASH_PICKUP',   'FLAT', 2.99, 0.00, 'GBP', 1); -- Egypt cash pickup

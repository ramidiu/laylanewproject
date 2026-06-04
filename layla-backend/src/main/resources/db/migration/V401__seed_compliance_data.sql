-- Default monitoring rules
INSERT INTO monitoring_rules (rule_name, rule_type, parameters, severity, is_active) VALUES
('High Velocity', 'VELOCITY', '{"maxTransactions": 5, "windowHours": 24}', 'MEDIUM', TRUE),
('Large Transaction', 'AMOUNT_THRESHOLD', '{"threshold": 5000, "currency": "GBP"}', 'HIGH', TRUE),
('Structuring Detection', 'STRUCTURING', '{"threshold": 5000, "variance": 500, "count": 3, "windowHours": 48}', 'HIGH', TRUE),
('High Risk Corridor', 'CORRIDOR_RISK', '{"highRiskCountries": ["IRN","PRK","SYR","AFG","YEM"]}', 'CRITICAL', TRUE),
('New Customer Large Transfer', 'AMOUNT_THRESHOLD', '{"threshold": 1000, "currency": "GBP", "newCustomerDays": 30}', 'MEDIUM', TRUE);

-- Sample sanctions entries
INSERT INTO sanctions_lists (list_name, entry_name, entry_type, country, last_updated) VALUES
('OFAC', 'Sample Sanctioned Person', 'INDIVIDUAL', 'IRN', NOW()),
('HMT', 'Sample UK Sanctioned Entity', 'ENTITY', 'SYR', NOW()),
('UN', 'Sample UN Listed Individual', 'INDIVIDUAL', 'PRK', NOW());

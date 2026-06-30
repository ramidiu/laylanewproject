-- Add MoMo as a mobile wallet provider option for Uganda (alongside MTN Mobile Money and Airtel Money).
INSERT IGNORE INTO mobile_money_services (country_code, country_name, service_name) VALUES
('UG', 'Uganda', 'MoMo');

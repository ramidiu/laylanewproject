-- Phase 5: device fingerprinting + structured risk factors for fraud scoring.

ALTER TABLE transactions
    ADD COLUMN visitor_id VARCHAR(128) NULL AFTER risk_score,
    ADD COLUMN risk_factors JSON NULL AFTER visitor_id,
    ADD INDEX idx_transactions_visitor_id (visitor_id),
    ADD INDEX idx_transactions_risk_score (risk_score);

-- Records the USI per-transaction commission portion of a transaction's fee, so the
-- funds-received fee distribution can book it as a distinct pass-through COST (Layla pays
-- USI) instead of counting the whole fee — commission included — as platform revenue.
ALTER TABLE transactions
    ADD COLUMN usi_commission_amount DECIMAL(18,4) NULL DEFAULT 0.0000 AFTER fx_margin_amount;

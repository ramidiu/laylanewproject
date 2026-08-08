-- ---------------------------------------------------------------------------
-- V559: Recompute all ledger amounts and running balances in GBP.
--
-- The platform/partner ledgers historically stored a "USD" common denominator that
-- was (a) a legacy artefact — Layla's books are in GBP — and (b) computed with an
-- inverted conversion for credits, so credits and debits used opposite directions and
-- the running balances did not reconcile.
--
-- From now on the ledgers are kept in GBP (see LedgerCurrencyConverter). This migration
-- rewrites every existing row so history tallies too:
--   * usd_amount   -> the GBP value of the row (column name kept for compatibility)
--   * balance_usd  -> the GBP running balance (per-scope, ordered by id)
--
-- GBP value of a row = local_amount converted via settlement_rates.rate_to_usd
-- (the USD value of 1 unit of a currency):  amount * rate_to_usd(ccy) / rate_to_usd(GBP).
-- A GBP row converts to itself. Amounts are recomputed only from local_amount/local_currency,
-- which this migration never touches, so it is safe and deterministic.
-- ---------------------------------------------------------------------------

SET @gbp_rate = COALESCE((SELECT rate_to_usd FROM settlement_rates WHERE currency = 'GBP' LIMIT 1), 1.26);

-- ── platform_ledger (single global running balance, ordered by id) ──────────
UPDATE platform_ledger pl
JOIN (
    SELECT id,
           gbp_amount,
           SUM(CASE WHEN entry_type = 'CREDIT' THEN gbp_amount ELSE -gbp_amount END)
               OVER (ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
    FROM (
        SELECT p.id,
               p.entry_type,
               ROUND(
                   CASE
                       WHEN p.local_currency IS NULL OR p.local_currency = '' OR p.local_currency = 'GBP'
                            THEN COALESCE(p.local_amount, 0)
                       ELSE COALESCE(p.local_amount, 0) * COALESCE(sr.rate_to_usd, 1) / @gbp_rate
                   END, 4) AS gbp_amount
        FROM platform_ledger p
        LEFT JOIN settlement_rates sr ON sr.currency = p.local_currency
    ) base
) calc ON calc.id = pl.id
SET pl.usd_amount  = calc.gbp_amount,
    pl.balance_usd = calc.running_balance;

-- ── partner_ledger (running balance per partner_id, ordered by id) ──────────
UPDATE partner_ledger tgt
JOIN (
    SELECT id,
           gbp_amount,
           SUM(CASE WHEN entry_type = 'CREDIT' THEN gbp_amount ELSE -gbp_amount END)
               OVER (PARTITION BY partner_id ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
    FROM (
        SELECT p.id,
               p.partner_id,
               p.entry_type,
               ROUND(
                   CASE
                       WHEN p.local_currency IS NULL OR p.local_currency = '' OR p.local_currency = 'GBP'
                            THEN COALESCE(p.local_amount, 0)
                       ELSE COALESCE(p.local_amount, 0) * COALESCE(sr.rate_to_usd, 1) / @gbp_rate
                   END, 4) AS gbp_amount
        FROM partner_ledger p
        LEFT JOIN settlement_rates sr ON sr.currency = p.local_currency
    ) base
) calc ON calc.id = tgt.id
SET tgt.usd_amount  = calc.gbp_amount,
    tgt.balance_usd = calc.running_balance;

-- ── payin_partner_ledger (running balance per partner_id, ordered by id) ────
UPDATE payin_partner_ledger tgt
JOIN (
    SELECT id,
           gbp_amount,
           SUM(CASE WHEN entry_type = 'CREDIT' THEN gbp_amount ELSE -gbp_amount END)
               OVER (PARTITION BY partner_id ORDER BY id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS running_balance
    FROM (
        SELECT p.id,
               p.partner_id,
               p.entry_type,
               ROUND(
                   CASE
                       WHEN p.local_currency IS NULL OR p.local_currency = '' OR p.local_currency = 'GBP'
                            THEN COALESCE(p.local_amount, 0)
                       ELSE COALESCE(p.local_amount, 0) * COALESCE(sr.rate_to_usd, 1) / @gbp_rate
                   END, 4) AS gbp_amount
        FROM payin_partner_ledger p
        LEFT JOIN settlement_rates sr ON sr.currency = p.local_currency
    ) base
) calc ON calc.id = tgt.id
SET tgt.usd_amount  = calc.gbp_amount,
    tgt.balance_usd = calc.running_balance;

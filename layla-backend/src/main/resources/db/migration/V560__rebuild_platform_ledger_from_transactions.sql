-- ---------------------------------------------------------------------------
-- V560: Rebuild the platform ledger as a faithful GBP projection of `transactions`.
--
-- Why: the platform ledger was one-sided and mis-valued. Payout DEBITs were written by
-- several scattered code paths (admin mark-paid, payout-partner, USI auto-payout) but the
-- matching "money in" CREDIT was only written by a couple of flows — so ~170 of ~190 paid
-- transactions had a debit with NO credit, and the ledger could never balance. On top of that,
-- foreign payout amounts were valued in GBP via settlement_rates, whose SDG rate is ~9x stale
-- (real ~7,158 SDG/£ vs table 763 SDG/£), inflating debits into a phantom six-figure balance.
--
-- Correct model (Layla is GBP, fees are inclusive):
--   * CREDIT "Payment received"  = send_amount            (money the customer paid in)
--   * DEBIT  "Payout"            = send_amount - fee      (GBP principal actually sent out)
--   * residual per txn           = fee_amount             (= profit; the running balance = total profit)
--
-- Money-in CREDIT is recorded once funds are confirmed (FUNDS_RECEIVED / PAID / COMPLETED);
-- the payout DEBIT is recorded once the transaction is paid out (PAID / COMPLETED).
-- ARCHIVED/CANCELLED/PROCESSING are excluded (no confirmed funds). send_amount is treated as GBP.
-- ---------------------------------------------------------------------------

DELETE FROM platform_ledger;
ALTER TABLE platform_ledger AUTO_INCREMENT = 1;

INSERT INTO platform_ledger
    (transaction_id, txn_display_id, entry_type, local_amount, local_currency,
     usd_amount, fx_rate_used, balance_usd, description, account_type, created_at)
SELECT
    transaction_id,
    txn_display_id,
    entry_type,
    gbp_amount                                   AS local_amount,
    'GBP'                                        AS local_currency,
    gbp_amount                                   AS usd_amount,     -- column kept; holds GBP
    1.000000                                     AS fx_rate_used,
    ROUND(SUM(signed_gbp) OVER (ORDER BY sort_key ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW), 4)
                                                 AS balance_usd,    -- GBP running balance = cumulative profit
    description,
    account_type,
    created_at
FROM (
    -- Money in: what the customer paid (GBP)
    SELECT
        t.id                                           AS transaction_id,
        t.reference_number                             AS txn_display_id,
        'CREDIT'                                       AS entry_type,
        ROUND(COALESCE(t.send_amount, 0), 4)           AS gbp_amount,
        ROUND(COALESCE(t.send_amount, 0), 4)           AS signed_gbp,
        CONCAT('Payment received for ', t.reference_number) AS description,
        'HOLDING'                                      AS account_type,
        t.created_at                                   AS created_at,
        (t.id * 2)                                     AS sort_key
    FROM transactions t
    WHERE t.status IN ('FUNDS_RECEIVED', 'PAID', 'COMPLETED')

    UNION ALL

    -- Payout: GBP principal sent out (send - fee); the remaining fee stays as profit
    SELECT
        t.id,
        t.reference_number,
        'DEBIT',
        ROUND(COALESCE(t.send_amount, 0) - COALESCE(t.fee_amount, 0), 4),
        -ROUND(COALESCE(t.send_amount, 0) - COALESCE(t.fee_amount, 0), 4),
        CONCAT('Payout for ', t.reference_number),
        'PAYOUT',
        t.created_at,
        (t.id * 2 + 1)
    FROM transactions t
    WHERE t.status IN ('PAID', 'COMPLETED')
) events
ORDER BY sort_key;

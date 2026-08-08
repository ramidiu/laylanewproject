package com.remitz.modules.transaction.service;

import com.remitz.modules.transaction.entity.SettlementRate;
import com.remitz.modules.transaction.repository.SettlementRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Converts monetary amounts into the ledger's base currency, GBP.
 *
 * <p>Layla is a UK business: every customer send is in GBP and the platform's books are kept in GBP.
 * The ledger therefore uses GBP as its single common denominator so every credit, debit and running
 * balance can be summed and reconciled directly.</p>
 *
 * <p>{@code settlement_rates.rate_to_usd} is the USD value of ONE unit of a currency
 * (USD=1.0, GBP=1.26, AED=0.2723, UGX=0.000268). To express an amount in GBP we go
 * {@code amount(ccy) -> USD -> GBP}:
 * <pre>gbp = amount * rateToUsd(ccy) / rateToUsd(GBP)</pre>
 * A GBP amount converts to itself, so credits (always GBP) are stored exactly with no rounding drift.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerCurrencyConverter {

    public static final String BASE_CURRENCY = "GBP";

    private final SettlementRateRepository settlementRateRepository;

    /** Convert {@code amount} in {@code currency} to the base currency (GBP), scaled to 4 dp. */
    public BigDecimal toGbp(BigDecimal amount, String currency) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (currency == null || currency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return amount.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal usdPerGbp = rateToUsd(BASE_CURRENCY);
        if (usdPerGbp.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("No GBP settlement rate — cannot convert {} {} to GBP, storing 0", amount, currency);
            return BigDecimal.ZERO;
        }
        return amount.multiply(rateToUsd(currency))
                .divide(usdPerGbp, 4, RoundingMode.HALF_UP);
    }

    /** FX rate actually applied: units of GBP per 1 unit of {@code currency}. */
    public BigDecimal gbpRate(String currency) {
        if (currency == null || currency.isBlank() || BASE_CURRENCY.equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        BigDecimal usdPerGbp = rateToUsd(BASE_CURRENCY);
        if (usdPerGbp.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return rateToUsd(currency).divide(usdPerGbp, 8, RoundingMode.HALF_UP);
    }

    /** USD value of 1 unit of {@code currency}; USD=1, unknown currencies fall back to 1 (no scaling). */
    private BigDecimal rateToUsd(String currency) {
        if ("USD".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        return settlementRateRepository.findByCurrency(currency)
                .map(SettlementRate::getRateToUsd)
                .orElseGet(() -> {
                    log.warn("No settlement rate for {} — treating rate_to_usd as 1", currency);
                    return BigDecimal.ONE;
                });
    }
}

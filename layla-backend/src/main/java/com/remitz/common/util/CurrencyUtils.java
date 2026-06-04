package com.remitz.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

public final class CurrencyUtils {

    private CurrencyUtils() {
    }

    public static String format(BigDecimal amount, String currencyCode) {
        if (amount == null || currencyCode == null) {
            return null;
        }
        Currency currency = Currency.getInstance(currencyCode);
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.UK);
        formatter.setCurrency(currency);
        formatter.setMinimumFractionDigits(currency.getDefaultFractionDigits());
        formatter.setMaximumFractionDigits(currency.getDefaultFractionDigits());
        return formatter.format(amount);
    }

    public static BigDecimal round(BigDecimal amount, int scale) {
        if (amount == null) {
            return null;
        }
        return amount.setScale(scale, RoundingMode.HALF_UP);
    }
}

package com.remitz.modules.payout.usi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One row of the USI live-rate view (Phase 1, read-only): a corridor + payout method and the
 * USI rate/charges we get back from getCharges. Read-only diagnostic — does not affect quotes.
 */
@Data
@Builder
public class UsiRateRow {
    private String     corridor;         // "GBP->TRY"
    private String     sendCurrency;
    private String     receiveCurrency;
    private String     receiveCountry;   // ISO / name used for the getCharges lookup
    private String     deliveryMethod;   // BANK_DEPOSIT / MOBILE_WALLET / CASH_PICKUP
    private String     usiTransType;     // Account / Mobile Transfer / Cash Collection
    private Long       payoutPartnerId;  // set when the corridor method is routed to a partner
    private boolean    ok;               // true when USI returned SUCCESS
    private BigDecimal usiRate;
    private BigDecimal commission;
    private BigDecimal destAmount;
    private BigDecimal totalCharges;
    private String     error;
}

package com.remitz.modules.payout.usi.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * USI Money P&L summary. Profit for a USI-routed transaction is simply the fee Layla charges —
 * there is no FX-margin/conversion profit (the customer gets USI's source rate). USI's own
 * commission is shown for reference only and does not affect Layla's profit.
 */
@Data
@Builder
public class UsiLedgerSummary {

    private LocalDate from;
    private LocalDate to;

    private long       transactionCount;
    private BigDecimal totalVolume;          // sum of send_amount
    private BigDecimal totalFee;             // sum of fee_amount (= profit)
    private BigDecimal totalProfit;          // == totalFee
    private BigDecimal totalUsiCommission;   // sum of USI commission (reference only)

    private List<Bucket> byCorridor;
    private List<Bucket> byDay;
    private List<TxRow>   transactions;

    @Data
    @Builder
    public static class TxRow {
        private String     referenceNumber;
        private String     status;
        private String     senderEmail;
        private BigDecimal sendAmount;
        private String     sendCurrency;
        private BigDecimal receiveAmount;
        private String     receiveCurrency;
        private BigDecimal fee;              // = profit
        private BigDecimal usiCommission;    // reference only
        private String     usiReference;     // USI RM... ref
        private String     createdAt;
    }

    @Data
    @Builder
    public static class Bucket {
        private String     key;              // corridor "GBP->UGX" or day "2026-07-16"
        private long       transactionCount;
        private BigDecimal volume;
        private BigDecimal fee;              // = profit
        private BigDecimal usiCommission;
    }
}

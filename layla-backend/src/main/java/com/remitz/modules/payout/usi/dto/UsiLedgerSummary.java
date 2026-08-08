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
    private BigDecimal totalFee;             // sum of fee_amount (= gross fee income)
    private BigDecimal totalProfit;          // == totalFee (legacy "fee = profit" figure)
    private BigDecimal totalUsiCommission;   // sum of USI commission (cost Layla pays USI)
    private BigDecimal totalFxMargin;        // sum(send_amount - usi source_transfer_amount)
    private BigDecimal totalRealProfit;      // TRUE P&L = sum(send + fee - agent_to_pay_hq)

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
        private BigDecimal fee;              // Layla fee income
        private BigDecimal usiCommission;    // USI commission (cost)
        private String     usiReference;     // USI RM... ref
        private String     createdAt;
        // --- true profit/loss breakdown (per transaction) ---
        private BigDecimal usiRate;          // USI conversion rate
        private BigDecimal usiSource;        // source_transfer_amount — GBP USI actually converted
        private BigDecimal usiTotalPaid;     // agent_to_pay_hq — what Layla pays USI (source + commission)
        private BigDecimal custPaid;         // send + fee — what the customer paid Layla
        private BigDecimal fxMargin;         // send - usiSource — FX spread Layla keeps
        private BigDecimal realProfit;       // custPaid - usiTotalPaid — TRUE profit (negative = loss)
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

package com.remitz.modules.payout.usi.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Result of a USI getCharges quote (read-only). Phase 1: used to surface the live USI rate
 * and charges for a corridor. Does NOT create a transaction and does NOT affect customer quotes.
 */
@Data
public class UsiChargesResult {
    private String     status;          // SUCCESS / FAILED / ERROR
    private String     sourceCurrency;
    private String     destCurrency;
    private BigDecimal sourceAmount;    // the amount we asked USI to price (SOURCE side)
    private BigDecimal rate;            // USI exchange rate (dest per 1 source)
    private BigDecimal destAmount;      // what the receiver would get
    private BigDecimal commission;      // USI commission
    private BigDecimal agentFee;
    private BigDecimal hqFee;
    private BigDecimal tax;
    private BigDecimal totalCharges;    // USI total_charges
    private String     error;           // populated when status != SUCCESS
}

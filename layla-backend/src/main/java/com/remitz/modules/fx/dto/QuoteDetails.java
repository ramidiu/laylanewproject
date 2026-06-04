package com.remitz.modules.fx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteDetails {

    private String quoteId;
    private String sendCurrency;
    private String receiveCurrency;
    private BigDecimal sendAmount;
    private BigDecimal receiveAmount;
    private BigDecimal exchangeRate;
    private BigDecimal appliedRate;
    private BigDecimal marginApplied;
    private BigDecimal fee;
    private BigDecimal totalCost;
    private Long corridorId;
    private String deliveryMethod;
    private LocalDateTime rateLockedUntil;
}

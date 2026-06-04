package com.remitz.common.dto;

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
public class QuoteResponse {

    private String quoteId;
    private BigDecimal sendAmount;
    private BigDecimal receiveAmount;
    private BigDecimal exchangeRate;
    private BigDecimal appliedRate;
    private BigDecimal marginApplied;
    private BigDecimal fee;
    private BigDecimal totalCost;
    private LocalDateTime rateLockedUntil;
    private Long expiresInSeconds;
}

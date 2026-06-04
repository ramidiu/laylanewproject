package com.remitz.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummaryReport {

    private Long totalTransactions;
    private BigDecimal totalVolume;
    private BigDecimal successRate;
    private BigDecimal avgTransactionValue;
    private Map<String, Long> byStatus;
    private Map<String, Long> byCorridor;
}

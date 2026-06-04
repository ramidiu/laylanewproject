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
public class NostroAccountResponse {

    private Long id;
    private String bankName;
    private String accountNumber;
    private String currency;
    private String country;
    private BigDecimal currentBalance;
    private BigDecimal lowBalanceThreshold;
    private LocalDateTime lastReconciledAt;
}

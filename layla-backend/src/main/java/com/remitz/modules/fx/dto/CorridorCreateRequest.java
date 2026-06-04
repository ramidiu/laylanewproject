package com.remitz.modules.fx.dto;

import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorCreateRequest {

    @NotBlank(message = "Send country is required")
    @Size(min = 3, max = 3, message = "Send country must be 3 characters")
    private String sendCountry;

    @NotBlank(message = "Receive country is required")
    @Size(min = 3, max = 3, message = "Receive country must be 3 characters")
    private String receiveCountry;

    @NotBlank(message = "Send currency is required")
    @Size(min = 3, max = 3, message = "Send currency must be 3 characters")
    private String sendCurrency;

    @NotBlank(message = "Receive currency is required")
    @Size(min = 3, max = 3, message = "Receive currency must be 3 characters")
    private String receiveCurrency;

    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
    private KycTier requiredKycTier;
    private RiskLevel riskLevel;
}

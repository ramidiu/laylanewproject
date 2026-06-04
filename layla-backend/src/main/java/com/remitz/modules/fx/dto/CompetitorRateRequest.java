package com.remitz.modules.fx.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorRateRequest {

    @NotBlank(message = "Competitor name is required")
    private String competitorName;

    @NotBlank(message = "Send currency is required")
    private String sendCurrency;

    @NotBlank(message = "Receive currency is required")
    private String receiveCurrency;

    @NotNull(message = "Customer rate is required")
    private BigDecimal customerRate;

    private BigDecimal fee;
    private BigDecimal totalCostPerUnit;
}

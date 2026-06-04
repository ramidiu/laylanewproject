package com.remitz.modules.fx.dto;

import com.remitz.common.enums.KycTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorLimitsResponse {

    private Long corridorId;
    private KycTier kycTier;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private BigDecimal dailyLimit;
    private BigDecimal monthlyLimit;
}

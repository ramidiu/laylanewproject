package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorResponse {

    private Long id;
    private String sendCountry;
    private String receiveCountry;
    private String sendCurrency;
    private String receiveCurrency;
    private Boolean isActive;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private KycTier requiredKycTier;
    private RiskLevel riskLevel;
    private List<DeliveryMethod> deliveryMethods;
}

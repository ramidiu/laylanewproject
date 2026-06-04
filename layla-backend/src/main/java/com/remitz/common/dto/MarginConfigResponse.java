package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarginConfigResponse {

    private Long id;
    private String sendCurrency;
    private String receiveCurrency;
    private DeliveryMethod deliveryMethod;
    private BigDecimal marginPercentage;
    private BigDecimal marginFixed;
    private String customerTier;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Boolean isActive;
}

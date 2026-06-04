package com.remitz.modules.fx.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.FeeType;
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
public class CorridorFeeRequest {

    @NotNull(message = "Delivery method is required")
    private DeliveryMethod deliveryMethod;

    @NotNull(message = "Fee type is required")
    private FeeType feeType;

    private BigDecimal flatFee;
    private BigDecimal percentageFee;
    private String tierRules;
    private String currency;
    private String updatedBy;
}

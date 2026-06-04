package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.FeeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorFeeResponse {

    private Long id;
    private Long corridorId;
    private DeliveryMethod deliveryMethod;
    private FeeType feeType;
    private BigDecimal flatFee;
    private BigDecimal percentageFee;
    private String tierRules;
    private String currency;
    private Boolean isActive;
}

package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequest {

    @NotBlank(message = "Send currency is required")
    private String sendCurrency;

    @NotBlank(message = "Receive currency is required")
    private String receiveCurrency;

    private BigDecimal sendAmount;
    private BigDecimal receiveAmount;
    private DeliveryMethod deliveryMethod;
    private Long corridorId;
}

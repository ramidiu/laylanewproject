package com.remitz.modules.transaction.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.RecurringFrequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRecurringTransferRequest {

    @NotNull(message = "Beneficiary ID is required")
    private Long beneficiaryId;

    @NotNull(message = "Corridor ID is required")
    private Long corridorId;

    @NotNull(message = "Delivery method is required")
    private DeliveryMethod deliveryMethod;

    @NotNull(message = "Send amount is required")
    private BigDecimal sendAmount;

    @NotBlank(message = "Send currency is required")
    private String sendCurrency;

    @NotBlank(message = "Receive currency is required")
    private String receiveCurrency;

    @NotNull(message = "Frequency is required")
    private RecurringFrequency frequency;

    private Integer customIntervalDays;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType paymentMethodType;

    private Integer maxExecutions;

    private String notes;
}

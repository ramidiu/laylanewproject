package com.remitz.modules.transaction.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.RecurringFrequency;
import com.remitz.common.enums.RecurringStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransferResponse {

    private Long id;
    private Long beneficiaryId;
    private String beneficiaryName;
    private Long corridorId;
    private DeliveryMethod deliveryMethod;
    private BigDecimal sendAmount;
    private String sendCurrency;
    private String receiveCurrency;
    private RecurringFrequency frequency;
    private Integer customIntervalDays;
    private LocalDate nextExecutionDate;
    private LocalDate lastExecutionDate;
    private RecurringStatus status;
    private PaymentMethodType paymentMethodType;
    private Integer totalExecutions;
    private Integer maxExecutions;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.remitz.modules.transaction.dto;

import com.remitz.common.enums.RecurringFrequency;
import com.remitz.common.enums.RecurringStatus;
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
public class UpdateRecurringTransferRequest {

    private BigDecimal sendAmount;
    private RecurringFrequency frequency;
    private Integer customIntervalDays;
    private LocalDate nextExecutionDate;
    private RecurringStatus status;
    private Integer maxExecutions;
    private String notes;
}

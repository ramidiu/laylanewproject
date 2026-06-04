package com.remitz.common.dto;

import com.remitz.common.enums.TransactionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionStatusUpdateRequest {

    @NotNull(message = "Status is required")
    private TransactionStatus status;

    private String reason;
}

package com.remitz.common.dto;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAlertResponse {

    private Long id;
    private String alertType;
    private Long ruleId;
    private Long transactionId;
    private UUID userId;
    private AlertSeverity severity;
    private AlertStatus status;
    private String assignedTo;
    private String notes;
    private LocalDateTime createdAt;
}

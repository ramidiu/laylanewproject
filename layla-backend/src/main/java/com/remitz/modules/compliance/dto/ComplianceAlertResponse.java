package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceAlertResponse {

    private Long id;
    private Long ruleId;
    private String ruleName;
    private Long userId;
    private Long transactionId;
    private AlertSeverity severity;
    private AlertStatus status;
    private String description;
    private String details;
    private Long assignedTo;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

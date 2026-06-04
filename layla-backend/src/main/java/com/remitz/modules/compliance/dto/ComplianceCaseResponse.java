package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCaseResponse {

    private Long id;
    private String caseReference;
    private Long userId;
    private CaseStatus status;
    private AlertSeverity priority;
    private Long assignedTo;
    private String summary;
    private String findings;
    private String outcome;
    private List<SarReportResponse> sarReports;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
}

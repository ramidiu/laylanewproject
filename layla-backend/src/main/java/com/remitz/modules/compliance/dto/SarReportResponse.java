package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.SarFilingStatus;
import com.remitz.common.enums.SarReportType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SarReportResponse {

    private Long id;
    private Long caseId;
    private SarReportType reportType;
    private SarFilingStatus filingStatus;
    private String reportContent;
    private Long filedBy;
    private LocalDateTime filedAt;
    private LocalDateTime acknowledgedAt;
    private String externalReference;
    private LocalDateTime createdAt;
}

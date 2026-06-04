package com.remitz.common.dto;

import com.remitz.common.enums.CaseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplianceCaseResponse {

    private Long id;
    private String caseReference;
    private List<Long> alertIds;
    private UUID userId;
    private CaseStatus status;
    private String assignedTo;
    private String priority;
    private String summary;
    private String findings;
    private String decision;
    private LocalDateTime createdAt;
}

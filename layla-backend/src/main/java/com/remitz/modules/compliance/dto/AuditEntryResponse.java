package com.remitz.modules.compliance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditEntryResponse {
    private Long alertId;
    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String severity;
    private String status;
    private String listType;
    private String matchedName;
    private String source;
    private String description;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerEmail;
    private String reason;
    private LocalDateTime resolvedAt;
    private LocalDateTime createdAt;
}

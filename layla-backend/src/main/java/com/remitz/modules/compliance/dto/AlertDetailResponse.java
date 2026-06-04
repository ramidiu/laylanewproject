package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDetailResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String userCountry;
    private Long transactionId;
    private String transactionReference;
    private AlertSeverity severity;
    private AlertStatus status;
    private String description;
    private Map<String, Object> details;
    private Long assignedTo;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;
    private String resolutionNotes;
    private LocalDateTime createdAt;

    private Long listEntryId;
    private String listEntryExternalId;
    private String listEntryName;
    private String listEntrySource;
    private String listEntryListType;
    private String listEntryCountry;
    private String listEntryAliases;
    private String listEntryTopics;
    private String listEntryDateOfBirth;
}

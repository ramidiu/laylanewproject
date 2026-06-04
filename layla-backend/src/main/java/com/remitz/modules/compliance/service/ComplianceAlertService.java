package com.remitz.modules.compliance.service;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import com.remitz.modules.compliance.dto.AlertUpdateRequest;
import com.remitz.modules.compliance.dto.ComplianceAlertResponse;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceAlertService {

    private final ComplianceAlertRepository complianceAlertRepository;

    @Transactional(readOnly = true)
    public Page<ComplianceAlertResponse> getAlerts(AlertStatus status, AlertSeverity severity,
                                                    Long userId, Pageable pageable) {
        return complianceAlertRepository.findWithFilters(status, severity, userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public java.util.Map<Long, Long> countOpenAlertsForUsers(java.util.Collection<Long> userIds) {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        for (Long id : userIds) result.put(id, 0L);
        if (userIds.isEmpty()) return result;
        var rows = complianceAlertRepository.countOpenByUserIds(
                userIds,
                java.util.Arrays.asList(AlertStatus.OPEN, AlertStatus.UNDER_REVIEW));
        for (var r : rows) {
            result.put(r.getUid(), r.getCnt());
        }
        return result;
    }

    @Transactional
    public ComplianceAlertEntity createAlert(Long userId,
                                              Long transactionId,
                                              AlertSeverity severity,
                                              String description,
                                              String detailsJson) {
        // Compliance disabled: do not persist. Return a transient entity so callers don't NPE.
        log.debug("Compliance alerts disabled — skipping createAlert for userId={}, severity={}, reason={}",
                userId, severity, description);
        return ComplianceAlertEntity.builder()
                .userId(userId)
                .transactionId(transactionId)
                .severity(severity != null ? severity : AlertSeverity.LOW)
                .status(AlertStatus.CLOSED_NO_ACTION)
                .description(description)
                .details(detailsJson)
                .build();
    }

    @Transactional
    public ComplianceAlertResponse updateAlert(Long id, AlertUpdateRequest request) {
        ComplianceAlertEntity alert = complianceAlertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + id));

        if (request.getStatus() != null) {
            alert.setStatus(request.getStatus());

            if (isClosedStatus(request.getStatus())) {
                alert.setResolvedAt(LocalDateTime.now());
            }
        }

        if (request.getAssignedTo() != null) {
            alert.setAssignedTo(request.getAssignedTo());
        }

        if (request.getResolutionNotes() != null) {
            alert.setResolutionNotes(request.getResolutionNotes());
        }

        log.info("Alert {} updated: status={}, assignedTo={}", id, alert.getStatus(), alert.getAssignedTo());
        return toResponse(complianceAlertRepository.save(alert));
    }

    @Transactional
    public ComplianceAlertResponse assignAlert(Long id, Long assigneeId) {
        ComplianceAlertEntity alert = complianceAlertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Alert not found: " + id));

        alert.setAssignedTo(assigneeId);
        if (alert.getStatus() == AlertStatus.OPEN) {
            alert.setStatus(AlertStatus.UNDER_REVIEW);
        }

        log.info("Alert {} assigned to user {}", id, assigneeId);
        return toResponse(complianceAlertRepository.save(alert));
    }

    private boolean isClosedStatus(AlertStatus status) {
        return status == AlertStatus.CLOSED_NO_ACTION
                || status == AlertStatus.CLOSED_SAR_FILED
                || status == AlertStatus.CLOSED_FALSE_POSITIVE;
    }

    private ComplianceAlertResponse toResponse(ComplianceAlertEntity entity) {
        return ComplianceAlertResponse.builder()
                .id(entity.getId())
                .ruleId(entity.getRule() != null ? entity.getRule().getId() : null)
                .ruleName(entity.getRule() != null ? entity.getRule().getRuleName() : null)
                .userId(entity.getUserId())
                .transactionId(entity.getTransactionId())
                .severity(entity.getSeverity())
                .status(entity.getStatus())
                .description(entity.getDescription())
                .details(entity.getDetails())
                .assignedTo(entity.getAssignedTo())
                .resolvedBy(entity.getResolvedBy())
                .resolvedAt(entity.getResolvedAt())
                .resolutionNotes(entity.getResolutionNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

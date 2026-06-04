package com.remitz.modules.compliance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.enums.AlertStatus;
import com.remitz.modules.compliance.dto.AlertDetailResponse;
import com.remitz.modules.compliance.dto.AlertDispositionRequest;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.entity.ComplianceWhitelistEntity;
import com.remitz.modules.compliance.entity.SanctionsListEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import com.remitz.modules.compliance.repository.ComplianceWhitelistRepository;
import com.remitz.modules.compliance.repository.SanctionsListRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceDispositionService {

    private final ComplianceAlertRepository alertRepository;
    private final ComplianceWhitelistRepository whitelistRepository;
    private final SanctionsListRepository sanctionsListRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${app.transaction-service.url:http://localhost:8083}")
    private String transactionServiceUrl;

    @Transactional(readOnly = true)
    public AlertDetailResponse getAlertDetail(Long alertId) {
        ComplianceAlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        AlertDetailResponse.AlertDetailResponseBuilder builder = AlertDetailResponse.builder()
                .id(alert.getId())
                .userId(alert.getUserId())
                .transactionId(alert.getTransactionId())
                .severity(alert.getSeverity())
                .status(alert.getStatus())
                .description(alert.getDescription())
                .assignedTo(alert.getAssignedTo())
                .resolvedBy(alert.getResolvedBy())
                .resolvedAt(alert.getResolvedAt())
                .resolutionNotes(alert.getResolutionNotes())
                .createdAt(alert.getCreatedAt())
                .listEntryId(alert.getListEntryId());

        if (alert.getDetails() != null) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(alert.getDetails(),
                        new TypeReference<Map<String, Object>>() {});
                builder.details(parsed);
            } catch (Exception e) {
                builder.details(Collections.singletonMap("raw", alert.getDetails()));
            }
        }

        // Look up user info via native query across the shared DB
        if (alert.getUserId() != null && alert.getUserId() > 0) {
            try {
                Map<String, Object> user = jdbcTemplate.queryForMap(
                        "SELECT CONCAT_WS(' ', first_name, last_name) AS name, email, country " +
                                "FROM users WHERE id = ?", alert.getUserId());
                builder.userName((String) user.get("name"));
                builder.userEmail((String) user.get("email"));
                builder.userCountry((String) user.get("country"));
            } catch (Exception e) {
                log.debug("Could not load user info for alert {}: {}", alertId, e.getMessage());
            }
        }

        // Look up transaction reference if present
        if (alert.getTransactionId() != null) {
            try {
                String ref = jdbcTemplate.queryForObject(
                        "SELECT reference_number FROM transactions WHERE id = ?",
                        String.class, alert.getTransactionId());
                builder.transactionReference(ref);
            } catch (Exception e) {
                log.debug("Could not load txn ref for alert {}: {}", alertId, e.getMessage());
            }
        }

        // Enrich with sanctions list entry
        if (alert.getListEntryId() != null) {
            sanctionsListRepository.findById(alert.getListEntryId()).ifPresent(entry -> {
                builder.listEntryExternalId(entry.getExternalId());
                builder.listEntryName(entry.getEntryName());
                builder.listEntrySource(entry.getSourceCode());
                builder.listEntryListType(entry.getListType() != null ? entry.getListType().name() : null);
                builder.listEntryCountry(entry.getCountry());
                builder.listEntryAliases(entry.getAliases());
                builder.listEntryTopics(entry.getTopics());
                builder.listEntryDateOfBirth(entry.getDateOfBirth() != null ? entry.getDateOfBirth().toString() : null);
            });
        }

        return builder.build();
    }

    @Transactional
    public int bulkDispositionByUser(Long userId, AlertDispositionRequest request, Long reviewerId) {
        if (userId == null) throw new IllegalArgumentException("userId is required");
        List<ComplianceAlertEntity> open = alertRepository.findByUserIdAndStatusIn(
                userId,
                java.util.Arrays.asList(AlertStatus.OPEN, AlertStatus.UNDER_REVIEW));
        int dispositioned = 0;
        for (ComplianceAlertEntity alert : open) {
            try {
                disposition(alert.getId(), request, reviewerId);
                dispositioned++;
            } catch (Exception e) {
                log.warn("Bulk disposition skipped alert {}: {}", alert.getId(), e.getMessage());
            }
        }
        log.info("Bulk disposition for userId={} action={} reviewer={} dispositioned={}",
                userId, request.getAction(), reviewerId, dispositioned);
        return dispositioned;
    }

    @Transactional
    public ComplianceAlertEntity disposition(Long alertId, AlertDispositionRequest request, Long reviewerId) {
        ComplianceAlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        if (alert.getStatus() != AlertStatus.OPEN && alert.getStatus() != AlertStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Alert is not open, current status: " + alert.getStatus());
        }

        AlertDispositionRequest.Action action = request.getAction();
        String reason = request.getReason() != null ? request.getReason() : "";

        switch (action) {
            case FALSE_POSITIVE -> {
                alert.setStatus(AlertStatus.CLOSED_FALSE_POSITIVE);
                alert.setResolvedAt(LocalDateTime.now());
                alert.setResolvedBy(reviewerId);
                alert.setResolutionNotes(reason);
                whitelistHit(alert, reviewerId, reason);
                releaseLinkedTransaction(alert);
            }
            case CONFIRMED_MATCH -> {
                alert.setStatus(AlertStatus.CLOSED_SAR_FILED);
                alert.setResolvedAt(LocalDateTime.now());
                alert.setResolvedBy(reviewerId);
                alert.setResolutionNotes(reason);
            }
            case ESCALATE -> {
                alert.setStatus(AlertStatus.ESCALATED);
                alert.setResolutionNotes(reason);
            }
        }

        ComplianceAlertEntity saved = alertRepository.save(alert);
        log.info("Alert {} dispositioned: action={}, reviewer={}, newStatus={}",
                alertId, action, reviewerId, saved.getStatus());
        return saved;
    }

    private void whitelistHit(ComplianceAlertEntity alert, Long reviewerId, String reason) {
        if (alert.getListEntryId() == null || alert.getUserId() == null || alert.getUserId() == 0L) return;
        boolean exists = whitelistRepository.existsBySubjectTypeAndSubjectIdAndListEntryId(
                ComplianceWhitelistEntity.SubjectType.CUSTOMER,
                alert.getUserId(),
                alert.getListEntryId());
        if (exists) return;
        whitelistRepository.save(ComplianceWhitelistEntity.builder()
                .subjectType(ComplianceWhitelistEntity.SubjectType.CUSTOMER)
                .subjectId(alert.getUserId())
                .listEntryId(alert.getListEntryId())
                .whitelistedByUserId(reviewerId)
                .reason(reason)
                .build());
    }

    private void releaseLinkedTransaction(ComplianceAlertEntity alert) {
        Long txnId = alert.getTransactionId();
        if (txnId == null) return;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("reason", "Compliance false-positive cleared (alertId=" + alert.getId() + ")");
            ResponseEntity<String> resp = restTemplate.exchange(
                    transactionServiceUrl + "/api/transactions/admin/" + txnId + "/release-from-compliance",
                    org.springframework.http.HttpMethod.PUT,
                    new HttpEntity<>(body, headers),
                    String.class);
            log.info("Txn {} release-from-compliance response: {}", txnId, resp.getStatusCode());
        } catch (Exception e) {
            log.warn("Failed to release txn {} from compliance hold: {}", txnId, e.getMessage());
        }
    }
}

package com.remitz.modules.compliance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.enums.AlertSeverity;
import com.remitz.modules.compliance.dto.ScreeningRequest;
import com.remitz.modules.compliance.dto.ScreeningResponse;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.entity.CtrReportEntity;
import com.remitz.modules.compliance.entity.ScreeningResultEntity;
import com.remitz.modules.compliance.repository.CtrReportRepository;
import com.remitz.modules.compliance.service.ComplianceAlertService;
import com.remitz.modules.compliance.service.SanctionsScreeningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service-to-service endpoints. Not routed by the api-gateway (which only
 * forwards /api/** paths), so only in-cluster callers on the compliance-service
 * host port can reach these.
 */
@RestController
@RequestMapping("/internal/compliance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance Internal", description = "Internal service-to-service compliance APIs")
public class InternalComplianceController {

    private final SanctionsScreeningService sanctionsScreeningService;
    private final ComplianceAlertService complianceAlertService;
    private final CtrReportRepository ctrReportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/ctr")
    @Operation(summary = "Internal: upsert a CTR draft",
            description = "Called by transaction-service nightly CTR scheduler to persist threshold reports")
    public ResponseEntity<Map<String, Object>> upsertCtr(@RequestBody Map<String, Object> body) {
        try {
            java.time.LocalDate reportDate = java.time.LocalDate.parse(body.get("reportDate").toString());
            Long userId = Long.valueOf(body.get("userId").toString());
            int txnCount = ((Number) body.get("transactionCount")).intValue();
            java.math.BigDecimal total = new java.math.BigDecimal(body.get("totalAmount").toString());
            String currency = body.get("currency").toString();
            java.math.BigDecimal threshold = new java.math.BigDecimal(body.get("threshold").toString());
            String userEmail = body.get("userEmail") != null ? body.get("userEmail").toString() : null;
            String userName = body.get("userName") != null ? body.get("userName").toString() : null;
            Object refs = body.get("transactionRefs");
            String refsJson = refs != null ? objectMapper.writeValueAsString(refs) : null;

            CtrReportEntity existing = ctrReportRepository
                    .findByReportDateAndUserId(reportDate, userId)
                    .orElse(null);

            CtrReportEntity saved;
            if (existing != null) {
                existing.setTransactionCount(txnCount);
                existing.setTotalAmount(total);
                existing.setCurrency(currency);
                existing.setThreshold(threshold);
                existing.setTransactionRefs(refsJson);
                if (userEmail != null) existing.setUserEmail(userEmail);
                if (userName != null) existing.setUserName(userName);
                saved = ctrReportRepository.save(existing);
            } else {
                saved = ctrReportRepository.save(CtrReportEntity.builder()
                        .reportDate(reportDate)
                        .userId(userId)
                        .userEmail(userEmail)
                        .userName(userName)
                        .transactionCount(txnCount)
                        .totalAmount(total)
                        .currency(currency)
                        .threshold(threshold)
                        .transactionRefs(refsJson)
                        .filingStatus(CtrReportEntity.FilingStatus.DRAFT)
                        .build());
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("id", saved.getId());
            resp.put("reportDate", saved.getReportDate());
            resp.put("userId", saved.getUserId());
            resp.put("filingStatus", saved.getFilingStatus());
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("CTR upsert failed: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/alerts")
    @Operation(summary = "Internal: create a compliance alert",
            description = "Called by transaction-service when a non-blocking monitoring rule fires")
    public ResponseEntity<Map<String, Object>> createAlert(@RequestBody Map<String, Object> body) {
        Long userId = body.get("userId") != null ? Long.valueOf(body.get("userId").toString()) : null;
        Long transactionId = body.get("transactionId") != null ? Long.valueOf(body.get("transactionId").toString()) : null;
        String severityStr = body.get("severity") != null ? body.get("severity").toString() : "MEDIUM";
        String description = body.get("description") != null ? body.get("description").toString() : "Alert";
        String detailsJson = body.get("details") != null ? body.get("details").toString() : null;

        AlertSeverity severity;
        try {
            severity = AlertSeverity.valueOf(severityStr.toUpperCase());
        } catch (Exception e) {
            severity = AlertSeverity.MEDIUM;
        }

        if (userId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "userId is required"));
        }

        ComplianceAlertEntity saved = complianceAlertService.createAlert(
                userId, transactionId, severity, description, detailsJson);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", saved.getId());
        response.put("userId", saved.getUserId());
        response.put("transactionId", saved.getTransactionId());
        response.put("severity", saved.getSeverity());
        response.put("status", saved.getStatus());
        response.put("createdAt", saved.getCreatedAt());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/screen")
    @Operation(summary = "Internal: screen a name against sanctions + PEP lists",
            description = "Called by auth-service on registration and transaction-service at txn time")
    public ResponseEntity<List<ScreeningResponse>> screen(@Valid @RequestBody ScreeningRequest request) {
        log.info("Internal screen for '{}' (country={}, dob={}) entity={}:{}",
                request.getFullName(), request.getCountry(), request.getDateOfBirth(),
                request.getEntityType(), request.getEntityId());

        List<ScreeningResultEntity> results = sanctionsScreeningService.screen(
                request.getFullName(),
                request.getCountry(),
                request.getDateOfBirth(),
                request.getEntityType(),
                request.getEntityId());

        List<ScreeningResponse> response = results.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    private ScreeningResponse toResponse(ScreeningResultEntity entity) {
        boolean isHit = entity.getStatus() == com.remitz.common.enums.ScreeningStatus.POTENTIAL_MATCH
                || entity.getStatus() == com.remitz.common.enums.ScreeningStatus.CONFIRMED_MATCH;
        return ScreeningResponse.builder()
                .id(entity.getId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .screenedName(entity.getScreenedName())
                .matchedList(entity.getMatchedList())
                .matchedEntryId(entity.getMatchedEntryId())
                .matchScore(entity.getMatchScore())
                .status(entity.getStatus())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .hit(isHit)
                .build();
    }
}

package com.remitz.modules.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.CaseStatus;
import com.remitz.common.enums.SarFilingStatus;
import com.remitz.common.enums.SarReportType;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.entity.ComplianceCaseEntity;
import com.remitz.modules.compliance.entity.SarReportEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import com.remitz.modules.compliance.repository.ComplianceCaseRepository;
import com.remitz.modules.compliance.repository.SarReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Builds a structured JSON SAR payload from a closed compliance alert.
 * The payload is written to sar_reports.report_content and left in DRAFT
 * for a compliance officer to review before filing to the regulator.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SarContentGeneratorService {

    private final ComplianceAlertRepository alertRepository;
    private final ComplianceCaseRepository caseRepository;
    private final SarReportRepository sarReportRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public SarReportEntity generateFromAlert(Long alertId, Long requestedByUserId) {
        ComplianceAlertEntity alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Alert not found: " + alertId));

        ComplianceCaseEntity complianceCase = findOrCreateCase(alert);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reportType", "SAR");
        payload.put("generatedAt", LocalDateTime.now().toString());
        payload.put("generatedBy", requestedByUserId);

        Map<String, Object> subject = new LinkedHashMap<>();
        subject.put("customerId", alert.getUserId());
        subject.put("caseReference", complianceCase.getCaseReference());
        payload.put("subject", subject);

        Map<String, Object> trigger = new LinkedHashMap<>();
        trigger.put("alertId", alert.getId());
        trigger.put("alertStatus", alert.getStatus() != null ? alert.getStatus().name() : null);
        trigger.put("alertSeverity", alert.getSeverity() != null ? alert.getSeverity().name() : null);
        trigger.put("description", alert.getDescription());
        trigger.put("ruleName", alert.getRule() != null ? alert.getRule().getRuleName() : null);
        trigger.put("ruleType", alert.getRule() != null && alert.getRule().getRuleType() != null
                ? alert.getRule().getRuleType().name() : null);
        trigger.put("transactionId", alert.getTransactionId());
        trigger.put("detectedAt", alert.getCreatedAt() != null ? alert.getCreatedAt().toString() : null);
        trigger.put("resolvedAt", alert.getResolvedAt() != null ? alert.getResolvedAt().toString() : null);
        trigger.put("dispositionedBy", alert.getResolvedBy());
        trigger.put("dispositionNotes", alert.getResolutionNotes());
        payload.put("trigger", trigger);

        if (alert.getDetails() != null && !alert.getDetails().isBlank()) {
            try {
                Object parsed = objectMapper.readValue(alert.getDetails(), Object.class);
                payload.put("ruleDetails", parsed);
            } catch (Exception e) {
                payload.put("ruleDetails", alert.getDetails());
            }
        }

        Map<String, Object> narrative = new LinkedHashMap<>();
        narrative.put("summary", String.format(
                "Customer %d triggered compliance alert '%s' and was closed as %s on %s.",
                alert.getUserId(),
                alert.getDescription() != null ? alert.getDescription() : "(no description)",
                alert.getStatus(),
                alert.getResolvedAt() != null ? alert.getResolvedAt() : "(pending)"));
        narrative.put("recommendation",
                "Manually review the facts above, attach supporting evidence, then change filing_status to SUBMITTED and record the regulator acknowledgement.");
        payload.put("narrative", narrative);

        String reportContent;
        try {
            reportContent = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception e) {
            reportContent = "{\"error\":\"serialization failed: " + e.getMessage() + "\"}";
        }

        SarReportEntity sar = SarReportEntity.builder()
                .complianceCase(complianceCase)
                .reportType(SarReportType.SAR)
                .filingStatus(SarFilingStatus.DRAFT)
                .reportContent(reportContent)
                .build();
        SarReportEntity saved = sarReportRepository.save(sar);
        log.info("Generated SAR draft id={} for alert={} case={}",
                saved.getId(), alert.getId(), complianceCase.getCaseReference());
        return saved;
    }

    private ComplianceCaseEntity findOrCreateCase(ComplianceAlertEntity alert) {
        Optional<ComplianceCaseEntity> latest = caseRepository.findTopByUserIdOrderByIdDesc(alert.getUserId());
        if (latest.isPresent() && latest.get().getStatus() != CaseStatus.CLOSED) {
            return latest.get();
        }
        String ref = "CASE-" + alert.getUserId() + "-" + System.currentTimeMillis();
        AlertSeverity priority = alert.getSeverity() != null ? alert.getSeverity() : AlertSeverity.MEDIUM;
        ComplianceCaseEntity newCase = ComplianceCaseEntity.builder()
                .caseReference(ref)
                .userId(alert.getUserId())
                .status(CaseStatus.INVESTIGATING)
                .priority(priority)
                .summary("Case auto-opened for SAR generation from alert " + alert.getId())
                .build();
        return caseRepository.save(newCase);
    }
}

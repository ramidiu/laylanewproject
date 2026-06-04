package com.remitz.modules.compliance.controller;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import com.remitz.common.enums.EntityType;
import com.remitz.modules.compliance.dto.*;
import com.remitz.modules.compliance.entity.RiskScoreEntity;
import com.remitz.modules.compliance.entity.ScreeningResultEntity;
import com.remitz.modules.compliance.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/compliance")
@RequiredArgsConstructor
@Tag(name = "Compliance", description = "Compliance, AML, and risk management APIs")
public class ComplianceController {

    private final SanctionsScreeningService sanctionsScreeningService;
    private final ComplianceAlertService complianceAlertService;
    private final ComplianceCaseService complianceCaseService;
    private final ComplianceRiskScoringService riskScoringService;
    private final MonitoringRuleService monitoringRuleService;
    private final ComplianceDispositionService complianceDispositionService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    // --- Screening ---

    @PostMapping("/screen")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('user:approve_kyc')")
    @Operation(summary = "Screen a name against sanctions lists",
            description = "Performs fuzzy matching against OFAC, EU, UN, and HMT sanctions lists")
    public ResponseEntity<List<ScreeningResponse>> screen(@Valid @RequestBody ScreeningRequest request) {
        List<ScreeningResultEntity> results = sanctionsScreeningService.screen(
                request.getFullName(), request.getCountry(), request.getDateOfBirth(),
                request.getEntityType(), request.getEntityId());

        List<ScreeningResponse> response = results.stream()
                .map(this::toScreeningResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    // --- Alerts ---

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('compliance:view_alerts')")
    @Operation(summary = "List compliance alerts", description = "Paginated and filterable alert listing")
    public ResponseEntity<Page<ComplianceAlertResponse>> getAlerts(
            @RequestParam(required = false) AlertStatus status,
            @RequestParam(required = false) AlertSeverity severity,
            @RequestParam(required = false) Long userId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(complianceAlertService.getAlerts(status, severity, userId, pageable));
    }

    @GetMapping("/alerts/open-counts")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('user:approve_kyc') or hasAuthority('config:manage_corridors')")
    @Operation(summary = "Count OPEN+UNDER_REVIEW alerts per user",
            description = "Batch endpoint for the KYC review page badges")
    public ResponseEntity<Map<Long, Long>> openAlertCounts(@RequestParam("userIds") List<Long> userIds) {
        return ResponseEntity.ok(complianceAlertService.countOpenAlertsForUsers(userIds));
    }

    @PostMapping("/alerts/bulk-disposition")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('compliance:file_sar') or hasAuthority('config:manage_corridors')")
    @Operation(summary = "Bulk-disposition all open alerts for a customer",
            description = "Applies the same action to every OPEN/UNDER_REVIEW alert for the given userId")
    public ResponseEntity<Map<String, Object>> bulkDisposition(
            @RequestParam("userId") Long userId,
            @org.springframework.web.bind.annotation.RequestBody com.remitz.modules.compliance.dto.AlertDispositionRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        Long reviewerId = extractReviewerId(httpRequest);
        int count = complianceDispositionService.bulkDispositionByUser(userId, request, reviewerId);
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", userId);
        body.put("action", request.getAction());
        body.put("dispositioned", count);
        return ResponseEntity.ok(body);
    }

    @PutMapping("/alerts/{id}")
    @PreAuthorize("hasAuthority('compliance:manage_alerts')")
    @Operation(summary = "Update a compliance alert", description = "Update status, assignment, or resolution notes")
    public ResponseEntity<ComplianceAlertResponse> updateAlert(
            @PathVariable Long id,
            @RequestBody AlertUpdateRequest request) {
        return ResponseEntity.ok(complianceAlertService.updateAlert(id, request));
    }

    @GetMapping("/alerts/{id}/details")
    @PreAuthorize("hasAuthority('compliance:view_alerts')")
    @Operation(summary = "Full alert detail with matched list entry and customer info")
    public ResponseEntity<com.remitz.modules.compliance.dto.AlertDetailResponse> alertDetail(@PathVariable Long id) {
        return ResponseEntity.ok(complianceDispositionService.getAlertDetail(id));
    }

    @PostMapping("/alerts/{id}/disposition")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('compliance:file_sar') or hasAuthority('config:manage_corridors')")
    @Operation(summary = "Disposition a compliance alert",
            description = "FALSE_POSITIVE (whitelist + release txn) / CONFIRMED_MATCH / ESCALATE")
    public ResponseEntity<ComplianceAlertResponse> disposition(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody com.remitz.modules.compliance.dto.AlertDispositionRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {

        Long reviewerId = extractReviewerId(httpRequest);
        com.remitz.modules.compliance.entity.ComplianceAlertEntity saved =
                complianceDispositionService.disposition(id, request, reviewerId);

        return ResponseEntity.ok(ComplianceAlertResponse.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .transactionId(saved.getTransactionId())
                .severity(saved.getSeverity())
                .status(saved.getStatus())
                .description(saved.getDescription())
                .details(saved.getDetails())
                .assignedTo(saved.getAssignedTo())
                .resolvedBy(saved.getResolvedBy())
                .resolvedAt(saved.getResolvedAt())
                .resolutionNotes(saved.getResolutionNotes())
                .createdAt(saved.getCreatedAt())
                .updatedAt(saved.getUpdatedAt())
                .build());
    }

    private Long extractReviewerId(jakarta.servlet.http.HttpServletRequest request) {
        try {
            String header = request.getHeader("X-User-Id");
            if (header != null && !header.isBlank()) return Long.parseLong(header);
        } catch (Exception ignore) {}
        try {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                String uuid = auth.getName();
                Long id = jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE uuid = ?", Long.class, uuid);
                if (id != null) return id;
            }
        } catch (Exception ignore) {}
        return 0L;
    }

    // --- Cases ---

    @GetMapping("/cases")
    @Operation(summary = "List compliance cases", description = "List all compliance cases with pagination")
    public ResponseEntity<org.springframework.data.domain.Page<ComplianceCaseResponse>> listCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(complianceCaseService.listCases(
                org.springframework.data.domain.PageRequest.of(page, size,
                        org.springframework.data.domain.Sort.by("createdAt").descending())));
    }

    @PostMapping("/cases")
    @PreAuthorize("hasAuthority('compliance:file_sar')")
    @Operation(summary = "Create a compliance case", description = "Create a case from one or more alerts")
    public ResponseEntity<ComplianceCaseResponse> createCase(@Valid @RequestBody CaseCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceCaseService.createCase(request));
    }

    @GetMapping("/cases/{id}")
    @PreAuthorize("hasAuthority('compliance:view_alerts')")
    @Operation(summary = "Get a compliance case", description = "Retrieve case details including SAR reports")
    public ResponseEntity<ComplianceCaseResponse> getCase(@PathVariable Long id) {
        return ResponseEntity.ok(complianceCaseService.getCase(id));
    }

    @PutMapping("/cases/{id}")
    @PreAuthorize("hasAuthority('compliance:manage_cases')")
    @Operation(summary = "Update a compliance case", description = "Update case status, findings, or assignment")
    public ResponseEntity<ComplianceCaseResponse> updateCase(
            @PathVariable Long id,
            @RequestBody CaseUpdateRequest request) {
        return ResponseEntity.ok(complianceCaseService.updateCase(id, request));
    }

    @PostMapping("/cases/{id}/sar")
    @PreAuthorize("hasAuthority('compliance:file_sar')")
    @Operation(summary = "Create a SAR report", description = "File a Suspicious Activity Report for a case")
    public ResponseEntity<SarReportResponse> createSarReport(
            @PathVariable Long id,
            @Valid @RequestBody SarCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(complianceCaseService.createSarReport(id, request));
    }

    // --- Risk Scoring ---

    @GetMapping("/risk/{entityType}/{id}")
    @PreAuthorize("hasAuthority('compliance:view_alerts')")
    @Operation(summary = "Get risk score", description = "Retrieve the latest risk score for an entity")
    public ResponseEntity<RiskScoreResponse> getRiskScore(
            @PathVariable EntityType entityType,
            @PathVariable Long id) {
        RiskScoreEntity riskScore = riskScoringService.getRiskScore(entityType, id);
        return ResponseEntity.ok(toRiskScoreResponse(riskScore));
    }

    @PostMapping("/risk/{entityType}/{id}")
    @PreAuthorize("hasAuthority('compliance:manage_alerts')")
    @Operation(summary = "Calculate risk score", description = "Calculate and store a new risk score for an entity")
    public ResponseEntity<RiskScoreResponse> calculateRiskScore(
            @PathVariable EntityType entityType,
            @PathVariable Long id,
            @RequestBody Map<String, Object> factors) {
        RiskScoreEntity riskScore = riskScoringService.calculateRiskScore(entityType, id, factors);
        return ResponseEntity.ok(toRiskScoreResponse(riskScore));
    }

    // --- Monitoring Rules ---

    @PostMapping("/rules")
    @PreAuthorize("hasAuthority('compliance:manage_rules')")
    @Operation(summary = "Create a monitoring rule", description = "Admin-only: create a new transaction monitoring rule")
    public ResponseEntity<MonitoringRuleResponse> createRule(@Valid @RequestBody MonitoringRuleRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(monitoringRuleService.createRule(request));
    }

    @GetMapping("/rules")
    @PreAuthorize("hasAuthority('compliance:view_alerts')")
    @Operation(summary = "List monitoring rules", description = "Retrieve all monitoring rules")
    public ResponseEntity<List<MonitoringRuleResponse>> getRules() {
        return ResponseEntity.ok(monitoringRuleService.getAllRules());
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("hasAuthority('compliance:manage_rules')")
    @Operation(summary = "Update a monitoring rule", description = "Admin-only: update an existing monitoring rule")
    public ResponseEntity<MonitoringRuleResponse> updateRule(
            @PathVariable Long id,
            @RequestBody MonitoringRuleRequest request) {
        return ResponseEntity.ok(monitoringRuleService.updateRule(id, request));
    }

    // --- Mappers ---

    private ScreeningResponse toScreeningResponse(ScreeningResultEntity entity) {
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
                .reviewedBy(entity.getReviewedBy())
                .reviewedAt(entity.getReviewedAt())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .hit(isHit)
                .build();
    }

    private RiskScoreResponse toRiskScoreResponse(RiskScoreEntity entity) {
        return RiskScoreResponse.builder()
                .id(entity.getId())
                .entityType(entity.getEntityType())
                .entityId(entity.getEntityId())
                .score(entity.getScore())
                .riskLevel(entity.getRiskLevel())
                .factors(entity.getFactors())
                .calculatedAt(entity.getCalculatedAt())
                .validUntil(entity.getValidUntil())
                .build();
    }
}

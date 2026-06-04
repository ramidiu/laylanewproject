package com.remitz.modules.compliance.controller;

import com.remitz.common.enums.AlertStatus;
import com.remitz.modules.compliance.dto.AuditEntryResponse;
import com.remitz.modules.compliance.entity.CtrReportEntity;
import com.remitz.modules.compliance.entity.SarReportEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import com.remitz.modules.compliance.repository.CtrReportRepository;
import com.remitz.modules.compliance.scheduler.NightlyRescreenScheduler;
import com.remitz.modules.compliance.service.ComplianceAuditService;
import com.remitz.modules.compliance.service.OpenSanctionsIngestService;
import com.remitz.modules.compliance.service.SarContentGeneratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/compliance/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Compliance Admin", description = "Admin-only compliance operations")
public class ComplianceAdminController {

    private final OpenSanctionsIngestService ingestService;
    private final ComplianceAlertRepository alertRepository;
    private final NightlyRescreenScheduler rescreenScheduler;
    private final ComplianceAuditService auditService;
    private final CtrReportRepository ctrReportRepository;
    private final SarContentGeneratorService sarContentGeneratorService;

    @GetMapping("/ctr")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('config:manage_corridors') or hasAuthority('config:manage_system')")
    @Operation(summary = "List CTR drafts",
            description = "Paginated list of Currency Transaction Reports — customers whose daily send volume crossed the regulatory threshold")
    public ResponseEntity<Map<String, Object>> listCtrs(
            @RequestParam(required = false) String filingStatus,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        CtrReportEntity.FilingStatus status = null;
        if (filingStatus != null && !filingStatus.isBlank()) {
            try { status = CtrReportEntity.FilingStatus.valueOf(filingStatus.toUpperCase()); }
            catch (Exception ignored) {}
        }
        LocalDate start = startDate != null && !startDate.isBlank() ? LocalDate.parse(startDate) : null;
        LocalDate end = endDate != null && !endDate.isBlank() ? LocalDate.parse(endDate) : null;

        Sort sort = Sort.by(Sort.Order.desc("reportDate"), Sort.Order.desc("totalAmount"));
        Page<CtrReportEntity> results = ctrReportRepository.findWithFilters(
                status, start, end,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500), sort));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (CtrReportEntity e : results.getContent()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", e.getId());
            row.put("reportDate", e.getReportDate());
            row.put("userId", e.getUserId());
            row.put("userEmail", e.getUserEmail());
            row.put("userName", e.getUserName());
            row.put("transactionCount", e.getTransactionCount());
            row.put("totalAmount", e.getTotalAmount());
            row.put("currency", e.getCurrency());
            row.put("threshold", e.getThreshold());
            row.put("filingStatus", e.getFilingStatus());
            row.put("filedAt", e.getFiledAt());
            row.put("transactionRefs", e.getTransactionRefs());
            row.put("createdAt", e.getCreatedAt());
            rows.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", rows);
        body.put("page", results.getNumber());
        body.put("size", results.getSize());
        body.put("totalElements", results.getTotalElements());
        body.put("totalPages", results.getTotalPages());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/ctr/{id}/submit")
    @PreAuthorize("hasAuthority('compliance:file_sar') or hasAuthority('config:manage_system')")
    @Operation(summary = "Mark a CTR as SUBMITTED",
            description = "Records that the CTR has been filed with the regulator")
    public ResponseEntity<Map<String, Object>> submitCtr(@PathVariable Long id,
                                                          @RequestParam(required = false) String externalReference) {
        CtrReportEntity ctr = ctrReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("CTR not found: " + id));
        ctr.setFilingStatus(CtrReportEntity.FilingStatus.SUBMITTED);
        ctr.setFiledAt(LocalDateTime.now());
        ctr.setFiledBy(currentUserId());
        if (externalReference != null) ctr.setExternalReference(externalReference);
        CtrReportEntity saved = ctrReportRepository.save(ctr);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", saved.getId());
        body.put("filingStatus", saved.getFilingStatus());
        body.put("filedAt", saved.getFiledAt());
        body.put("externalReference", saved.getExternalReference());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/alerts/{id}/generate-sar")
    @PreAuthorize("hasAuthority('compliance:file_sar') or hasAuthority('config:manage_system')")
    @Operation(summary = "Generate a SAR draft from a closed alert",
            description = "Builds a structured JSON SAR payload in DRAFT state for manual review before filing")
    public ResponseEntity<Map<String, Object>> generateSarFromAlert(@PathVariable Long id) {
        SarReportEntity saved = sarContentGeneratorService.generateFromAlert(id, currentUserId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sarReportId", saved.getId());
        body.put("filingStatus", saved.getFilingStatus());
        body.put("reportContent", saved.getReportContent());
        body.put("caseReference", saved.getComplianceCase() != null
                ? saved.getComplianceCase().getCaseReference() : null);
        return ResponseEntity.ok(body);
    }

    private Long currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return null;
            Object principal = auth.getPrincipal();
            if (principal instanceof Number n) return n.longValue();
            if (principal != null) {
                String s = principal.toString();
                try { return Long.parseLong(s); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
        return null;
    }

    @PostMapping("/ingest/run")
    @PreAuthorize("hasAuthority('config:manage_system') or hasAuthority('config:manage_corridors')")
    @Operation(summary = "Run OpenSanctions ingest now",
            description = "Manually trigger an OpenSanctions dataset refresh. Long-running.")
    public ResponseEntity<Map<String, Object>> runIngestNow() {
        log.info("Manual OpenSanctions ingest triggered via admin endpoint");
        OpenSanctionsIngestService.IngestSummary summary = ingestService.runAll();
        return ResponseEntity.ok(toResponse(summary));
    }

    @GetMapping("/ingest/status")
    @PreAuthorize("hasAuthority('config:manage_system') or hasAuthority('config:manage_corridors')")
    @Operation(summary = "Get live OpenSanctions row count")
    public ResponseEntity<Map<String, Object>> status() {
        long count = ingestService.countLiveOpenSanctionsRows();
        Map<String, Object> body = new HashMap<>();
        body.put("liveOpenSanctionsRows", count);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('config:manage_corridors') or hasAuthority('config:manage_system')")
    @Operation(summary = "Compliance disposition audit trail",
            description = "Every closed alert — who cleared whom, when, and why")
    public ResponseEntity<Map<String, Object>> audit(
            @org.springframework.web.bind.annotation.RequestParam(required = false) Long reviewerId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String action,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String startDate,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String endDate,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "0") int page,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "50") int size) {

        LocalDateTime start = parseDate(startDate);
        LocalDateTime end = parseDate(endDate);
        int limit = Math.min(Math.max(size, 1), 500);
        int offset = Math.max(page, 0) * limit;

        List<AuditEntryResponse> rows = auditService.findAudit(reviewerId, action, start, end, limit, offset);
        long total = auditService.countAudit(reviewerId, action, start, end);

        Map<String, Object> body = new HashMap<>();
        body.put("content", rows);
        body.put("page", page);
        body.put("size", limit);
        body.put("totalElements", total);
        body.put("totalPages", (int) Math.ceil((double) total / (double) limit));
        return ResponseEntity.ok(body);
    }

    private LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.length() == 10) {
                return LocalDateTime.parse(raw + "T00:00:00");
            }
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    @PostMapping("/rescreen/run")
    @PreAuthorize("hasAuthority('config:manage_system') or hasAuthority('config:manage_corridors') or hasAuthority('compliance:view_alerts')")
    @Operation(summary = "Run the nightly re-screen now",
            description = "Manually triggers Phase 4: re-screens every active customer against the current sanctions + PEP lists. Skips duplicates and whitelisted pairs automatically.")
    public ResponseEntity<Map<String, Object>> runRescreenNow() {
        log.info("Manual re-screen triggered via admin endpoint");
        NightlyRescreenScheduler.RescreenSummary summary = rescreenScheduler.runRescreen();
        Map<String, Object> body = new HashMap<>();
        body.put("screened", summary.screened);
        body.put("failed", summary.failed);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/metrics")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('config:manage_corridors') or hasAuthority('config:manage_system')")
    @Operation(summary = "Operational metrics for the compliance queue")
    public ResponseEntity<Map<String, Object>> metrics() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime past24h = now.minusHours(24);
        LocalDateTime past30d = now.minusDays(30);

        long openedToday = alertRepository.countByCreatedAtAfter(startOfToday);
        long pendingReview24h = alertRepository.countByStatusInAndCreatedAtAfter(
                Arrays.asList(AlertStatus.OPEN, AlertStatus.UNDER_REVIEW),
                past24h.minusDays(30));
        long closedFp30d = alertRepository.countByStatusAndResolvedAtAfter(
                AlertStatus.CLOSED_FALSE_POSITIVE, past30d);
        long closedSar30d = alertRepository.countByStatusAndResolvedAtAfter(
                AlertStatus.CLOSED_SAR_FILED, past30d);
        long closedNoAction30d = alertRepository.countByStatusAndResolvedAtAfter(
                AlertStatus.CLOSED_NO_ACTION, past30d);
        long totalClosed30d = closedFp30d + closedSar30d + closedNoAction30d;
        Double avgMinutes = alertRepository.avgMinutesToDispositionSince(past30d);
        double falsePositiveRate = totalClosed30d == 0 ? 0.0 :
                (double) closedFp30d / (double) totalClosed30d;

        Map<String, Object> out = new HashMap<>();
        out.put("openedToday", openedToday);
        out.put("pendingReview", pendingReview24h);
        out.put("closedLast30Days", totalClosed30d);
        out.put("closedFalsePositiveLast30Days", closedFp30d);
        out.put("closedConfirmedMatchLast30Days", closedSar30d);
        out.put("meanMinutesToDisposition30d", avgMinutes != null ? avgMinutes : 0.0);
        out.put("falsePositiveRate30d", falsePositiveRate);
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> toResponse(OpenSanctionsIngestService.IngestSummary summary) {
        Map<String, Object> body = new HashMap<>();
        body.put("totalUpserted", summary.totalUpserted);
        body.put("totalSoftDeleted", summary.totalSoftDeleted);
        body.put("totalSkipped", summary.totalSkipped);
        List<Map<String, Object>> datasets = new ArrayList<>();
        for (OpenSanctionsIngestService.DatasetResult r : summary.datasets) {
            Map<String, Object> d = new HashMap<>();
            d.put("datasetId", r.datasetId);
            d.put("upserted", r.upserted);
            d.put("softDeleted", r.softDeleted);
            d.put("skipped", r.skipped);
            d.put("malformed", r.malformed);
            if (r.error != null) d.put("error", r.error);
            datasets.add(d);
        }
        body.put("datasets", datasets);
        return body;
    }
}

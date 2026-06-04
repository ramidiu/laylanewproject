package com.remitz.modules.compliance.scheduler;

import com.remitz.modules.compliance.config.OpenSanctionsProperties;
import com.remitz.modules.compliance.service.OpenSanctionsIngestService;
import com.remitz.modules.user.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OpenSanctionsIngestScheduler {

    private final OpenSanctionsIngestService ingestService;
    private final OpenSanctionsProperties properties;
    // Code added by Naresh: System Controls Phase 5 — runtime on/off for this job.
    private final SystemConfigService systemConfigService;

    /**
     * Code added by Naresh: Read runtime control from system_config with safe fallback.
     * Default TRUE so a missing row preserves the existing behavior of the property gate.
     */
    private boolean jobEnabled() {
        return systemConfigService.getBoolean("jobs.opensanctions_refresh.enabled", true);
    }

    @Scheduled(cron = "${app.compliance.opensanctions.cron:0 0 3 * * *}", zone = "UTC")
    public void dailyIngest() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!jobEnabled()) {
            log.info("OpenSanctions daily ingest skipped: jobs.opensanctions_refresh.enabled=false");
            return;
        }
        log.info("OpenSanctions daily ingest triggered by scheduler");
        OpenSanctionsIngestService.IngestSummary summary = ingestService.runAll();
        log.info("OpenSanctions daily ingest finished: upserted={} softDeleted={} skipped={}",
                summary.totalUpserted, summary.totalSoftDeleted, summary.totalSkipped);
    }

    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void bootstrapIfEmpty() {
        if (!properties.isEnabled()) {
            log.info("OpenSanctions disabled, skipping bootstrap ingest");
            return;
        }
        if (!jobEnabled()) {
            log.info("OpenSanctions bootstrap skipped: jobs.opensanctions_refresh.enabled=false");
            return;
        }
        long existing;
        try {
            existing = ingestService.countLiveOpenSanctionsRows();
        } catch (Exception e) {
            log.warn("OpenSanctions bootstrap count failed, skipping: {}", e.getMessage());
            return;
        }
        if (existing > 0) {
            log.info("OpenSanctions bootstrap skipped: {} live rows already present", existing);
            return;
        }
        log.info("OpenSanctions bootstrap starting (table is empty)");
        try {
            OpenSanctionsIngestService.IngestSummary summary = ingestService.runAll();
            log.info("OpenSanctions bootstrap finished: upserted={} softDeleted={}",
                    summary.totalUpserted, summary.totalSoftDeleted);
        } catch (Exception e) {
            log.error("OpenSanctions bootstrap failed: {}", e.getMessage(), e);
        }
    }
}

package com.remitz.modules.compliance.scheduler;

import com.remitz.common.enums.EntityType;
import com.remitz.modules.compliance.service.SanctionsScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NightlyRescreenScheduler {

    private static final int PAGE_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final SanctionsScreeningService screeningService;

    @Scheduled(cron = "${app.compliance.rescreen.cron:0 0 4 * * *}", zone = "UTC")
    public void nightlyRescreen() {
        RescreenSummary s = runRescreen();
        log.info("Nightly re-screen complete: screened={}, failed={}", s.screened, s.failed);
    }

    public RescreenSummary runRescreen() {
        log.info("Re-screen starting");
        long totalScreened = 0;
        long totalFailed = 0;
        long offset = 0;
        while (true) {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, first_name, last_name, country, date_of_birth " +
                            "FROM users " +
                            "WHERE status = 'ACTIVE' " +
                            "ORDER BY id LIMIT ? OFFSET ?",
                    PAGE_SIZE, offset);
            if (rows.isEmpty()) break;

            for (Map<String, Object> row : rows) {
                Long userId = ((Number) row.get("id")).longValue();
                String first = (String) row.get("first_name");
                String last = (String) row.get("last_name");
                String country = (String) row.get("country");
                Object dobObj = row.get("date_of_birth");
                String dob = dobObj != null ? dobObj.toString() : null;

                String fullName = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
                if (fullName.isBlank()) continue;

                try {
                    screeningService.screen(fullName, country, dob, EntityType.CUSTOMER, userId);
                    totalScreened++;
                } catch (Exception e) {
                    totalFailed++;
                    log.warn("Re-screen failed for userId={}: {}", userId, e.getMessage());
                }
            }

            offset += rows.size();
            if (rows.size() < PAGE_SIZE) break;
        }
        RescreenSummary summary = new RescreenSummary();
        summary.screened = totalScreened;
        summary.failed = totalFailed;
        return summary;
    }

    public static class RescreenSummary {
        public long screened;
        public long failed;
    }
}

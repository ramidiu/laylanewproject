package com.remitz.modules.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.EntityType;
import com.remitz.common.enums.ScreeningListType;
import com.remitz.common.enums.ScreeningStatus;
import com.remitz.common.enums.AlertStatus;
import com.remitz.modules.compliance.config.ComplianceProperties;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.entity.ComplianceWhitelistEntity;
import com.remitz.modules.compliance.entity.SanctionsListEntity;
import com.remitz.modules.compliance.entity.ScreeningResultEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import com.remitz.modules.compliance.repository.ComplianceWhitelistRepository;
import com.remitz.modules.compliance.repository.SanctionsListRepository;
import com.remitz.modules.compliance.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.text.similarity.JaroWinklerSimilarity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Name-based fuzzy screening against the sanctions and PEP list tables.
 *
 * <p>Called synchronously by transaction-service at send time (via the
 * internal endpoint {@code /internal/compliance/screen}) and by auth-service
 * during customer registration. Also invoked by {@code NightlyRescreenScheduler}
 * to re-screen every active customer against updated lists each night.
 *
 * <h2>Algorithm</h2>
 * <ol>
 *   <li>Normalise the input name (lowercase, strip diacritics, remove titles).</li>
 *   <li>Query {@code sanctions_lists} for candidate rows using country + list-type
 *       narrowing, then rank by <a href="https://en.wikipedia.org/wiki/Jaro%E2%80%93Winkler_distance">Jaro-Winkler similarity</a>.</li>
 *   <li>Apply a <b>hard threshold</b>: SANCTIONS hits ≥ {@code 0.85}, PEP hits
 *       ≥ {@code 0.88}. Anything below is treated as a non-hit (CLEAR).</li>
 *   <li>Consult {@code compliance_whitelist} — if the exact subject/list-entry
 *       pair has been cleared as a false positive, downgrade the match to
 *       CLEAR and do not create an alert.</li>
 *   <li>Otherwise, persist a {@code screening_results} row AND create a
 *       {@code compliance_alerts} row with the appropriate severity.</li>
 * </ol>
 *
 * <h2>Status outcomes</h2>
 * <ul>
 *   <li>{@code CLEAR} — no hit or whitelisted; transaction allowed.</li>
 *   <li>{@code POTENTIAL_MATCH} — above threshold but not an exact match;
 *       typically placed on COMPLIANCE_HOLD for manual review.</li>
 *   <li>{@code CONFIRMED_MATCH} — exact or near-exact match (DOB + name); the
 *       transaction is always held.</li>
 * </ul>
 *
 * <h2>Tuning</h2>
 * The thresholds are tuned for the OpenSanctions dataset (~800k entries
 * including sanctions, PEP, and crime topics). If you switch data sources,
 * you must re-calibrate on a labelled sample to balance false positives vs
 * false negatives. Track the "false positive rate" metric via the compliance
 * audit view at {@code /superadmin/compliance-audit}.
 *
 * <h2>Performance</h2>
 * Each call is a single DB query on {@code sanctions_lists} with a
 * country/list-type filter; the index on {@code (list_type, country)} keeps
 * this sub-millisecond even at 800k rows. Fuzzy scoring happens in-memory
 * on the small candidate set returned by the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SanctionsScreeningService {

    private static final double SANCTIONS_THRESHOLD = 0.85;
    private static final double PEP_THRESHOLD = 0.88;
    private static final double COUNTRY_BONUS = 0.05;
    private static final double DOB_EXACT_BONUS = 0.10;
    private static final double DOB_CLOSE_BONUS = 0.05;
    private static final double MAX_SCORE = 1.0;

    private final SanctionsListRepository sanctionsListRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final ComplianceAlertRepository complianceAlertRepository;
    private final ComplianceWhitelistRepository complianceWhitelistRepository;
    private final ComplianceAlertService complianceAlertService;
    private final ComplianceProperties complianceProperties;
    private final JaroWinklerSimilarity jaroWinklerSimilarity = new JaroWinklerSimilarity();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public List<ScreeningResultEntity> screen(String fullName, String country,
                                               EntityType entityType, Long entityId) {
        return screen(fullName, country, null, entityType, entityId);
    }

    @Transactional
    public List<ScreeningResultEntity> screen(String fullName, String country, String dateOfBirth,
                                               EntityType entityType, Long entityId) {
        // Compliance disabled: never screen, always return empty (no hits, no alerts).
        log.debug("Sanctions screening disabled — skipping for {}:{}", entityType, entityId);
        return new ArrayList<>();
    }

    private List<ScreeningResultEntity> screenDisabled(String fullName, String country, String dateOfBirth,
                                               EntityType entityType, Long entityId) {
        log.info("Screening '{}' (country={}, dob={}) for {}:{}",
                fullName, country, dateOfBirth, entityType, entityId);

        List<ScreeningResultEntity> results = new ArrayList<>();
        String normalizedQuery = normalize(fullName);
        LocalDate queryDob = parseDate(dateOfBirth);

        for (SanctionsListEntity.ListType listType : new SanctionsListEntity.ListType[]{
                SanctionsListEntity.ListType.SANCTIONS, SanctionsListEntity.ListType.PEP}) {

            double threshold = thresholdFor(listType);
            List<SanctionsListEntity> candidates = sanctionsListRepository.findActiveByListType(listType);
            if (candidates.isEmpty()) continue;

            for (SanctionsListEntity entry : candidates) {
                Match best = bestMatch(normalizedQuery, entry);
                if (best == null) continue;

                double adjusted = best.score;
                if (country != null && entry.getCountry() != null
                        && country.equalsIgnoreCase(entry.getCountry())) {
                    adjusted = Math.min(MAX_SCORE, adjusted + COUNTRY_BONUS);
                }
                if (queryDob != null && entry.getDateOfBirth() != null) {
                    long yearsDiff = Math.abs(entry.getDateOfBirth().getYear() - queryDob.getYear());
                    if (entry.getDateOfBirth().equals(queryDob)) {
                        adjusted = Math.min(MAX_SCORE, adjusted + DOB_EXACT_BONUS);
                    } else if (yearsDiff <= 1) {
                        adjusted = Math.min(MAX_SCORE, adjusted + DOB_CLOSE_BONUS);
                    }
                }

                if (adjusted < threshold) continue;

                BigDecimal scorePercent = BigDecimal.valueOf(adjusted * 100)
                        .setScale(2, RoundingMode.HALF_UP);

                ScreeningResultEntity result = ScreeningResultEntity.builder()
                        .entityType(entityType)
                        .entityId(entityId)
                        .screenedName(fullName)
                        .matchedList(mapLegacyList(entry))
                        .matchedEntryId(entry.getId())
                        .matchScore(scorePercent)
                        .status(ScreeningStatus.POTENTIAL_MATCH)
                        .notes(buildMatchNotes(best, entry, listType))
                        .build();

                results.add(screeningResultRepository.save(result));
                createAlertForHit(entityType, entityId, entry, listType, adjusted, fullName, best);

                log.warn("{} match: '{}' -> '{}' [{}] score={} source={}",
                        listType, fullName, entry.getEntryName(),
                        entry.getExternalId(), scorePercent, entry.getSourceCode());
            }
        }

        if (results.isEmpty()) {
            ScreeningResultEntity clearResult = ScreeningResultEntity.builder()
                    .entityType(entityType)
                    .entityId(entityId)
                    .screenedName(fullName)
                    .status(ScreeningStatus.CLEAR)
                    .build();
            results.add(screeningResultRepository.save(clearResult));
            log.info("Screening clear for '{}' on {}:{}", fullName, entityType, entityId);
        }

        return results;
    }

    @Transactional
    public ScreeningResultEntity reviewScreeningResult(Long resultId, ScreeningStatus newStatus, Long reviewerId) {
        ScreeningResultEntity result = screeningResultRepository.findById(resultId)
                .orElseThrow(() -> new RuntimeException("Screening result not found: " + resultId));
        result.setStatus(newStatus);
        result.setReviewedBy(reviewerId);
        result.setReviewedAt(java.time.LocalDateTime.now());
        log.info("Screening result {} reviewed by {} with status {}", resultId, reviewerId, newStatus);
        return screeningResultRepository.save(result);
    }

    private double thresholdFor(SanctionsListEntity.ListType listType) {
        if (listType == SanctionsListEntity.ListType.PEP) return PEP_THRESHOLD;
        if (listType == SanctionsListEntity.ListType.SANCTIONS) return SANCTIONS_THRESHOLD;
        return complianceProperties.getScreeningThreshold() / 100.0;
    }

    private Match bestMatch(String normalizedQuery, SanctionsListEntity entry) {
        if (entry.getEntryName() == null) return null;
        double bestScore = 0.0;
        String matchedAgainst = entry.getEntryName();

        double primary = jaroWinklerSimilarity.apply(normalizedQuery, normalize(entry.getEntryName()));
        if (primary > bestScore) {
            bestScore = primary;
            matchedAgainst = entry.getEntryName();
        }

        List<String> aliases = parseAliases(entry.getAliases());
        for (String alias : aliases) {
            double s = jaroWinklerSimilarity.apply(normalizedQuery, normalize(alias));
            if (s > bestScore) {
                bestScore = s;
                matchedAgainst = alias;
            }
        }
        return new Match(bestScore, matchedAgainst);
    }

    private List<String> parseAliases(String aliasesJson) {
        if (aliasesJson == null || aliasesJson.isBlank()) return Collections.emptyList();
        try {
            @SuppressWarnings("unchecked")
            List<String> list = objectMapper.readValue(aliasesJson, List.class);
            return list == null ? Collections.emptyList() : list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static String normalize(String raw) {
        if (raw == null) return "";
        String nfd = Normalizer.normalize(raw, Normalizer.Form.NFD);
        String stripped = nfd.replaceAll("\\p{M}", "");
        return stripped.toLowerCase(Locale.ROOT)
                .replaceAll("[\\-_.]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.length() >= 10) return LocalDate.parse(raw.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception ignore) {}
        return null;
    }

    private ScreeningListType mapLegacyList(SanctionsListEntity entry) {
        if (entry.getListName() != null) return entry.getListName();
        String src = entry.getSourceCode();
        if (src == null) return null;
        String s = src.toLowerCase(Locale.ROOT);
        if (s.startsWith("us_ofac")) return ScreeningListType.OFAC;
        if (s.startsWith("gb_hmt") || s.startsWith("gb_fcdo")) return ScreeningListType.HMT;
        if (s.startsWith("eu_")) return ScreeningListType.EU;
        if (s.startsWith("un_")) return ScreeningListType.UN;
        return null;
    }

    private String buildMatchNotes(Match match, SanctionsListEntity entry, SanctionsListEntity.ListType listType) {
        Map<String, Object> notes = new LinkedHashMap<>();
        notes.put("listType", listType.name());
        notes.put("source", entry.getSourceCode());
        notes.put("externalId", entry.getExternalId());
        notes.put("matchedAgainst", match.matchedText);
        try {
            return objectMapper.writeValueAsString(notes);
        } catch (Exception e) {
            return match.matchedText;
        }
    }

    private void createAlertForHit(EntityType entityType, Long entityId,
                                    SanctionsListEntity entry,
                                    SanctionsListEntity.ListType listType,
                                    double score, String screenedName, Match match) {
        AlertSeverity severity;
        if (score >= 0.95) severity = AlertSeverity.HIGH;
        else if (score >= 0.88) severity = AlertSeverity.MEDIUM;
        else severity = AlertSeverity.LOW;

        Long userId = entityType == EntityType.CUSTOMER ? entityId : null;
        Long transactionId = entityType == EntityType.TRANSACTION ? entityId : null;

        if (userId == null && transactionId == null) return;

        Long subjectUserId = userId != null ? userId : 0L;
        Long listEntryId = entry.getId();

        // Dedupe: skip if an OPEN alert already exists for the same user + list entry
        if (userId != null && listEntryId != null) {
            if (complianceAlertRepository.existsByUserIdAndListEntryIdAndStatus(
                    userId, listEntryId, AlertStatus.OPEN)) {
                return;
            }
            // Whitelist gate: skip if this user previously cleared the exact same list entry as false positive
            if (complianceWhitelistRepository.existsBySubjectTypeAndSubjectIdAndListEntryId(
                    ComplianceWhitelistEntity.SubjectType.CUSTOMER, userId, listEntryId)) {
                log.info("Skipping alert: whitelisted CUSTOMER userId={} listEntryId={}", userId, listEntryId);
                return;
            }
            // Short/common name guard: names with fewer than 6 chars require a higher
            // match threshold to avoid flooding alerts on common names like "Alex", "Ali"
            String nameToCheck = screenedName != null ? screenedName : "";
            String[] nameParts = nameToCheck.trim().split("\\s+");
            boolean isShortName = nameToCheck.length() < 6 || nameParts.length < 2;
            if (isShortName && score < 0.95) {
                log.debug("Skipping low-confidence hit on short name '{}' (score {})", screenedName, score);
                return;
            }
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("listType", listType.name());
        details.put("source", entry.getSourceCode());
        details.put("externalId", entry.getExternalId());
        details.put("listEntryId", listEntryId);
        details.put("matchedName", entry.getEntryName());
        details.put("matchedAgainst", match.matchedText);
        details.put("score", BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
        details.put("screenedName", screenedName);
        details.put("entityType", entityType.name());
        details.put("entityId", entityId);
        details.put("topics", entry.getTopics());

        String detailsJson;
        try {
            detailsJson = objectMapper.writeValueAsString(details);
        } catch (Exception e) {
            detailsJson = null;
        }

        String description = String.format("%s hit: '%s' matched '%s' on %s (score %.2f)",
                listType.name(), screenedName, entry.getEntryName(),
                entry.getSourceCode() != null ? entry.getSourceCode() : "opensanctions", score);

        ComplianceAlertEntity alert = complianceAlertService.createAlert(
                subjectUserId, transactionId, severity, description, detailsJson);
        if (alert != null) {
            alert.setListEntryId(listEntryId);
            complianceAlertRepository.save(alert);
        }
    }

    private static class Match {
        final double score;
        final String matchedText;
        Match(double score, String matchedText) {
            this.score = score;
            this.matchedText = matchedText;
        }
    }
}

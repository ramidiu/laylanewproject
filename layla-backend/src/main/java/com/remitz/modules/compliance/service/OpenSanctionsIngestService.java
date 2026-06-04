package com.remitz.modules.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.modules.compliance.config.OpenSanctionsProperties;
import com.remitz.modules.compliance.dto.OpenSanctionsEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenSanctionsIngestService {

    private static final String UPSERT_SQL = """
            INSERT INTO sanctions_lists
              (external_id, source_code, list_type, list_name, entry_name, entry_type,
               schema_type, aliases, topics, country, nationalities, date_of_birth,
               additional_info, last_updated, last_seen_at, deleted_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NULL)
            ON DUPLICATE KEY UPDATE
              source_code = VALUES(source_code),
              list_type = VALUES(list_type),
              list_name = VALUES(list_name),
              entry_name = VALUES(entry_name),
              entry_type = VALUES(entry_type),
              schema_type = VALUES(schema_type),
              aliases = VALUES(aliases),
              topics = VALUES(topics),
              country = VALUES(country),
              nationalities = VALUES(nationalities),
              date_of_birth = VALUES(date_of_birth),
              additional_info = VALUES(additional_info),
              last_updated = VALUES(last_updated),
              last_seen_at = VALUES(last_seen_at),
              deleted_at = NULL
            """;

    private static final String SOFT_DELETE_SQL = """
            UPDATE sanctions_lists
            SET deleted_at = ?
            WHERE source_code = ?
              AND external_id IS NOT NULL
              AND (last_seen_at IS NULL OR last_seen_at < ?)
              AND deleted_at IS NULL
            """;

    private static final String COUNT_OPENSANCTIONS_SQL =
            "SELECT COUNT(*) FROM sanctions_lists WHERE external_id IS NOT NULL AND deleted_at IS NULL";

    private static final Set<String> INDIVIDUAL_SCHEMAS = Set.of("Person");

    private static final Set<String> ENTITY_SCHEMAS = Set.of(
            "Company", "Organization", "LegalEntity", "PublicBody", "Airplane", "Vessel");

    private static final DateTimeFormatter[] DATE_FORMATS = new DateTimeFormatter[]{
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy"),
            DateTimeFormatter.ofPattern("yyyy-MM")
    };

    private final OpenSanctionsProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public IngestSummary runAll() {
        IngestSummary summary = new IngestSummary();
        if (!properties.isEnabled()) {
            log.info("OpenSanctions ingest disabled via config, skipping");
            return summary;
        }
        for (OpenSanctionsProperties.Dataset dataset : properties.getDatasets()) {
            try {
                DatasetResult result = ingestDataset(dataset);
                summary.datasets.add(result);
                summary.totalUpserted += result.upserted;
                summary.totalSoftDeleted += result.softDeleted;
                summary.totalSkipped += result.skipped;
            } catch (Exception e) {
                log.error("Failed to ingest OpenSanctions dataset '{}': {}", dataset.getId(), e.getMessage(), e);
                DatasetResult failed = new DatasetResult(dataset.getId());
                failed.error = e.getMessage();
                summary.datasets.add(failed);
            }
        }
        return summary;
    }

    public long countLiveOpenSanctionsRows() {
        Long value = jdbcTemplate.queryForObject(COUNT_OPENSANCTIONS_SQL, Long.class);
        return value == null ? 0L : value;
    }

    private DatasetResult ingestDataset(OpenSanctionsProperties.Dataset dataset) throws Exception {
        String datasetId = dataset.getId();
        String defaultListType = dataset.getDefaultListType();
        String url = properties.getBaseUrl() + "/" + datasetId + "/entities.ftm.json";

        log.info("OpenSanctions ingest starting for dataset '{}' from {}", datasetId, url);
        LocalDateTime runStartedAt = LocalDateTime.now();
        Path tempFile = downloadToTempFile(url, datasetId);
        DatasetResult result = new DatasetResult(datasetId);

        try (InputStream in = Files.newInputStream(tempFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {

            List<Object[]> batch = new ArrayList<>(properties.getBatchSize());
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                OpenSanctionsEntity entity;
                try {
                    entity = objectMapper.readValue(line, OpenSanctionsEntity.class);
                } catch (Exception e) {
                    result.malformed++;
                    continue;
                }

                Object[] row = toRow(entity, defaultListType, runStartedAt);
                if (row == null) {
                    result.skipped++;
                    continue;
                }
                batch.add(row);

                if (batch.size() >= properties.getBatchSize()) {
                    result.upserted += flushBatch(batch);
                    batch.clear();
                    if (properties.getMaxEntries() > 0 && result.upserted >= properties.getMaxEntries()) {
                        log.info("Reached maxEntries={}, stopping dataset '{}'",
                                properties.getMaxEntries(), datasetId);
                        break;
                    }
                }
            }
            if (!batch.isEmpty()) {
                result.upserted += flushBatch(batch);
            }
        } finally {
            try { Files.deleteIfExists(tempFile); } catch (Exception ignore) {}
        }

        Timestamp cutoff = Timestamp.valueOf(runStartedAt);
        int deleted = jdbcTemplate.update(SOFT_DELETE_SQL, cutoff, datasetId, cutoff);
        result.softDeleted = deleted;

        log.info("OpenSanctions ingest complete for '{}': upserted={}, softDeleted={}, skipped={}, malformed={}",
                datasetId, result.upserted, result.softDeleted, result.skipped, result.malformed);
        return result;
    }

    private Path downloadToTempFile(String url, String datasetId) throws Exception {
        Files.createDirectories(Path.of(properties.getDownloadDir()));
        Path target = Path.of(properties.getDownloadDir(),
                "opensanctions-" + datasetId + ".ftm.json");
        Path partial = Path.of(target + ".part");

        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(10))
                .GET()
                .build();

        HttpResponse<Path> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofFile(partial));
        if (response.statusCode() >= 400) {
            try { Files.deleteIfExists(partial); } catch (Exception ignore) {}
            throw new IllegalStateException(
                    "Failed to download " + url + ": HTTP " + response.statusCode());
        }
        long size = Files.size(partial);
        if (size <= 0) {
            throw new IllegalStateException("Downloaded file for " + datasetId + " is empty");
        }
        Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        log.info("Downloaded OpenSanctions dataset '{}' ({} bytes) to {}", datasetId, size, target);
        return target;
    }

    private int flushBatch(List<Object[]> batch) {
        int[] counts = jdbcTemplate.batchUpdate(UPSERT_SQL, batch);
        int written = 0;
        for (int c : counts) {
            if (c >= 0) written += 1;
            else if (c == java.sql.Statement.SUCCESS_NO_INFO) written += 1;
        }
        return written;
    }

    private Object[] toRow(OpenSanctionsEntity entity,
                            String defaultListType,
                            LocalDateTime runStartedAt) {
        if (entity == null || entity.getId() == null || entity.getId().isBlank()) {
            return null;
        }

        String schema = entity.getSchema();
        String entryType = classifyEntryType(schema);
        if (entryType == null) {
            return null;
        }

        Map<String, List<Object>> props = entity.getProperties() != null
                ? entity.getProperties()
                : Collections.emptyMap();

        String entryName = firstString(props.get("name"));
        if (entryName == null) entryName = entity.getCaption();
        if (entryName == null || entryName.isBlank()) return null;
        if (entryName.length() > 500) entryName = entryName.substring(0, 500);

        List<String> topicsList = stringList(props.get("topics"));
        String listType = classifyListType(topicsList, defaultListType);

        List<String> aliasesList = new ArrayList<>();
        aliasesList.addAll(stringList(props.get("alias")));
        aliasesList.addAll(stringList(props.get("weakAlias")));
        aliasesList.addAll(stringList(props.get("previousName")));

        List<String> nationalitiesList = stringList(props.get("nationality"));
        if (nationalitiesList.isEmpty()) {
            nationalitiesList = stringList(props.get("country"));
        }
        String country = firstString(props.get("country"));
        if (country == null) country = firstString(props.get("jurisdiction"));
        if (country != null) {
            country = country.toUpperCase(Locale.ROOT);
            if (country.length() > 3) country = country.substring(0, 3);
        }

        LocalDate dob = parseDate(firstString(props.get("birthDate")));

        String sourceCode = entity.getDatasets() != null && !entity.getDatasets().isEmpty()
                ? entity.getDatasets().get(0)
                : null;
        if (sourceCode != null && sourceCode.length() > 64) {
            sourceCode = sourceCode.substring(0, 64);
        }

        String aliasesJson = toJsonArray(aliasesList);
        String topicsJson = toJsonArray(topicsList);
        String nationalitiesJson = toJsonArray(nationalitiesList);

        String externalId = entity.getId();
        if (externalId.length() > 255) externalId = externalId.substring(0, 255);

        Timestamp runTs = Timestamp.valueOf(runStartedAt);

        Object[] row = new Object[15];
        row[0] = externalId;
        row[1] = sourceCode;
        row[2] = listType;
        row[3] = mapLegacyListName(sourceCode, listType);
        row[4] = entryName;
        row[5] = entryType;
        row[6] = schema != null && schema.length() > 40 ? schema.substring(0, 40) : schema;
        row[7] = aliasesJson;
        row[8] = topicsJson;
        row[9] = country;
        row[10] = nationalitiesJson;
        row[11] = dob != null ? Date.valueOf(dob) : null;
        row[12] = null;
        row[13] = runTs;
        row[14] = runTs;
        return row;
    }

    private static String classifyEntryType(String schema) {
        if (schema == null) return null;
        if (INDIVIDUAL_SCHEMAS.contains(schema)) return "INDIVIDUAL";
        if (ENTITY_SCHEMAS.contains(schema)) return "ENTITY";
        return null;
    }

    private static String classifyListType(List<String> topics, String fallback) {
        Set<String> t = topics == null ? Collections.emptySet() : new HashSet<>(topics);
        if (t.stream().anyMatch(x -> x.startsWith("sanction"))) return "SANCTIONS";
        if (t.stream().anyMatch(x -> x.startsWith("role.pep") || x.startsWith("role.rca"))) return "PEP";
        if (t.stream().anyMatch(x -> x.startsWith("crime"))) return "CRIME";
        if (t.stream().anyMatch(x -> x.startsWith("debarment"))) return "DEBARMENT";
        if (fallback != null) return fallback;
        return "OTHER";
    }

    private static String mapLegacyListName(String sourceCode, String listType) {
        if (sourceCode == null) return null;
        String s = sourceCode.toLowerCase(Locale.ROOT);
        if (s.startsWith("us_ofac")) return "OFAC";
        if (s.startsWith("gb_hmt") || s.startsWith("gb_fcdo")) return "HMT";
        if (s.startsWith("eu_")) return "EU";
        if (s.startsWith("un_")) return "UN";
        return null;
    }

    private static String firstString(List<Object> values) {
        if (values == null || values.isEmpty()) return null;
        Object first = values.get(0);
        if (first == null) return null;
        String s = first.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static List<String> stringList(List<Object> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return values.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private static LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String cleaned = raw.length() > 10 ? raw.substring(0, 10) : raw;
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                if (f == DateTimeFormatter.ISO_LOCAL_DATE && cleaned.length() == 10) {
                    return LocalDate.parse(cleaned, f);
                }
                if (cleaned.length() == 4) {
                    return LocalDate.parse(cleaned + "-01-01", DateTimeFormatter.ISO_LOCAL_DATE);
                }
                if (cleaned.length() == 7) {
                    return LocalDate.parse(cleaned + "-01", DateTimeFormatter.ISO_LOCAL_DATE);
                }
            } catch (Exception ignore) {}
        }
        return null;
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            return null;
        }
    }

    public static class DatasetResult {
        public final String datasetId;
        public int upserted = 0;
        public int softDeleted = 0;
        public int skipped = 0;
        public int malformed = 0;
        public String error;

        public DatasetResult(String datasetId) {
            this.datasetId = datasetId;
        }
    }

    public static class IngestSummary {
        public final List<DatasetResult> datasets = new ArrayList<>();
        public int totalUpserted = 0;
        public int totalSoftDeleted = 0;
        public int totalSkipped = 0;
    }
}

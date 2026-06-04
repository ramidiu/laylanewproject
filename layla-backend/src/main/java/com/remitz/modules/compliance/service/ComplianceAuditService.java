package com.remitz.modules.compliance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.modules.compliance.dto.AuditEntryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceAuditService {

    private static final List<String> CLOSED_STATUSES = Arrays.asList(
            "CLOSED_FALSE_POSITIVE",
            "CLOSED_SAR_FILED",
            "CLOSED_NO_ACTION",
            "ESCALATED");

    private static final String SELECT_BASE = """
            SELECT
                a.id AS alert_id,
                a.user_id,
                CONCAT_WS(' ', cu.first_name, cu.last_name) AS customer_name,
                cu.email AS customer_email,
                a.severity,
                a.status,
                a.description,
                a.details,
                a.resolved_by,
                CONCAT_WS(' ', ru.first_name, ru.last_name) AS reviewer_name,
                ru.email AS reviewer_email,
                a.resolution_notes,
                a.resolved_at,
                a.created_at
            FROM compliance_alerts a
            LEFT JOIN users cu ON cu.id = a.user_id
            LEFT JOIN users ru ON ru.id = a.resolved_by
            WHERE a.status IN (:statuses)
              AND (:reviewerId IS NULL OR a.resolved_by = :reviewerId)
              AND (:action IS NULL OR a.status = :action)
              AND (:startDate IS NULL OR a.resolved_at >= :startDate)
              AND (:endDate IS NULL OR a.resolved_at < :endDate)
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<AuditEntryResponse> findAudit(Long reviewerId,
                                                String action,
                                                LocalDateTime startDate,
                                                LocalDateTime endDate,
                                                int limit,
                                                int offset) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("statuses", CLOSED_STATUSES)
                .addValue("reviewerId", reviewerId)
                .addValue("action", action)
                .addValue("startDate", startDate != null ? Timestamp.valueOf(startDate) : null)
                .addValue("endDate", endDate != null ? Timestamp.valueOf(endDate) : null)
                .addValue("limit", limit)
                .addValue("offset", offset);

        String sql = SELECT_BASE + " ORDER BY a.resolved_at DESC, a.updated_at DESC LIMIT :limit OFFSET :offset";

        return jdbc.query(sql, params, (ResultSet rs, int rowNum) -> mapRow(rs));
    }

    public long countAudit(Long reviewerId, String action, LocalDateTime startDate, LocalDateTime endDate) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("statuses", CLOSED_STATUSES)
                .addValue("reviewerId", reviewerId)
                .addValue("action", action)
                .addValue("startDate", startDate != null ? Timestamp.valueOf(startDate) : null)
                .addValue("endDate", endDate != null ? Timestamp.valueOf(endDate) : null);

        String sql = """
                SELECT COUNT(*)
                FROM compliance_alerts a
                WHERE a.status IN (:statuses)
                  AND (:reviewerId IS NULL OR a.resolved_by = :reviewerId)
                  AND (:action IS NULL OR a.status = :action)
                  AND (:startDate IS NULL OR a.resolved_at >= :startDate)
                  AND (:endDate IS NULL OR a.resolved_at < :endDate)
                """;
        Long count = jdbc.queryForObject(sql, params, Long.class);
        return count != null ? count : 0L;
    }

    private AuditEntryResponse mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> parsedDetails = parseDetails(rs.getString("details"));

        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        Timestamp createdAt = rs.getTimestamp("created_at");

        return AuditEntryResponse.builder()
                .alertId(rs.getLong("alert_id"))
                .customerId(rs.getLong("user_id"))
                .customerName(rs.getString("customer_name"))
                .customerEmail(rs.getString("customer_email"))
                .severity(rs.getString("severity"))
                .status(rs.getString("status"))
                .listType(String.valueOf(parsedDetails.getOrDefault("listType", "")))
                .matchedName(String.valueOf(parsedDetails.getOrDefault("matchedName", "")))
                .source(String.valueOf(parsedDetails.getOrDefault("source", "")))
                .description(rs.getString("description"))
                .reviewerId(rs.getObject("resolved_by") != null ? rs.getLong("resolved_by") : null)
                .reviewerName(rs.getString("reviewer_name"))
                .reviewerEmail(rs.getString("reviewer_email"))
                .reason(rs.getString("resolution_notes"))
                .resolvedAt(resolvedAt != null ? resolvedAt.toLocalDateTime() : null)
                .createdAt(createdAt != null ? createdAt.toLocalDateTime() : null)
                .build();
    }

    private Map<String, Object> parseDetails(String raw) {
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}

package com.remitz.modules.auth.controller;

import com.remitz.common.audit.AuditLog;
import com.remitz.common.audit.AuditLogRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Audit Logs Admin", description = "Admin-only audit log listing")
public class AuditLogAdminController {

    private final AuditLogRepository auditLogRepository;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/audit-logs")
    @PreAuthorize("hasAuthority('config:manage_system') or hasAuthority('config:manage_corridors') or hasAuthority('report:view_operational')")
    @Operation(summary = "List audit logs",
            description = "Paginated audit logs merged with login history. Optional action + date range filters, newest first.")
    public ResponseEntity<Map<String, Object>> listAuditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        LocalDateTime start = parseDate(startDate, false);
        LocalDateTime end = parseDate(endDate, true);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        // For filtered queries use a large fetch so merged.size() is an accurate total
        String normalizedActionPre = action != null && !action.isBlank() ? action : null;
        int fetchLimit = normalizedActionPre != null
                ? Math.max(safeSize * (safePage + 1), 2000)
                : safeSize * (safePage + 1);

        List<Map<String, Object>> merged = new ArrayList<>();

        // 1) audit_logs rows
        String normalizedAction = normalizedActionPre;
        Page<AuditLog> auditPage = auditLogRepository.findWithFilters(
                normalizedAction, start, end, PageRequest.of(0, fetchLimit));
        for (AuditLog al : auditPage.getContent()) {
            merged.add(toRow(al.getId(), al.getAction(), al.getUserEmail(), al.getUserRole(),
                    al.getEntityType(), al.getEntityId(), al.getIpAddress(), al.getDescription(),
                    al.getCreatedAt(), "AUDIT"));
        }

        // 2) kyc_audit_log rows (document uploads, status changes, tier upgrades, screening)
        boolean includeKyc = normalizedAction == null
                || normalizedAction.equals("DOCUMENT_UPLOADED")
                || normalizedAction.equals("STATUS_CHANGED")
                || normalizedAction.equals("VERIFICATION_INITIATED")
                || normalizedAction.equals("SCREENING_RUN")
                || normalizedAction.equals("MANUAL_OVERRIDE")
                || normalizedAction.equals("TIER_UPGRADED")
                || normalizedAction.equals("KYC_UPDATE");
        if (includeKyc) {
            StringBuilder kycSql = new StringBuilder(
                    "SELECT k.id AS id, k.user_id, k.action, k.actor_id, k.actor_role, " +
                            "k.details, k.ip_address, k.created_at, " +
                            "u.email AS user_email, CONCAT_WS(' ', u.first_name, u.last_name) AS user_name " +
                            "FROM kyc_audit_log k " +
                            "LEFT JOIN users u ON u.id = k.user_id " +
                            "WHERE 1=1");
            List<Object> kycArgs = new ArrayList<>();
            if (normalizedAction != null && !normalizedAction.equals("KYC_UPDATE")) {
                kycSql.append(" AND k.action = ?");
                kycArgs.add(normalizedAction);
            }
            if (start != null) {
                kycSql.append(" AND k.created_at >= ?");
                kycArgs.add(Timestamp.valueOf(start));
            }
            if (end != null) {
                kycSql.append(" AND k.created_at < ?");
                kycArgs.add(Timestamp.valueOf(end));
            }
            kycSql.append(" ORDER BY k.created_at DESC LIMIT ").append(fetchLimit);

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(kycSql.toString(), kycArgs.toArray());
                for (Map<String, Object> r : rows) {
                    Object createdAt = r.get("created_at");
                    LocalDateTime ts = createdAt instanceof Timestamp
                            ? ((Timestamp) createdAt).toLocalDateTime() : null;
                    Long id = r.get("id") != null ? ((Number) r.get("id")).longValue() : null;
                    String userEmail = (String) r.get("user_email");
                    String userName = (String) r.get("user_name");
                    String actionLabel = (String) r.get("action");
                    String description = buildKycDescription(actionLabel, userName, (String) r.get("details"));
                    merged.add(toRow(id, actionLabel, userEmail, (String) r.get("actor_role"),
                            "KYC", r.get("user_id") != null ? r.get("user_id").toString() : null,
                            (String) r.get("ip_address"), description, ts, "KYC"));
                }
            } catch (Exception e) {
                log.warn("kyc_audit_log merge failed: {}", e.getMessage());
            }
        }

        // 3) transaction_status_history — non-SYSTEM actor status changes
        boolean includeTxnStatus = normalizedAction == null || normalizedAction.equals("TRANSACTION_STATUS_CHANGE");
        if (includeTxnStatus) {
            StringBuilder txSql = new StringBuilder(
                    "SELECT tsh.id, tsh.transaction_id, tsh.from_status, tsh.to_status, " +
                            "tsh.actor_type, tsh.actor_id, tsh.reason, tsh.ip_address, tsh.created_at, " +
                            "t.reference_number, u.email AS actor_email " +
                            "FROM transaction_status_history tsh " +
                            "LEFT JOIN transactions t ON t.id = tsh.transaction_id " +
                            "LEFT JOIN users u ON u.id = tsh.actor_id " +
                            "WHERE tsh.actor_type != 'SYSTEM'");
            List<Object> txArgs = new ArrayList<>();
            if (start != null) {
                txSql.append(" AND tsh.created_at >= ?");
                txArgs.add(Timestamp.valueOf(start));
            }
            if (end != null) {
                txSql.append(" AND tsh.created_at < ?");
                txArgs.add(Timestamp.valueOf(end));
            }
            txSql.append(" ORDER BY tsh.created_at DESC LIMIT ").append(fetchLimit);
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(txSql.toString(), txArgs.toArray());
                for (Map<String, Object> r : rows) {
                    Object createdAt = r.get("created_at");
                    LocalDateTime ts = createdAt instanceof Timestamp ? ((Timestamp) createdAt).toLocalDateTime() : null;
                    Long id = r.get("id") != null ? ((Number) r.get("id")).longValue() : null;
                    String ref = (String) r.get("reference_number");
                    String fromSt = (String) r.get("from_status");
                    String toSt = (String) r.get("to_status");
                    String actorEmail = (String) r.get("actor_email");
                    String actorType = (String) r.get("actor_type");
                    String desc = "Transaction " + (ref != null ? ref : "#" + r.get("transaction_id")) +
                            " status changed" +
                            (fromSt != null ? " from " + fromSt : "") +
                            " to " + toSt +
                            (r.get("reason") != null ? " — " + r.get("reason") : "");
                    merged.add(toRow(id, "TRANSACTION_STATUS_CHANGE", actorEmail, actorType,
                            "TRANSACTION", r.get("transaction_id") != null ? r.get("transaction_id").toString() : null,
                            (String) r.get("ip_address"), desc, ts, "TRANSACTION"));
                }
            } catch (Exception e) {
                log.warn("transaction_status_history merge failed: {}", e.getMessage());
            }
        }

        // 4) system_config_audit — FX rate and config changes
        boolean includeConfig = normalizedAction == null || normalizedAction.equals("CONFIG_CHANGE");
        if (includeConfig) {
            StringBuilder cfgSql = new StringBuilder(
                    "SELECT id, config_key, old_value, new_value, changed_by, changed_at, change_source, reason " +
                            "FROM system_config_audit WHERE 1=1");
            List<Object> cfgArgs = new ArrayList<>();
            if (start != null) {
                cfgSql.append(" AND changed_at >= ?");
                cfgArgs.add(Timestamp.valueOf(start));
            }
            if (end != null) {
                cfgSql.append(" AND changed_at < ?");
                cfgArgs.add(Timestamp.valueOf(end));
            }
            cfgSql.append(" ORDER BY changed_at DESC LIMIT ").append(fetchLimit);
            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(cfgSql.toString(), cfgArgs.toArray());
                for (Map<String, Object> r : rows) {
                    Object changedAt = r.get("changed_at");
                    LocalDateTime ts = changedAt instanceof Timestamp ? ((Timestamp) changedAt).toLocalDateTime() : null;
                    Long id = r.get("id") != null ? ((Number) r.get("id")).longValue() : null;
                    String configKey = (String) r.get("config_key");
                    String oldVal = (String) r.get("old_value");
                    String newVal = (String) r.get("new_value");
                    String changedBy = (String) r.get("changed_by");
                    String desc = "Config '" + configKey + "' changed" +
                            (oldVal != null ? " from [" + truncate(oldVal, 80) + "]" : "") +
                            " to [" + truncate(newVal, 80) + "]" +
                            (r.get("reason") != null ? " — " + r.get("reason") : "");
                    merged.add(toRow(id, "CONFIG_CHANGE", changedBy, "ADMIN",
                            "CONFIG", configKey, null, desc, ts, "CONFIG"));
                }
            } catch (Exception e) {
                log.warn("system_config_audit merge failed: {}", e.getMessage());
            }
        }

        // 5) login_history rows for login/logout events
        boolean isLoginCategory = normalizedAction != null &&
                (normalizedAction.equals("LOGIN") || normalizedAction.equals("LOGOUT") || normalizedAction.equals("ALL_LOGINS"));
        boolean includeLogin = normalizedAction == null
                || normalizedAction.contains("LOGIN") || normalizedAction.contains("LOGOUT");
        if (includeLogin) {
            StringBuilder sql = new StringBuilder(
                    "SELECT id, user_id, user_email, user_role, event_type, ip_address, user_agent, created_at " +
                            "FROM login_history WHERE 1=1");
            List<Object> args = new ArrayList<>();
            if (normalizedAction != null) {
                if (isLoginCategory) {
                    // category filter — match all login/logout events
                    sql.append(" AND event_type LIKE ?");
                    args.add("%" + normalizedAction + "%");
                } else {
                    sql.append(" AND event_type = ?");
                    args.add(normalizedAction);
                }
            }
            if (start != null) {
                sql.append(" AND created_at >= ?");
                args.add(Timestamp.valueOf(start));
            }
            if (end != null) {
                sql.append(" AND created_at < ?");
                args.add(Timestamp.valueOf(end));
            }
            sql.append(" ORDER BY created_at DESC LIMIT ").append(fetchLimit);

            try {
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), args.toArray());
                for (Map<String, Object> r : rows) {
                    Object createdAt = r.get("created_at");
                    LocalDateTime ts = createdAt instanceof Timestamp
                            ? ((Timestamp) createdAt).toLocalDateTime() : null;
                    Long id = r.get("id") != null ? ((Number) r.get("id")).longValue() : null;
                    merged.add(toRow(id, (String) r.get("event_type"),
                            (String) r.get("user_email"), (String) r.get("user_role"),
                            "USER", r.get("user_id") != null ? r.get("user_id").toString() : null,
                            (String) r.get("ip_address"),
                            "Login event: " + r.get("event_type"),
                            ts, "LOGIN"));
                }
            } catch (Exception e) {
                log.warn("login_history merge failed: {}", e.getMessage());
            }
        }

        merged.sort(Comparator.comparing(
                (Map<String, Object> m) -> (LocalDateTime) m.get("createdAt"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        // Use merged size as total when filtered (accurate), else count from DB for unfiltered
        long totalElements;
        if (normalizedAction != null) {
            totalElements = merged.size();
        } else {
            // Count each source accurately for unfiltered view
            long auditCount = auditLogRepository.findWithFilters(null, start, end, PageRequest.of(0, 1)).getTotalElements();
            long kycCount = countQuery("SELECT COUNT(*) FROM kyc_audit_log WHERE 1=1", start, end, "created_at");
            long txnCount = countQuery("SELECT COUNT(*) FROM transaction_status_history WHERE actor_type != 'SYSTEM'", start, end, "created_at");
            long cfgCount = countQuery("SELECT COUNT(*) FROM system_config_audit WHERE 1=1", start, end, "changed_at");
            long loginCount = countQuery("SELECT COUNT(*) FROM login_history WHERE 1=1", start, end, "created_at");
            totalElements = auditCount + kycCount + txnCount + cfgCount + loginCount;
        }
        int from = Math.min(safePage * safeSize, (int) Math.min(totalElements, Integer.MAX_VALUE));
        int to = Math.min(from + safeSize, merged.size());
        List<Map<String, Object>> pageContent = merged.subList(from, Math.min(to, merged.size()));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("content", pageContent);
        body.put("page", safePage);
        body.put("size", safeSize);
        body.put("totalElements", totalElements);
        body.put("totalPages", (int) Math.ceil((double) totalElements / (double) safeSize));
        return ResponseEntity.ok(body);
    }

    private long countQuery(String baseSql, LocalDateTime start, LocalDateTime end, String tsCol) {
        try {
            StringBuilder sql = new StringBuilder(baseSql);
            List<Object> args = new ArrayList<>();
            if (start != null) { sql.append(" AND ").append(tsCol).append(" >= ?"); args.add(Timestamp.valueOf(start)); }
            if (end != null)   { sql.append(" AND ").append(tsCol).append(" < ?");  args.add(Timestamp.valueOf(end));   }
            Long count = jdbcTemplate.queryForObject(sql.toString(), Long.class, args.toArray());
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("countQuery failed for '{}': {}", baseSql, e.getMessage());
            return 0L;
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) + "…" : s;
    }

    private String buildKycDescription(String action, String userName, String detailsJson) {
        String subject = userName != null && !userName.isBlank() ? userName : "customer";
        String verb;
        switch (action != null ? action : "") {
            case "DOCUMENT_UPLOADED":
                verb = "uploaded a KYC document";
                break;
            case "STATUS_CHANGED":
                verb = "KYC document status changed";
                break;
            case "VERIFICATION_INITIATED":
                verb = "KYC verification started";
                break;
            case "SCREENING_RUN":
                verb = "sanction / PEP screening run";
                break;
            case "MANUAL_OVERRIDE":
                verb = "KYC manually overridden";
                break;
            case "TIER_UPGRADED":
                verb = "KYC tier upgraded";
                break;
            default:
                verb = "KYC event (" + action + ")";
        }
        String base = verb + " for " + subject;
        if (detailsJson != null && !detailsJson.isBlank()) {
            String trimmed = detailsJson.length() > 200 ? detailsJson.substring(0, 200) + "…" : detailsJson;
            return base + " — " + trimmed;
        }
        return base;
    }

    private Map<String, Object> toRow(Long id, String actionName, String email, String role,
                                        String entityType, String entityId, String ip,
                                        String description, LocalDateTime createdAt, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("action", actionName);
        m.put("userEmail", email);
        m.put("userRole", role);
        m.put("entityType", entityType);
        m.put("entityId", entityId);
        m.put("ipAddress", ip);
        m.put("description", description);
        m.put("createdAt", createdAt);
        m.put("source", source);
        return m;
    }

    private LocalDateTime parseDate(String raw, boolean endOfDay) {
        if (raw == null || raw.isBlank()) return null;
        try {
            if (raw.length() == 10) {
                return endOfDay
                        ? LocalDateTime.parse(raw + "T23:59:59")
                        : LocalDateTime.parse(raw + "T00:00:00");
            }
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            log.debug("Could not parse date '{}': {}", raw, e.getMessage());
            return null;
        }
    }
}

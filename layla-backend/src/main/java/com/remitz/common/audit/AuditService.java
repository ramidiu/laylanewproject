package com.remitz.common.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepo;
    private final AccessLogRepository accessLogRepo;
    private final LoginHistoryRepository loginHistoryRepo;

    @Async
    public void logAudit(Long userId, String email, String role, String serviceName,
                         String action, String entityType, String entityId,
                         String description, String oldValue, String newValue,
                         String ipAddress, String userAgent) {
        try {
            auditLogRepo.save(AuditLog.builder()
                    .userId(userId)
                    .userEmail(email)
                    .userRole(role)
                    .serviceName(serviceName)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .description(description)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .ipAddress(ipAddress)
                    .userAgent(truncate(userAgent, 500))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }

    @Async
    public void logAccess(Long userId, String email, String role, String serviceName,
                          String httpMethod, String endpoint, String queryParams,
                          Integer responseStatus, Long responseTimeMs,
                          String ipAddress, String userAgent) {
        try {
            accessLogRepo.save(AccessLog.builder()
                    .userId(userId)
                    .userEmail(email)
                    .userRole(role)
                    .serviceName(serviceName)
                    .httpMethod(httpMethod)
                    .endpoint(endpoint)
                    .queryParams(queryParams)
                    .responseStatus(responseStatus)
                    .responseTimeMs(responseTimeMs)
                    .ipAddress(ipAddress)
                    .userAgent(truncate(userAgent, 500))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write access log: {}", e.getMessage());
        }
    }

    @Async
    public void logLogin(Long userId, String email, String role, String eventType,
                         String ipAddress, String userAgent) {
        try {
            loginHistoryRepo.save(LoginHistory.builder()
                    .userId(userId)
                    .userEmail(email)
                    .userRole(role)
                    .eventType(eventType)
                    .ipAddress(ipAddress)
                    .userAgent(truncate(userAgent, 500))
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write login history: {}", e.getMessage());
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}

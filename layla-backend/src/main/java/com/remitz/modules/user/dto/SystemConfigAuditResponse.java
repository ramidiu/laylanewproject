package com.remitz.modules.user.dto;

import com.remitz.modules.user.entity.SystemConfigAudit;

import java.time.LocalDateTime;

/**
 * Code added by Naresh: System Controls Phase 3 — safe outbound shape for audit history.
 * Flattens the entity into a record the UI can render directly.
 */
public record SystemConfigAuditResponse(
        Long id,
        String configKey,
        String oldValue,
        String newValue,
        Integer oldVersion,
        Integer newVersion,
        String changedBy,
        LocalDateTime changedAt,
        String changeSource,
        String reason
) {

    public static SystemConfigAuditResponse from(SystemConfigAudit e) {
        return new SystemConfigAuditResponse(
                e.getId(),
                e.getConfigKey(),
                e.getOldValue(),
                e.getNewValue(),
                e.getOldVersion(),
                e.getNewVersion(),
                e.getChangedBy(),
                e.getChangedAt(),
                e.getChangeSource(),
                e.getReason()
        );
    }
}

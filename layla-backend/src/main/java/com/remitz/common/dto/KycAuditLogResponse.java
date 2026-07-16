package com.remitz.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One KYC audit-trail event for the admin panel — captures what happened, who did it (role),
 * from which IP, and when. Sourced from kyc_audit_log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycAuditLogResponse {
    private Long id;
    private String action;      // DOCUMENT_UPLOADED / STATUS_CHANGED / TIER_UPGRADED / ...
    private Long actorId;
    private String actorRole;   // CUSTOMER / ADMIN / ... (null for system events)
    private String ipAddress;
    private String details;     // raw JSON details (documentId, newStatus, newTier, etc.)
    private LocalDateTime createdAt;
}

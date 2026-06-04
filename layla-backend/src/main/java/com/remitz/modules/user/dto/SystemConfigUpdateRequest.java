package com.remitz.modules.user.dto;

/**
 * Code added by Naresh: System Controls Phase 2 — payload for
 * PUT /api/users/admin/system-config/{key}.
 *
 * {@code value} is mandatory. {@code version} is optional so the existing UI
 * (which may not always send a version) keeps working; newer clients can opt in
 * to optimistic-locking by sending the version they fetched.
 * {@code reason} is optional (Phase 7) and captures operator intent for
 * dangerous toggles. Persisted into system_config_audit.reason.
 */
public record SystemConfigUpdateRequest(
        String value,
        Integer version,
        String reason
) {
}

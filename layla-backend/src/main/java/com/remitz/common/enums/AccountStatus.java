package com.remitz.common.enums;

/**
 * Lifecycle of a customer account with respect to deletion requests
 * (Google Play Account Deletion policy compliance).
 *
 * <ul>
 *   <li>{@code ACTIVE} — normal account.</li>
 *   <li>{@code DELETE_REQUESTED} — user asked for deletion; access disabled,
 *       records retained for the legal AML/KYC/tax retention period.</li>
 *   <li>{@code DELETED} — account hard-purged after the retention period.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    DELETE_REQUESTED,
    DELETED
}

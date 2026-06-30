package com.remitz.common.enums;

/**
 * KYC tiers:
 *   TIER_1 — base level, account created, ZERO documents (every new user starts here).
 *   TIER_2 — identity document + proof of address approved → "verified".
 *   TIER_3 — additionally source of funds approved.
 *
 * TIER_0 is retained ONLY for backward compatibility with historic rows and the DB enum;
 * it is never assigned by current code (migrated to TIER_1) and is treated as unverified.
 */
public enum KycTier {
    TIER_0,
    TIER_1,
    TIER_2,
    TIER_3;

    /** A customer is "verified" once identity + proof of address are approved (TIER_2 and above). */
    public boolean isVerified() {
        return this == TIER_2 || this == TIER_3;
    }
}

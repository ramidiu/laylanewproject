package com.remitz.modules.payin.kuber.dto;

import java.math.BigDecimal;

/**
 * Code added by Naresh: Phase 3 — minimal DEV-only input shape for the
 * POST /api/dev/kuber/checkout-test smoke endpoint. Not intended for production use.
 */
public record KuberCheckoutTestRequest(
        String fullName,
        String email,
        String mobile,
        BigDecimal amount
) {
}

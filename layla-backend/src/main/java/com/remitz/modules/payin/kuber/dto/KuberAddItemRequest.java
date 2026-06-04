package com.remitz.modules.payin.kuber.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Code added by Naresh: Phase 3 — request body for POST /addItem.
 * eventDateTime is unix-seconds as per Kuber spec sample (1773635223).
 */
public record KuberAddItemRequest(
        List<KuberAddItemMenuItem> menuList,
        String eventName,
        long eventDateTime,
        BigDecimal amount
) {
}

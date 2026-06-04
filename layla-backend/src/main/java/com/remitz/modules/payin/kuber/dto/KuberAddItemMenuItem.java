package com.remitz.modules.payin.kuber.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;

/**
 * Code added by Naresh: Phase 3 — one line item inside the addItem menuList array.
 * Field casing matches the Kuber spec exactly (lowercase "postal", camelCase keys).
 * The event-shop semantics (aboutEvent, perTicketPrice) are Kuber quirks and must not
 * leak outside this module.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record KuberAddItemMenuItem(
        String firstName,
        String lastName,
        String email,
        String mobile,
        String streetNumber,
        String streetName,
        String city,
        String state,
        String postal,
        String country,
        String aboutEvent,
        int quantity,
        BigDecimal perTicketPrice
) {
}

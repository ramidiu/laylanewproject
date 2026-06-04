package com.remitz.modules.payin.kuber.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Code added by Naresh: Phase 3 — response wrapper for POST /addItem.
 * Kuber returns { "message": "...", "data": { "orderID": "...", "redirectURL": "..." } }.
 * Unknown fields are tolerated so future additions don't break parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KuberAddItemResponse(
        String message,
        Data data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String orderID, String redirectURL) {
    }
}

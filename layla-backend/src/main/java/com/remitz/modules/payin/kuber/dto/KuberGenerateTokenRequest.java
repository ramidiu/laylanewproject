package com.remitz.modules.payin.kuber.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Code added by Naresh: Phase 2 — request body for POST /generateToken.
 * Field names (merchantID, deviceID) match the Kuber spec exactly (camel-case "ID" suffix).
 */
public record KuberGenerateTokenRequest(
        @JsonProperty("merchantID") String merchantID,
        @JsonProperty("deviceID") String deviceID
) {
}

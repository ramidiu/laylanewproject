package com.remitz.modules.payin.kuber.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Code added by Naresh: Phase 2 — response wrapper for POST /generateToken.
 * Kuber returns { "message": "...", "data": { "token": "JWT_TOKEN" } }.
 * Unknown top-level fields are tolerated so future server-side additions don't break parsing.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KuberGenerateTokenResponse(
        String message,
        Data data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String token) {
    }
}

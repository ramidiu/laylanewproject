package com.remitz.modules.auth.controller;

import com.remitz.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@Slf4j
@Hidden
public class SwaggerConfigController {

    private final SecretKey signingKey;

    private static final Map<String, Set<String>> ROLE_PATH_ACCESS = Map.ofEntries(
            Map.entry("SUPER_ADMIN", Set.of("/api/")),
            Map.entry("ADMIN", Set.of("/api/")),
            Map.entry("CUSTOMER", Set.of(
                    "/api/auth/login", "/api/auth/register", "/api/auth/verify-otp",
                    "/api/auth/resend-otp", "/api/auth/refresh", "/api/auth/logout",
                    "/api/auth/forgot-password", "/api/auth/reset-password",
                    "/api/auth/change-password", "/api/auth/check-email",
                    "/api/auth/verify-mfa", "/api/auth/mfa/setup",
                    "/api/users/", "/api/transactions/", "/api/beneficiaries/",
                    "/api/fx/quote", "/api/fx/rates", "/api/corridors/",
                    "/api/notifications/", "/api/wallet/", "/api/recurring-transfers/"
            )),
            Map.entry("COMPLIANCE_OFFICER", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/compliance/", "/api/auth/admin/")),
            Map.entry("TREASURY_MANAGER", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/fx/", "/api/corridors/")),
            Map.entry("PAYOUT_PARTNER", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/transactions/partners/", "/api/transactions/settlement/")),
            Map.entry("PAYIN_PARTNER", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/transactions/corridors/", "/api/transactions/settlement/")),
            Map.entry("AGENT", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/transactions/", "/api/beneficiaries/", "/api/users/")),
            Map.entry("SUPPORT", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/users/", "/api/transactions/", "/api/support/", "/api/notifications/")),
            Map.entry("FINANCE", Set.of("/api/auth/login", "/api/auth/logout",
                    "/api/transactions/settlement/", "/api/transactions/admin/"))
    );

    public SwaggerConfigController(JwtProperties jwtProperties) {
        this.signingKey = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/swagger-config")
    public ResponseEntity<Map<String, Object>> getFilteredSwaggerConfig(HttpServletRequest request) {
        Set<String> userRoles = extractRolesFromToken(request);

        Set<String> allowedPrefixes = new HashSet<>();
        for (String role : userRoles) {
            Set<String> paths = ROLE_PATH_ACCESS.getOrDefault(role, Set.of());
            allowedPrefixes.addAll(paths);
        }

        if (allowedPrefixes.isEmpty()) {
            allowedPrefixes = Set.of("/api/auth/login", "/api/auth/register",
                    "/api/auth/check-email", "/api/auth/forgot-password");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roles", userRoles);
        result.put("allowedPathPrefixes", allowedPrefixes);
        result.put("message", userRoles.isEmpty()
                ? "Unauthenticated — showing public endpoints only. Login and pass your JWT to see all endpoints for your role."
                : "Filtered for roles: " + String.join(", ", userRoles));

        return ResponseEntity.ok(result);
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractRolesFromToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Set.of();
        }
        try {
            String token = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            List<String> roles = claims.get("roles", List.class);
            return roles != null ? new HashSet<>(roles) : Set.of();
        } catch (Exception e) {
            log.debug("Failed to parse JWT for swagger-config: {}", e.getMessage());
            return Set.of();
        }
    }
}

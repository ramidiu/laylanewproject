package com.remitz.common.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.modules.user.service.SystemConfigService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code added by Naresh: System Controls Phase 7 — maintenance-mode gate.
 *
 * When {@code maintenance.mode.enabled=true}, this filter returns HTTP 503 with
 * the operator-configured {@code maintenance.message} for customer-facing write
 * requests. Allow-listed paths (auth, admin, actuator, swagger, system-config)
 * and all GET / HEAD / OPTIONS requests bypass the block so the admin panel and
 * observability endpoints remain usable.
 *
 * Runs before {@code JwtAuthenticationFilter} so responses are produced before
 * authentication work happens.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
@Slf4j
public class MaintenanceModeFilter extends OncePerRequestFilter {

    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Read runtime control from system_config with safe fallback. Default FALSE preserves
        // normal behavior when the row is missing.
        if (!systemConfigService.getBoolean("maintenance.mode.enabled", false)) {
            chain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();
        String path = request.getRequestURI();

        if (isAllowed(method, path)) {
            chain.doFilter(request, response);
            return;
        }

        String message = systemConfigService.getString(
                "maintenance.message",
                "System maintenance is currently in progress. Please try again later.");

        log.info("Maintenance mode blocked {} {}", method, path);

        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Service Unavailable");
        body.put("message", message);
        body.put("maintenanceMode", true);
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("path", path);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isAllowed(String method, String path) {
        // Safe methods always pass.
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)
                || "OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }
        // Allow-list covers: auth, admin, super-admin system-config, actuator, swagger, dev.
        return path.startsWith("/api/auth/")
                || path.startsWith("/api/admin/")
                || path.startsWith("/api/users/admin/")
                || path.startsWith("/api/dev/")
                || path.startsWith("/actuator/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/api-docs");
    }
}

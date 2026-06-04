package com.remitz.security;

import com.remitz.security.JwtService;
import com.remitz.modules.auth.service.TokenBlacklistService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest effectiveRequest = request;

        try {
            String token = extractTokenFromRequest(request);

            if (StringUtils.hasText(token) && !tokenBlacklistService.isBlacklisted(token)) {
                Claims claims = jwtService.validateToken(token);

                if (claims != null) {
                    String userUuid = claims.getSubject();

                    @SuppressWarnings("unchecked")
                    List<String> permissions = claims.get("permissions", List.class);
                    if (permissions == null) {
                        permissions = Collections.emptyList();
                    }
                    @SuppressWarnings("unchecked")
                    List<String> roles = claims.get("roles", List.class);
                    if (roles == null) {
                        roles = Collections.emptyList();
                    }

                    // Code added by Naresh: roles are also exposed as Spring Security authorities
                    // with ROLE_ prefix so @PreAuthorize("hasRole('PAYIN_PARTNER')") resolves correctly.
                    List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
                    permissions.stream()
                            .map(SimpleGrantedAuthority::new)
                            .forEach(authorities::add);
                    roles.stream()
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                            .forEach(authorities::add);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userUuid, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // Code added by Naresh: inject X-User-* request headers from JWT claims so
                    // downstream controllers (PayoutPartnerController, PayinPartnerController,
                    // SupportTicketController, ComplianceController, AuthController MFA endpoints)
                    // can resolve caller identity in the monolith without a Spring Cloud Gateway.
                    Map<String, String> injected = new HashMap<>();
                    if (userUuid != null) injected.put("X-User-UUID", userUuid);
                    Object emailClaim = claims.get("email");
                    if (emailClaim != null) injected.put("X-User-Email", emailClaim.toString());
                    Object userIdClaim = claims.get("userId");
                    if (userIdClaim != null) injected.put("X-User-Id", userIdClaim.toString());
                    if (!injected.isEmpty()) {
                        effectiveRequest = new HeaderInjectingRequestWrapper(request, injected);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(effectiveRequest, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * Code added by Naresh: wraps the incoming request so that getHeader()/getHeaders() return
     * the JWT-derived values for X-User-UUID, X-User-Email, X-User-Id when the client didn't
     * provide them. Client-sent headers with the same names are ignored (JWT is authoritative).
     */
    private static class HeaderInjectingRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> overrides;

        HeaderInjectingRequestWrapper(HttpServletRequest request, Map<String, String> overrides) {
            super(request);
            this.overrides = overrides;
        }

        @Override
        public String getHeader(String name) {
            for (Map.Entry<String, String> e : overrides.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) return e.getValue();
            }
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            for (Map.Entry<String, String> e : overrides.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return Collections.enumeration(Collections.singletonList(e.getValue()));
                }
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            Enumeration<String> original = super.getHeaderNames();
            while (original != null && original.hasMoreElements()) {
                names.add(original.nextElement());
            }
            names.addAll(overrides.keySet());
            return Collections.enumeration(names);
        }
    }
}

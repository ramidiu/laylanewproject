package com.remitz.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SECURITY (SEC-15): fixed-window rate limiting for abuse-/cost-sensitive endpoints (auth brute-force,
 * OTP/SMS abuse, email enumeration, paid address lookups, public FX quotes). Distributed via the
 * Redis the application already runs (no new dependency). Fails OPEN on any Redis error so it can
 * never take the app down; runs before Spring Security so blocked requests never reach authentication.
 *
 * Login is limited on TWO keys (defence in depth): a broad per-IP bucket (catches one IP trying many
 * accounts / enumeration) AND a strict per-IP+email bucket (protects a single account from brute force
 * and stops one abuser from exhausting a shared-NAT bucket for everyone). The e-mail is read from the
 * request body via a cached-body wrapper so the downstream controller can still read the body.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final StringRedisTemplate redis;

    @Value("${app.rate-limit.enabled:true}")
    private boolean enabled;

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    private static final Pattern EMAIL_IN_BODY = Pattern.compile("\"email\"\\s*:\\s*\"([^\"]+)\"");

    private enum KeyBy { IP, IP_EMAIL }

    private record Rule(String id, String method, String pattern, int limit, int windowSeconds, KeyBy keyBy) {}

    private static final List<Rule> RULES = List.of(
            // Login — broad per-IP AND strict per-IP+email
            new Rule("login-ip",      "POST", "/api/auth/login",             30, 300, KeyBy.IP),
            new Rule("login-email",   "POST", "/api/auth/login",              5, 300, KeyBy.IP_EMAIL),
            new Rule("admin-login-ip","POST", "/api/auth/admin/login",       20, 300, KeyBy.IP),
            new Rule("admin-login-em","POST", "/api/auth/admin/login",        5, 300, KeyBy.IP_EMAIL),
            new Rule("verify-otp",    "POST", "/api/auth/verify-otp",         10, 300, KeyBy.IP),
            new Rule("verify-mfa",    "POST", "/api/auth/verify-mfa",         10, 300, KeyBy.IP),
            new Rule("resend-otp",    "POST", "/api/auth/resend-otp",          4, 300, KeyBy.IP),
            new Rule("forgot-pw",     "POST", "/api/auth/forgot-password",     5, 900, KeyBy.IP),
            new Rule("reset-pw",      "POST", "/api/auth/reset-password",      8, 900, KeyBy.IP),
            new Rule("register",      "POST", "/api/auth/register",            8, 3600, KeyBy.IP),
            new Rule("check-email",   "GET",  "/api/auth/check-email",        40, 300, KeyBy.IP),
            new Rule("fx-quote",      "POST", "/api/fx/quote",               120,  60, KeyBy.IP),
            new Rule("address",       "GET",  "/api/address/**",              40,  60, KeyBy.IP),
            new Rule("volume-intent", "POST", "/api/volume/payment-intent",   15, 300, KeyBy.IP)
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        List<Rule> matched = new ArrayList<>();
        for (Rule r : RULES) {
            if (r.method().equalsIgnoreCase(request.getMethod()) && MATCHER.match(r.pattern(), request.getRequestURI())) {
                matched.add(r);
            }
        }
        if (matched.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        // Only buffer the body (and read the e-mail) when a per-IP+email rule is in play.
        HttpServletRequest effective = request;
        String email = null;
        if (matched.stream().anyMatch(r -> r.keyBy() == KeyBy.IP_EMAIL)) {
            CachedBodyRequest cached = new CachedBodyRequest(request);
            effective = cached;
            email = extractEmail(cached.getBody());
        }

        String ip = clientIp(request);
        for (Rule r : matched) {
            String key;
            if (r.keyBy() == KeyBy.IP_EMAIL) {
                if (email == null || email.isBlank()) continue;   // no e-mail supplied — skip per-account rule
                key = "rl:" + r.id() + ":" + ip + ":" + email.trim().toLowerCase();
            } else {
                key = "rl:" + r.id() + ":" + ip;
            }
            if (isLimited(r, key, response)) {
                return; // 429 written
            }
        }
        chain.doFilter(effective, response);
    }

    /** Increment the fixed-window counter for a precomputed key; returns true (and writes 429) when exceeded. */
    private boolean isLimited(Rule rule, String key, HttpServletResponse response) throws IOException {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count == null) return false;
            if (count == 1L) {
                redis.expire(key, rule.windowSeconds(), TimeUnit.SECONDS);
            }
            if (count > rule.limit()) {
                Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : rule.windowSeconds();
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(retryAfter));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please retry in "
                                + retryAfter + " seconds.\"}");
                log.warn("Rate limit hit: rule={} key={} count={}/{}", rule.id(), key, count, rule.limit());
                return true;
            }
        } catch (Exception e) {
            log.warn("Rate-limit backend error (allowing request): {}", e.getMessage());
        }
        return false;
    }

    private String extractEmail(byte[] body) {
        if (body == null || body.length == 0) return null;
        try {
            Matcher m = EMAIL_IN_BODY.matcher(new String(body, StandardCharsets.UTF_8));
            return m.find() ? m.group(1) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        String xReal = request.getHeader("X-Real-IP");
        if (xReal != null && !xReal.isBlank()) return xReal.trim();
        return request.getRemoteAddr();
    }

    /** Buffers the request body so the filter can read it (for the e-mail) and the controller can re-read it. */
    private static class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        byte[] getBody() { return body; }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return bais.read(); }
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { /* no-op */ }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), StandardCharsets.UTF_8));
        }
    }
}

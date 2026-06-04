package com.remitz.modules.payin.kuber.service;

import com.remitz.modules.payin.kuber.config.KuberProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Code added by Naresh: Phase 2 — obtains and caches a Kuber JWT.
 *
 * Cache strategy for DEV/POC:
 *  - Process-local AtomicReference (no Redis dependency introduced in this phase).
 *  - Kuber has not published a documented token TTL; we use a conservative 30-minute
 *    soft expiry. Phase 3+ will refresh-on-401 once we know the real failure signature.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KuberTokenService {

    private static final Duration SOFT_TTL = Duration.ofMinutes(30);

    private final KuberProperties properties;
    private final KuberClient client;

    private final AtomicReference<CachedToken> cache = new AtomicReference<>();

    /**
     * Returns a valid JWT, using the cached value when it has not soft-expired.
     * Throws IllegalStateException when kuber.enabled=false.
     */
    public synchronized String getValidToken() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Kuber integration is disabled (kuber.enabled=false)");
        }

        CachedToken current = cache.get();
        if (current != null && current.isValid()) {
            log.debug("Kuber token available from in-memory cache");
            return current.token();
        }

        log.info("Kuber token cache miss — requesting a new token");
        String fresh = client.generateToken();
        cache.set(new CachedToken(fresh, Instant.now().plus(SOFT_TTL)));
        log.info("Kuber token cached (soft-expires in {} min)", SOFT_TTL.toMinutes());
        return fresh;
    }

    /**
     * Drops the cached token. Phase 3+ will invoke this on HTTP 401 from downstream calls.
     */
    public void invalidate() {
        cache.set(null);
        log.info("Kuber token cache invalidated");
    }

    private record CachedToken(String token, Instant expiresAt) {
        boolean isValid() {
            return token != null && !token.isBlank() && Instant.now().isBefore(expiresAt);
        }
    }
}

package com.remitz.security;

import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * SECURITY (SEC-12): centralised object-level authorization used from @PreAuthorize SpEL, e.g.
 *   @PreAuthorize("@ownershipGuard.ownsUuid(#uuid) or hasPermission(null,'user:view')")
 *   @PreAuthorize("@ownershipGuard.ownsUserId(#userId) or hasPermission(null,'user:approve_kyc')")
 *
 * The authenticated principal name is the JWT subject = the caller's user UUID
 * (see JwtAuthenticationFilter). Staff bypass is expressed separately in the SpEL via
 * hasPermission(...)/hasRole(...). Uses only Spring Security core + the existing UserRepository —
 * no new dependency.
 */
@Component("ownershipGuard")
@RequiredArgsConstructor
public class OwnershipGuard {

    private final UserRepository userRepository;

    /** True when the current caller's UUID equals the given resource-owner UUID. */
    public boolean ownsUuid(String ownerUuid) {
        String caller = callerUuid();
        return caller != null && ownerUuid != null && caller.equals(ownerUuid);
    }

    /**
     * True when a path reference that may be EITHER a numeric user id OR a user UUID resolves to the
     * current caller's own account (matches KycController.resolveUserId semantics).
     */
    public boolean ownsUserRef(String userIdOrUuid) {
        if (userIdOrUuid == null) return false;
        try {
            return ownsUserId(Long.parseLong(userIdOrUuid));
        } catch (NumberFormatException notNumeric) {
            return ownsUuid(userIdOrUuid);
        }
    }

    /** True when the given numeric user id resolves to the current caller's own account. */
    public boolean ownsUserId(Long ownerUserId) {
        String caller = callerUuid();
        if (caller == null || ownerUserId == null) return false;
        return userRepository.findByUuid(caller)
                .map(u -> ownerUserId.equals(u.getId()))
                .orElse(false);
    }

    private String callerUuid() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return null;
        String name = auth.getName();
        return (name == null || name.isBlank() || "anonymousUser".equals(name)) ? null : name;
    }
}

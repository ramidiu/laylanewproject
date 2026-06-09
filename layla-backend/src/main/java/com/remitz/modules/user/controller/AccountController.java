package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.modules.user.dto.AccountDeletionRequest;
import com.remitz.modules.user.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Customer account lifecycle endpoints (Google Play Account Deletion policy).
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Account lifecycle (deletion requests)")
public class AccountController {

    private final AccountService accountService;

    @Operation(summary = "Request account deletion",
            description = "Marks the authenticated user's account for deletion, disables access, "
                    + "revokes sessions, audits the event, and emails a confirmation. "
                    + "Records are retained for the legally required AML/KYC/tax period.")
    @PostMapping("/delete-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> requestDeletion(
            @Valid @RequestBody(required = false) AccountDeletionRequest body,
            HttpServletRequest request) {

        String token = extractBearerToken(request);
        String reason = body != null ? body.getReason() : null;

        accountService.requestAccountDeletion(token, reason, clientIp(request),
                request.getHeader("User-Agent"));

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Account deletion request received. Your account has been deactivated and "
                        + "a confirmation email has been sent.")
                .build());
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

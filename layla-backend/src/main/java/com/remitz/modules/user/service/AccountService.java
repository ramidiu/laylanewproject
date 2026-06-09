package com.remitz.modules.user.service;

import com.remitz.common.audit.AuditService;
import com.remitz.common.enums.AccountStatus;
import com.remitz.common.enums.UserStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.auth.service.AuthService;
import com.remitz.modules.notification.service.EmailService;
import com.remitz.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Handles customer-initiated account deletion requests in line with Google
 * Play's Account Deletion policy.
 *
 * <p>Deletion is a <b>soft</b> operation: the account is marked
 * {@link AccountStatus#DELETE_REQUESTED}, access is disabled (login blocked +
 * all sessions revoked), an audit event is recorded, and a confirmation email is
 * sent. Transaction and KYC records are <b>retained</b> for the legally required
 * AML/KYC/tax retention period — they are not hard-deleted here.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthService authService;
    private final EmailService emailService;
    private final AuditService auditService;

    /**
     * Mark the authenticated user's account for deletion.
     *
     * @param accessToken the bearer access token from the request (used to
     *                    resolve the user and to revoke the live session)
     * @param reason      optional, user-supplied reason
     * @param ipAddress   caller IP (audited)
     * @param userAgent   caller user agent (audited)
     */
    @Transactional
    public void requestAccountDeletion(String accessToken, String reason,
                                       String ipAddress, String userAgent) {
        String uuid = accessToken == null ? null : jwtService.getUserUuidFromToken(accessToken);
        if (uuid == null) {
            throw new RemitzException("Unauthenticated", HttpStatus.UNAUTHORIZED);
        }

        UserEntity user = userRepository.findByUuid(uuid)
                .orElseThrow(() -> new RemitzException("Account not found", HttpStatus.NOT_FOUND));

        // Prevent duplicate requests.
        if (user.getAccountStatus() == AccountStatus.DELETE_REQUESTED
                || user.getAccountStatus() == AccountStatus.DELETED) {
            throw new RemitzException("A deletion request is already in progress for this account",
                    HttpStatus.CONFLICT);
        }

        UserStatus previousStatus = user.getStatus();

        // Soft delete: flag the account, disable access, retain records.
        user.setAccountStatus(AccountStatus.DELETE_REQUESTED);
        user.setDeleteRequestedAt(LocalDateTime.now());
        user.setDeleteReason(reason);
        user.setDeletedBy(user.getEmail());            // self-requested
        user.setStatus(UserStatus.CLOSED);             // blocks login + shows in admin archive list
        userRepository.save(user);

        // Revoke the live session and all refresh tokens.
        try {
            authService.revokeAllSessions(user.getId(), accessToken);
        } catch (Exception e) {
            log.warn("Failed to revoke sessions during account deletion for {}: {}",
                    user.getEmail(), e.getMessage());
        }

        // Audit (IP + timestamp captured by AuditService).
        auditService.logAudit(
                user.getId(), user.getEmail(), "CUSTOMER", "AccountService",
                "ACCOUNT_DELETION_REQUESTED", "USER", user.getUuid(),
                "User requested account deletion." + (reason != null && !reason.isBlank()
                        ? " Reason: " + reason : ""),
                previousStatus != null ? previousStatus.name() : null,
                AccountStatus.DELETE_REQUESTED.name(),
                ipAddress, userAgent);

        // Confirmation email (best-effort).
        try {
            emailService.sendEmail(
                    user.getEmail(),
                    "Account Deletion Request Received",
                    buildDeletionEmail(user.getFirstName()),
                    user.getId(), "ACCOUNT_DELETION_REQUEST", null);
        } catch (Exception e) {
            log.warn("Failed to send account deletion email to {}: {}", user.getEmail(), e.getMessage());
        }

        log.info("Account deletion requested for user {} (id={})", user.getEmail(), user.getId());
    }

    private String buildDeletionEmail(String firstName) {
        String greeting = (firstName != null && !firstName.isBlank()) ? "Dear " + firstName + "," : "Hello,";
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#1f2937;line-height:1.6;\">"
                + "<p>" + greeting + "</p>"
                + "<p>We have received your request to delete your Layla Money Transfer account.</p>"
                + "<p>Your account has been deactivated.</p>"
                + "<p>Certain transaction and identity verification records may be retained for the "
                + "legally required period in accordance with Anti-Money Laundering (AML), Know Your "
                + "Customer (KYC), tax, and financial regulations.</p>"
                + "<p>If you did not make this request, please contact support immediately at "
                + "<a href=\"mailto:support@laylamoneytransfer.co.uk\">support@laylamoneytransfer.co.uk</a>.</p>"
                + "<p>Kind regards,<br/>Layla Money Transfer</p>"
                + "</div>";
    }
}

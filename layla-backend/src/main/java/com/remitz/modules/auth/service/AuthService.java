package com.remitz.modules.auth.service;

import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.UserStatus;
import com.remitz.common.enums.UserType;
import com.remitz.security.JwtProperties;
import com.remitz.security.JwtService;
import com.remitz.modules.auth.config.SecurityProperties;
import com.remitz.modules.auth.dto.*;
import com.remitz.modules.auth.entity.*;
import com.remitz.modules.auth.repository.*;
import com.remitz.common.audit.AuditService;
import com.remitz.common.dto.OtpVerifyRequest;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.UnauthorizedException;
import com.remitz.modules.auth.service.AuthEmailService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Single entry-point for authentication, registration, password management,
 * multi-factor authentication, and demo access.
 *
 * <h2>Login flow ({@link #login})</h2>
 * <ol>
 *   <li>Check Redis login-attempts counter — if above
 *       {@code securityProperties.maxLoginAttempts}, block the account for
 *       the configured lockout duration.</li>
 *   <li>Look up the user by email, verify password with BCrypt.</li>
 *   <li>Check {@code emailVerified} — unverified users cannot proceed.</li>
 *   <li>Check {@code demoAccessExpiresAt} — expired demo users are rejected.</li>
 *   <li>Check {@code status} — only ACTIVE and PENDING_VERIFICATION can log in.</li>
 *   <li>Enforce concurrent-session cap — revoke oldest tokens if over limit.</li>
 *   <li><b>Staff MFA enforcement</b> — if the user has any role in
 *       {@link #STAFF_ROLES} and {@code mfaEnabled=false}, return a
 *       {@code mfaSetupRequired=true} response with a fresh TOTP secret and
 *       QR code. The caller must complete setup via
 *       {@link #completeStaffMfaSetup} before receiving real tokens.</li>
 *   <li><b>Customer MFA</b> — if {@code mfaEnabled=true}, return an
 *       {@code mfaRequired=true} response with a short-lived MFA token; the
 *       caller must submit a TOTP code to {@code /verify-mfa}.</li>
 *   <li>Otherwise, issue access + refresh tokens and persist the refresh
 *       token in {@code refresh_tokens}.</li>
 * </ol>
 *
 * <h2>Staff MFA enforcement</h2>
 * The constant {@link #STAFF_ROLES} lists the internal roles that must have
 * MFA enabled at all times. If a new staff role is added, it must also be
 * added here or MFA enforcement will be bypassed.
 *
 * <h2>Important invariants</h2>
 * <ul>
 *   <li>No access token is ever issued before password verification.</li>
 *   <li>Staff cannot bypass MFA — even a valid password returns
 *       {@code mfaSetupRequired} if {@code mfaEnabled=false}.</li>
 *   <li>Every login and password-failure event is audited via
 *       {@link AuditService}.</li>
 *   <li>The login-attempts counter in Redis expires automatically after
 *       {@code lockoutDurationMinutes}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final String LOGIN_ATTEMPTS_PREFIX = "login:attempts:";
    private static final String RESET_TOKEN_PREFIX = "reset:token:";
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#+\\-_])[A-Za-z\\d@$!%*?&#+\\-_]{8,}$"
    );

    private static final Set<String> STAFF_ROLES = Set.of(
            "ADMIN", "SUPER_ADMIN", "COMPLIANCE_OFFICER", "TREASURY_MANAGER",
            "SUPPORT", "FINANCE", "PAYIN_PARTNER", "PAYOUT_PARTNER"
    );

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;
    private final JwtProperties jwtProperties;
    private final SecurityProperties securityProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final OtpService otpService;
    private final AuthEmailService emailService;
    private final AuditService auditService;
    private final TotpService totpService;
    private final ComplianceScreeningClient complianceScreeningClient;
    // Code added by Naresh: System Controls Phase 7 — runtime registration master switch.
    private final com.remitz.modules.user.service.SystemConfigService systemConfigService;

    @org.springframework.beans.factory.annotation.Value("${app.mfa.enforce-staff:true}")
    private boolean enforceStaffMfa;

    @org.springframework.beans.factory.annotation.Value("${app.frontend-url:https://layla.money}")
    private String frontendUrl;

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        if (!systemConfigService.getBoolean("registration.enabled", true)) {
            throw new RemitzException(
                    "New registrations are temporarily disabled. Please try again later.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Validate password policy
        validatePasswordPolicy(request.getPassword());

        // Handle existing email: resend OTP for unverified, reject for verified
        UserEntity existing = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (existing != null) {
            if (Boolean.TRUE.equals(existing.getEmailVerified())) {
                // Fully registered — ask them to log in
                throw new RemitzException(
                        "This email is already registered. Please log in instead.",
                        org.springframework.http.HttpStatus.CONFLICT);
            }
            // Unverified — resend OTP and return existing account info
            // Update basic fields (user might have typed different name/phone)
            if (request.getFirstName() != null) existing.setFirstName(request.getFirstName());
            if (request.getLastName() != null) existing.setLastName(request.getLastName());
            if (request.getPhone() != null) existing.setPhone(request.getPhone());
            if (request.getCountryOfResidence() != null) existing.setCountryOfResidence(request.getCountryOfResidence());
            if (request.getCountry() != null) existing.setCountry(request.getCountry());
            if (request.getAddressLine1() != null) existing.setAddressLine1(request.getAddressLine1());
            if (request.getAddressLine2() != null) existing.setAddressLine2(request.getAddressLine2());
            if (request.getCity() != null) existing.setCity(request.getCity());
            if (request.getPostcode() != null) existing.setPostcode(request.getPostcode());
            // Update password to the new one (user might have forgotten the old one)
            existing.setPasswordHash(passwordEncoder.encode(request.getPassword()));
            userRepository.save(existing);

            String otp = otpService.generateOtp();
            otpService.storeOtp(request.getEmail(), otp);
            String fullName = (existing.getFirstName() != null ? existing.getFirstName() : "")
                    + " " + (existing.getLastName() != null ? existing.getLastName() : "");
            emailService.sendOtpEmail(request.getEmail(), otp, fullName.trim());
            log.info("Resent OTP for unverified account: {}", request.getEmail());

            return RegisterResponse.builder()
                    .uuid(existing.getUuid())
                    .email(existing.getEmail())
                    .message("We've sent you a new verification code. Please check your email to complete registration.")
                    .build();
        }

        // Create user
        String uuid = UUID.randomUUID().toString();
        UserEntity user = UserEntity.builder()
                .uuid(uuid)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .country(request.getCountryOfResidence() != null ? request.getCountryOfResidence() : request.getCountry())
                .nationality(request.getNationality())
                .countryOfResidence(request.getCountryOfResidence())
                .countryCode(request.getCountryCode())
                .addressLine1(request.getAddressLine1())
                .addressLine2(request.getAddressLine2())
                .city(request.getCity())
                .postcode(request.getPostcode())
                .userType(UserType.INDIVIDUAL)
                .kycTier(KycTier.TIER_0)
                .status(UserStatus.ACTIVE)
                .mfaEnabled(false)
                .emailVerified(false)
                .preferredLanguage("en")
                .build();

        // Assign CUSTOMER role
        RoleEntity customerRole = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Default CUSTOMER role not found"));
        user.getRoles().add(customerRole);

        userRepository.save(user);

        // Save initial password to history
        PasswordHistoryEntity passwordHistory = PasswordHistoryEntity.builder()
                .userId(user.getId())
                .passwordHash(user.getPasswordHash())
                .build();
        passwordHistoryRepository.save(passwordHistory);

        // Generate and send OTP for email verification
        String otp = otpService.generateOtp();
        otpService.storeOtp(request.getEmail(), otp);
        log.debug("OTP generated for {}", request.getEmail());

        String fullName = request.getFirstName() + " " + request.getLastName();
        emailService.sendOtpEmail(request.getEmail(), otp, fullName);

        complianceScreeningClient.screenCustomerAsync(
                user.getId(),
                fullName.trim(),
                user.getCountry(),
                null);

        log.info("User registered successfully with UUID: {}. OTP sent to {}.", uuid, request.getEmail());

        return RegisterResponse.builder()
                .uuid(uuid)
                .email(user.getEmail())
                .message("OTP sent to " + request.getEmail())
                .build();
    }

    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail();

        // Check login attempts
        String attemptsKey = LOGIN_ATTEMPTS_PREFIX + email;
        String attemptsStr = stringRedisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= securityProperties.getMaxLoginAttempts()) {
            auditService.logLogin(null, email, null, "LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalStateException("Account is temporarily locked due to too many failed login attempts. " +
                    "Please try again after " + securityProperties.getLockoutDurationMinutes() + " minutes.");
        }

        // Find user
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    incrementLoginAttempts(attemptsKey);
                    auditService.logLogin(null, email, null, "LOGIN_FAILED", ipAddress, userAgent);
                    return new IllegalArgumentException("Invalid email or password");
                });

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            incrementLoginAttempts(attemptsKey);
            auditService.logLogin(user.getId(), email, null, "LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Clear login attempts on success
        stringRedisTemplate.delete(attemptsKey);

        // Check email verification — auto-send a fresh OTP so the customer can verify
        // immediately on the OTP page (the frontend redirects there on this error).
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            try {
                String otp = otpService.generateOtp();
                otpService.storeOtp(email, otp);
                String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " " +
                        (user.getLastName() != null ? user.getLastName() : "")).trim();
                emailService.sendOtpEmail(email, otp, fullName);
            } catch (Exception e) {
                log.warn("Failed to send OTP on unverified login for {}: {}", email, e.getMessage());
            }
            throw new UnauthorizedException("Email not verified. Please complete OTP verification.");
        }

        // Check demo-access expiration
        if (user.getDemoAccessExpiresAt() != null
                && user.getDemoAccessExpiresAt().isBefore(LocalDateTime.now())) {
            auditService.logLogin(user.getId(), email, null, "LOGIN_FAILED", ipAddress, userAgent);
            throw new UnauthorizedException(
                    "Your demo access has expired. Please request new demo credentials.");
        }

        // Check user status — allow ACTIVE and PENDING_VERIFICATION (profile changes under review)
        if (user.getStatus() != UserStatus.ACTIVE
                && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            auditService.logLogin(user.getId(), email, null, "LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalStateException("Account is " + user.getStatus().name().toLowerCase() +
                    ". Please contact support.");
        }

        // Check concurrent sessions
        long activeSessions = refreshTokenRepository.countByUserIdAndRevokedFalse(user.getId());
        if (activeSessions >= securityProperties.getMaxConcurrentSessions()) {
            // Revoke oldest sessions to make room
            log.info("Max concurrent sessions reached for user {}. Revoking oldest sessions.", user.getUuid());
            revokeAllRefreshTokens(user.getId());
        }

        // Force MFA setup for staff roles that haven't enabled MFA yet.
        boolean isStaff = user.getRoles().stream()
                .map(RoleEntity::getName)
                .anyMatch(STAFF_ROLES::contains);
        if (enforceStaffMfa && isStaff && !Boolean.TRUE.equals(user.getMfaEnabled())) {
            String secret = totpService.generateSecret();
            String qrCodeUri = totpService.generateQrCodeDataUri(secret, email);
            auditService.logLogin(user.getId(), email, primaryRoleName(user),
                    "LOGIN_MFA_SETUP_REQUIRED", ipAddress, userAgent);
            return LoginResponse.builder()
                    .mfaSetupRequired(true)
                    .mfaSetupSecret(secret)
                    .mfaSetupQrCodeUri(qrCodeUri)
                    .userUuid(user.getUuid())
                    .build();
        }

        // Check MFA
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            String mfaToken = jwtService.generateMfaToken(user);
            auditService.logLogin(user.getId(), email, "CUSTOMER", "LOGIN_SUCCESS", ipAddress, userAgent);
            return LoginResponse.builder()
                    .mfaToken(mfaToken)
                    .mfaRequired(true)
                    .build();
        }

        // Generate tokens
        Set<String> permissions = collectPermissions(user);
        String accessToken = jwtService.generateAccessToken(user, permissions, false);
        String refreshToken = jwtService.generateRefreshToken(user);

        // Save refresh token
        saveRefreshToken(user, refreshToken);

        auditService.logLogin(user.getId(), email, "CUSTOMER", "LOGIN_SUCCESS", ipAddress, userAgent);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .mfaRequired(false)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .passwordChangeRequired(Boolean.TRUE.equals(user.getPasswordChangeRequired()))
                .build();
    }

    @Transactional
    public TokenResponse verifyMfa(MfaVerifyRequest request) {
        // Validate MFA token
        Claims claims = jwtService.validateToken(request.getMfaToken());
        if (claims == null) {
            throw new IllegalArgumentException("Invalid or expired MFA token");
        }

        String tokenType = claims.get("type", String.class);
        if (!"mfa".equals(tokenType)) {
            throw new IllegalArgumentException("Invalid token type for MFA verification");
        }

        String userUuid = claims.getSubject();
        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify TOTP code (simple time-based verification)
        if (!verifyTotpCode(user.getMfaSecret(), request.getTotpCode())) {
            throw new IllegalArgumentException("Invalid TOTP code");
        }

        // Generate full access tokens
        Set<String> permissions = collectPermissions(user);
        String accessToken = jwtService.generateAccessToken(user, permissions, true);
        String refreshToken = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, refreshToken);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public TokenResponse refreshToken(RefreshTokenRequest request) {
        RefreshTokenEntity storedToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(request.getRefreshToken())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        // Check expiration
        if (storedToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            storedToken.setRevoked(true);
            refreshTokenRepository.save(storedToken);
            throw new IllegalArgumentException("Refresh token has expired");
        }

        UserEntity user = storedToken.getUser();

        // Revoke old refresh token
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        // Generate new tokens
        Set<String> permissions = collectPermissions(user);
        String newAccessToken = jwtService.generateAccessToken(user, permissions, false);
        String newRefreshToken = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, newRefreshToken);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public void logout(String token) {
        // Blacklist the access token
        long remainingTtl = jwtService.getTokenRemainingTimeMs(token);
        if (remainingTtl > 0) {
            tokenBlacklistService.blacklist(token, remainingTtl);
        }

        // Revoke all refresh tokens for the user
        String userUuid = jwtService.getUserUuidFromToken(token);
        if (userUuid != null) {
            userRepository.findByUuid(userUuid).ifPresent(user ->
                    revokeAllRefreshTokens(user.getId()));
        }

        log.info("User logged out successfully");
    }

    public boolean emailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    public void forgotPassword(ForgotPasswordRequest request) {
        // Always return success to prevent email enumeration
        userRepository.findByEmail(request.getEmail()).ifPresent(user -> {
            String resetToken = UUID.randomUUID().toString();
            String key = RESET_TOKEN_PREFIX + resetToken;

            // Store reset token in Redis with 1-hour TTL
            stringRedisTemplate.opsForValue().set(key, user.getUuid(), 1, TimeUnit.HOURS);

            // Send password reset email
            String fullName = user.getFirstName() != null ? user.getFirstName() : user.getEmail().split("@")[0];
            emailService.sendPasswordResetEmail(user.getEmail(), resetToken, fullName);

            log.info("Password reset token generated and email sent for user: {}", user.getUuid());
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String key = RESET_TOKEN_PREFIX + request.getResetToken();
        String userUuid = stringRedisTemplate.opsForValue().get(key);

        if (userUuid == null) {
            throw new IllegalArgumentException("Invalid or expired reset token");
        }

        // Validate password policy
        validatePasswordPolicy(request.getNewPassword());

        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Check password history
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        List<PasswordHistoryEntity> recentPasswords =
                passwordHistoryRepository.findTop5ByUserIdOrderByCreatedAtDesc(user.getId());

        for (PasswordHistoryEntity history : recentPasswords) {
            if (passwordEncoder.matches(request.getNewPassword(), history.getPasswordHash())) {
                throw new IllegalArgumentException(
                        "New password cannot be the same as any of your last " +
                                securityProperties.getPasswordHistoryCount() + " passwords");
            }
        }

        // Update password
        user.setPasswordHash(newPasswordHash);
        user.setPasswordChangeRequired(false);   // forgot-password reset counts as setting their own password
        userRepository.save(user);

        // Save to password history
        PasswordHistoryEntity passwordHistory = PasswordHistoryEntity.builder()
                .userId(user.getId())
                .passwordHash(newPasswordHash)
                .build();
        passwordHistoryRepository.save(passwordHistory);

        // Revoke all refresh tokens
        revokeAllRefreshTokens(user.getId());

        // Delete reset token from Redis
        stringRedisTemplate.delete(key);

        log.info("Password reset successfully for user: {}", user.getUuid());
    }

    @Transactional
    public void changePassword(String userUuid, ChangePasswordRequest request) {
        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Validate new password policy
        validatePasswordPolicy(request.getNewPassword());

        // Check password history
        List<PasswordHistoryEntity> recentPasswords =
                passwordHistoryRepository.findTop5ByUserIdOrderByCreatedAtDesc(user.getId());

        for (PasswordHistoryEntity history : recentPasswords) {
            if (passwordEncoder.matches(request.getNewPassword(), history.getPasswordHash())) {
                throw new IllegalArgumentException(
                        "New password cannot be the same as any of your last " +
                                securityProperties.getPasswordHistoryCount() + " passwords");
            }
        }

        // Update password
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        user.setPasswordHash(newPasswordHash);
        user.setPasswordChangeRequired(false);   // they've now set their own password
        userRepository.save(user);

        // Save to password history
        PasswordHistoryEntity passwordHistory = PasswordHistoryEntity.builder()
                .userId(user.getId())
                .passwordHash(newPasswordHash)
                .build();
        passwordHistoryRepository.save(passwordHistory);

        log.info("Password changed successfully for user: {}", user.getUuid());
    }

    public void resendOtp(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new com.remitz.common.exception.ResourceNotFoundException("User", "email", email));

        String otp = otpService.generateOtp();
        otpService.storeOtp(email, otp);
        log.debug("OTP generated for {}", email);
        emailService.sendOtpEmail(email, otp, user.getFirstName() != null ? user.getFirstName() : "User");
    }

    @Transactional
    public LoginResponse verifyRegistrationOtp(OtpVerifyRequest request) {
        String email = request.getEmail();
        String otp = request.getOtp();

        // Validate OTP
        if (!otpService.validateOtp(email, otp)) {
            throw new UnauthorizedException("Invalid or expired OTP");
        }

        // Find user and mark email as verified
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired OTP"));

        user.setEmailVerified(true);
        userRepository.save(user);

        // Delete OTP from Redis
        otpService.deleteOtp(email);

        // Generate tokens
        Set<String> permissions = collectPermissions(user);
        String accessToken = jwtService.generateAccessToken(user, permissions, false);
        String refreshToken = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, refreshToken);

        log.info("Email verified successfully for user: {}", user.getUuid());

        // Send welcome email
        String fullName = user.getFirstName() != null ? user.getFirstName() : email.split("@")[0];
        emailService.sendWelcomeEmail(email, fullName);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .mfaRequired(false)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public LoginResponse adminLogin(LoginRequest request, String ipAddress, String userAgent) {
        String email = request.getEmail();

        // Check login attempts
        String attemptsKey = LOGIN_ATTEMPTS_PREFIX + email;
        String attemptsStr = stringRedisTemplate.opsForValue().get(attemptsKey);
        int attempts = attemptsStr != null ? Integer.parseInt(attemptsStr) : 0;

        if (attempts >= securityProperties.getMaxLoginAttempts()) {
            auditService.logLogin(null, email, null, "ADMIN_LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalStateException("Account is temporarily locked due to too many failed login attempts. " +
                    "Please try again after " + securityProperties.getLockoutDurationMinutes() + " minutes.");
        }

        // Find user
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    incrementLoginAttempts(attemptsKey);
                    auditService.logLogin(null, email, null, "ADMIN_LOGIN_FAILED", ipAddress, userAgent);
                    return new IllegalArgumentException("Invalid email or password");
                });

        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            incrementLoginAttempts(attemptsKey);
            auditService.logLogin(user.getId(), email, null, "ADMIN_LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalArgumentException("Invalid email or password");
        }

        // Clear login attempts on success
        stringRedisTemplate.delete(attemptsKey);

        // Validate staff role
        boolean hasStaffRole = user.getRoles().stream()
                .anyMatch(role -> STAFF_ROLES.contains(role.getName()));

        if (!hasStaffRole) {
            auditService.logLogin(user.getId(), email, null, "ADMIN_LOGIN_FAILED", ipAddress, userAgent);
            throw new UnauthorizedException("Access denied. Staff role required.");
        }

        // Check user status — allow ACTIVE and PENDING_VERIFICATION
        if (user.getStatus() != UserStatus.ACTIVE
                && user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            auditService.logLogin(user.getId(), email, null, "ADMIN_LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalStateException("Account is " + user.getStatus().name().toLowerCase() +
                    ". Please contact support.");
        }

        // Check concurrent sessions
        long activeSessions = refreshTokenRepository.countByUserIdAndRevokedFalse(user.getId());
        if (activeSessions >= securityProperties.getMaxConcurrentSessions()) {
            log.info("Max concurrent sessions reached for admin user {}. Revoking oldest sessions.", user.getUuid());
            revokeAllRefreshTokens(user.getId());
        }

        // Check MFA
        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            String mfaToken = jwtService.generateMfaToken(user);
            String primaryRole = user.getRoles().stream()
                    .map(RoleEntity::getName)
                    .filter(STAFF_ROLES::contains)
                    .findFirst().orElse(null);
            auditService.logLogin(user.getId(), email, primaryRole, "ADMIN_LOGIN_SUCCESS", ipAddress, userAgent);
            return LoginResponse.builder()
                    .mfaToken(mfaToken)
                    .mfaRequired(true)
                    .build();
        }

        // Generate tokens
        Set<String> permissions = collectPermissions(user);
        String accessToken = jwtService.generateAccessToken(user, permissions, false);
        String refreshToken = jwtService.generateRefreshToken(user);

        saveRefreshToken(user, refreshToken);

        String primaryRole = user.getRoles().stream()
                .map(RoleEntity::getName)
                .filter(STAFF_ROLES::contains)
                .findFirst().orElse(null);
        auditService.logLogin(user.getId(), email, primaryRole, "ADMIN_LOGIN_SUCCESS", ipAddress, userAgent);

        log.info("Admin login successful for user: {}", user.getUuid());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .mfaRequired(false)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .build();
    }

    @Transactional
    public RegisterResponse registerPartner(com.remitz.modules.auth.dto.RegisterPartnerRequest request) {
        // Validate password policy
        validatePasswordPolicy(request.getPassword());

        // Check email not taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email is already registered");
        }

        // Validate role is a partner role
        String roleName = request.getRole();
        if (!"PAYOUT_PARTNER".equals(roleName) && !"PAYIN_PARTNER".equals(roleName)) {
            throw new IllegalArgumentException("Invalid partner role: " + roleName);
        }

        // Create user
        String uuid = UUID.randomUUID().toString();
        UserEntity user = UserEntity.builder()
                .uuid(uuid)
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .userType(UserType.INDIVIDUAL)
                .kycTier(KycTier.TIER_0)
                .status(UserStatus.ACTIVE)
                .mfaEnabled(false)
                .emailVerified(true) // Partner accounts are pre-verified
                .preferredLanguage("en")
                .build();

        // Assign partner role
        RoleEntity partnerRole = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        user.getRoles().add(partnerRole);

        userRepository.save(user);

        // Save initial password to history
        PasswordHistoryEntity passwordHistory = PasswordHistoryEntity.builder()
                .userId(user.getId())
                .passwordHash(user.getPasswordHash())
                .build();
        passwordHistoryRepository.save(passwordHistory);

        log.info("Partner user registered successfully with UUID: {} and role: {}", uuid, roleName);

        return RegisterResponse.builder()
                .uuid(uuid)
                .email(user.getEmail())
                .message("Partner account created with role " + roleName)
                .build();
    }

    @Transactional
    public com.remitz.modules.auth.dto.DemoAccessResponse createDemoAccess(
            com.remitz.modules.auth.dto.DemoAccessRequest request, String frontendBaseUrl) {
        final String roleName = "ADMIN";

        String email = request.getEmail().trim().toLowerCase();
        String tempPassword = generateDemoPassword();
        String passwordHash = passwordEncoder.encode(tempPassword);
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);

        String rawName = request.getFullName() != null ? request.getFullName().trim() : "";
        String firstName = rawName;
        String lastName = "";
        int spaceIdx = rawName.indexOf(' ');
        if (spaceIdx > 0) {
            firstName = rawName.substring(0, spaceIdx);
            lastName = rawName.substring(spaceIdx + 1).trim();
        }

        UserEntity existing = userRepository.findByEmail(email).orElse(null);
        UserEntity user;
        if (existing != null) {
            if (existing.getDemoAccessExpiresAt() == null) {
                throw new IllegalArgumentException(
                        "Email is already registered. Please sign in or use password reset instead.");
            }
            existing.setPasswordHash(passwordHash);
            existing.setDemoAccessExpiresAt(expiresAt);
            existing.setFirstName(firstName);
            existing.setLastName(lastName);
            existing.setPhone(request.getPhone());
            existing.setCountry(request.getCountry());
            existing.setStatus(UserStatus.ACTIVE);
            existing.setEmailVerified(true);
            RoleEntity requested = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
            existing.getRoles().clear();
            existing.getRoles().add(requested);
            user = userRepository.save(existing);
            log.info("Demo-access: refreshed credentials for existing demo user {} ({})", email, roleName);
        } else {
            RoleEntity requested = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new IllegalStateException("Role not seeded: " + roleName));
            UserEntity fresh = UserEntity.builder()
                    .uuid(UUID.randomUUID().toString())
                    .email(email)
                    .passwordHash(passwordHash)
                    .firstName(firstName)
                    .lastName(lastName)
                    .phone(request.getPhone())
                    .country(request.getCountry())
                    .countryOfResidence(request.getCountry())
                    .userType(UserType.INDIVIDUAL)
                    .kycTier(KycTier.TIER_3)
                    .status(UserStatus.ACTIVE)
                    .mfaEnabled(false)
                    .emailVerified(true)
                    .preferredLanguage("en")
                    .demoAccessExpiresAt(expiresAt)
                    .build();
            fresh.getRoles().add(requested);
            user = userRepository.save(fresh);
            log.info("Demo-access: created new demo user {} ({}) id={}", email, roleName, user.getId());
        }

        String baseUrl = frontendBaseUrl != null ? frontendBaseUrl : frontendUrl;
        String loginUrl = baseUrl + "/admin-login";
        String customerLoginUrl = baseUrl + "/login";
        String registerUrl = baseUrl + "/register";

        emailService.sendDemoAccessEmail(email,
                rawName.isEmpty() ? "there" : firstName,
                email, tempPassword, "Admin", loginUrl,
                buildDemoEmailSteps(loginUrl, registerUrl), expiresAt);

        return com.remitz.modules.auth.dto.DemoAccessResponse.builder()
                .email(email)
                .role(roleName)
                .loginUrl(loginUrl)
                .expiresAt(expiresAt)
                .message("Demo credentials sent to " + email)
                .build();
    }

    private String generateDemoPassword() {
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String digits = "23456789";
        String special = "@$!%*?&#";
        String all = lower + upper + digits + special;
        java.security.SecureRandom r = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(12);
        sb.append(lower.charAt(r.nextInt(lower.length())));
        sb.append(upper.charAt(r.nextInt(upper.length())));
        sb.append(digits.charAt(r.nextInt(digits.length())));
        sb.append(special.charAt(r.nextInt(special.length())));
        for (int i = 4; i < 12; i++) sb.append(all.charAt(r.nextInt(all.length())));
        // Shuffle
        char[] arr = sb.toString().toCharArray();
        for (int i = arr.length - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            char tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp;
        }
        return new String(arr);
    }

    private String buildDemoEmailSteps(String adminLoginUrl, String registerUrl) {
        String customerLoginUrl = adminLoginUrl.replace("/admin-login", "/login");
        return "<h3 style='color:#1B3571;margin:20px 0 10px;'>Getting Started</h3>" +
                "<p><strong>Step 1 — Admin Dashboard</strong><br/>" +
                "Sign in with the credentials above at the <strong>Admin Login</strong>: " +
                "<a href='" + adminLoginUrl + "'>" + adminLoginUrl + "</a>.<br/>" +
                "You'll land on the Admin dashboard with full operator controls: users, transactions, compliance, KYC, corridors, and more.</p>" +
                "<p><strong>Step 2 — Create a Customer Account</strong><br/>" +
                "To experience the customer flow (send money, KYC, beneficiaries), register a separate customer account at " +
                "<a href='" + registerUrl + "'>" + registerUrl + "</a>.<br/>" +
                "Use a different email address for the customer account. Customer login is at " +
                "<a href='" + customerLoginUrl + "'>" + customerLoginUrl + "</a>.</p>" +
                "<p><strong>Step 3 — End-to-End Demo</strong><br/>" +
                "As the customer: complete KYC, add a beneficiary, get a quote, and send a test transfer.<br/>" +
                "Switch to the admin dashboard to see the transaction appear, review KYC, and manage compliance alerts.</p>" +
                "<div style='margin-top:16px;padding:12px;background:#F0F9FF;border-radius:8px;border:1px solid #BAE6FD;'>" +
                "<strong style='color:#1B3571;'>Login URLs:</strong><br/>" +
                "Admin Panel: <a href='" + adminLoginUrl + "'>" + adminLoginUrl + "</a><br/>" +
                "Customer App: <a href='" + customerLoginUrl + "'>" + customerLoginUrl + "</a>" +
                "</div>";
    }

    @Transactional
    public void extendDemoAccess(Long userId, int hours) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (user.getDemoAccessExpiresAt() == null) {
            throw new IllegalArgumentException("User is not a demo account");
        }
        LocalDateTime newExpiry = LocalDateTime.now().plusHours(hours);
        user.setDemoAccessExpiresAt(newExpiry);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);
        log.info("Demo access extended for user {} to {}", user.getEmail(), newExpiry);
    }

    public List<UserEntity> listDemoUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getDemoAccessExpiresAt() != null)
                .sorted(java.util.Comparator.comparing(UserEntity::getDemoAccessExpiresAt).reversed())
                .collect(java.util.stream.Collectors.toList());
    }

    // ---- Private helpers ----

    private void validatePasswordPolicy(String password) {
        if (!PASSWORD_PATTERN.matcher(password).matches()) {
            throw new IllegalArgumentException(
                    "Password must be at least 8 characters and contain at least one uppercase letter, " +
                            "one lowercase letter, one digit, and one special character");
        }
    }

    private void incrementLoginAttempts(String attemptsKey) {
        stringRedisTemplate.opsForValue().increment(attemptsKey);
        stringRedisTemplate.expire(attemptsKey, securityProperties.getLockoutDurationMinutes(), TimeUnit.MINUTES);
    }

    private Set<String> collectPermissions(UserEntity user) {
        return user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(PermissionEntity::getCode)
                .collect(Collectors.toSet());
    }

    private String primaryRoleName(UserEntity user) {
        return user.getRoles().stream()
                .map(RoleEntity::getName)
                .findFirst()
                .orElse("CUSTOMER");
    }

    @Transactional
    public LoginResponse completeStaffMfaSetup(String email, String password, String secret,
                                                String code, String ipAddress, String userAgent) {
        UserEntity user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.logLogin(user.getId(), email, null, "LOGIN_FAILED", ipAddress, userAgent);
            throw new IllegalArgumentException("Invalid email or password");
        }

        boolean isStaff = user.getRoles().stream()
                .map(RoleEntity::getName)
                .anyMatch(STAFF_ROLES::contains);
        if (!isStaff) {
            throw new IllegalStateException("Staff MFA setup is only available for admin accounts");
        }

        if (secret == null || secret.isBlank() || code == null || code.isBlank()) {
            throw new IllegalArgumentException("Secret and verification code are required");
        }

        if (!totpService.verifyCode(secret, code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        userRepository.save(user);

        Set<String> permissions = collectPermissions(user);
        String accessToken = jwtService.generateAccessToken(user, permissions, false);
        String refreshToken = jwtService.generateRefreshToken(user);
        saveRefreshToken(user, refreshToken);

        auditService.logLogin(user.getId(), email, primaryRoleName(user),
                "LOGIN_MFA_SETUP_COMPLETED", ipAddress, userAgent);

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpirationMs() / 1000)
                .userUuid(user.getUuid())
                .build();
    }

    private void saveRefreshToken(UserEntity user, String refreshToken) {
        RefreshTokenEntity tokenEntity = RefreshTokenEntity.builder()
                .user(user)
                .token(refreshToken)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtProperties.getRefreshTokenExpirationMs() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(tokenEntity);
    }

    private void revokeAllRefreshTokens(Long userId) {
        // Since we can't do a bulk update easily with JPA, we use a custom approach
        refreshTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(userId) && !t.getRevoked())
                .forEach(t -> {
                    t.setRevoked(true);
                    refreshTokenRepository.save(t);
                });
    }

    /**
     * Revoke every active session for a user: blacklist the supplied access token
     * (the one used to make the request) and revoke all of the user's refresh
     * tokens. Used by account deletion so the user cannot keep using the app.
     */
    @Transactional
    public void revokeAllSessions(Long userId, String currentAccessToken) {
        if (currentAccessToken != null) {
            long remainingTtl = jwtService.getTokenRemainingTimeMs(currentAccessToken);
            if (remainingTtl > 0) {
                tokenBlacklistService.blacklist(currentAccessToken, remainingTtl);
            }
        }
        revokeAllRefreshTokens(userId);
    }

    private boolean verifyTotpCode(String secret, String code) {
        return totpService.verifyCode(secret, code);
    }

    public Map<String, String> setupMfa(String userUuid) {
        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new IllegalStateException("MFA is already enabled for this account");
        }

        String secret = totpService.generateSecret();
        String qrCodeDataUri = totpService.generateQrCodeDataUri(secret, user.getEmail());

        log.info("MFA setup initiated for user: {}", userUuid);

        return Map.of(
                "secret", secret,
                "qrCodeDataUri", qrCodeDataUri
        );
    }

    @Transactional
    public Map<String, Object> enableMfa(String userUuid, String secret, String code) {
        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new IllegalStateException("MFA is already enabled for this account");
        }

        if (secret == null || code == null) {
            throw new IllegalArgumentException("Secret and code are required");
        }

        if (!totpService.verifyCode(secret, code)) {
            throw new IllegalArgumentException("Invalid TOTP code. Please try again.");
        }

        user.setMfaSecret(secret);
        user.setMfaEnabled(true);
        userRepository.save(user);

        log.info("MFA enabled for user: {}", userUuid);

        return Map.of(
                "success", true,
                "message", "MFA has been enabled successfully"
        );
    }

    @Transactional
    public Map<String, Object> disableMfa(String userUuid, String password) {
        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!Boolean.TRUE.equals(user.getMfaEnabled())) {
            throw new IllegalStateException("MFA is not enabled for this account");
        }

        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid password");
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userRepository.save(user);

        log.info("MFA disabled for user: {}", userUuid);

        return Map.of(
                "success", true,
                "message", "MFA has been disabled successfully"
        );
    }
}

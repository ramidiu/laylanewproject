package com.remitz.modules.auth.controller;

import com.remitz.modules.auth.dto.*;
import com.remitz.modules.auth.dto.RegisterPartnerRequest;
import com.remitz.modules.auth.service.AuthService;
import com.remitz.common.dto.OtpVerifyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import static com.remitz.common.util.PiiMasker.maskEmail;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "Authentication and authorization endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register a new user", description = "Creates a new user account with CUSTOMER role and sends OTP for email verification")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User created — OTP sent to email"),
            @ApiResponse(responseCode = "400", description = "Email already registered or validation error"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info("Registration request for email: {}", maskEmail(request.getEmail()));
        RegisterResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/register-partner")
    @Operation(summary = "Register a partner user", description = "Creates a partner user account with specified role (PAYOUT_PARTNER or PAYIN_PARTNER) and pre-verified email")
    public ResponseEntity<RegisterResponse> registerPartner(@Valid @RequestBody RegisterPartnerRequest request) {
        log.info("Partner registration request for email: {} with role: {}", maskEmail(request.getEmail()), request.getRole());
        RegisterResponse response = authService.registerPartner(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/demo-access")
    @Operation(summary = "Request demo access credentials",
            description = "Creates (or refreshes) a demo user account with a temporary password valid 24h and emails the credentials")
    public ResponseEntity<DemoAccessResponse> demoAccess(@Valid @RequestBody DemoAccessRequest request) {
        log.info("Demo-access request from {}", maskEmail(request.getEmail()));
        DemoAccessResponse response = authService.createDemoAccess(request, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/admin/demo-users")
    @PreAuthorize("hasAuthority('config:manage_system')")
    @Operation(summary = "List all demo access users", description = "Returns all users created via demo-access with their expiry status")
    public ResponseEntity<List<Map<String, Object>>> listDemoUsers() {
        List<com.remitz.modules.auth.entity.UserEntity> demos = authService.listDemoUsers();
        List<Map<String, Object>> result = demos.stream().map(u -> {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("email", u.getEmail());
            m.put("firstName", u.getFirstName());
            m.put("lastName", u.getLastName());
            m.put("phone", u.getPhone());
            m.put("country", u.getCountry());
            m.put("status", u.getStatus());
            m.put("demoAccessExpiresAt", u.getDemoAccessExpiresAt());
            m.put("expired", u.getDemoAccessExpiresAt() != null && u.getDemoAccessExpiresAt().isBefore(java.time.LocalDateTime.now()));
            m.put("createdAt", u.getCreatedAt());
            String roles = u.getRoles().stream().map(r -> r.getName()).collect(java.util.stream.Collectors.joining(", "));
            m.put("roles", roles);
            return m;
        }).collect(java.util.stream.Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/admin/demo-users/{id}/extend")
    @PreAuthorize("hasAuthority('config:manage_system')")
    @Operation(summary = "Extend demo access by 24 hours")
    public ResponseEntity<Map<String, Object>> extendDemoAccess(
            @PathVariable Long id,
            @RequestParam(defaultValue = "24") int hours) {
        authService.extendDemoAccess(id, hours);
        return ResponseEntity.ok(Map.of("success", true, "message", "Extended by " + hours + " hours"));
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticates user credentials and returns JWT tokens. If MFA is enabled, returns an MFA token instead.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Login successful — tokens returned"),
            @ApiResponse(responseCode = "400", description = "Invalid email or password"),
            @ApiResponse(responseCode = "409", description = "Account locked due to too many failed attempts"),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded")
    })
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                                HttpServletRequest httpRequest) {
        log.info("Login request for email: {}", maskEmail(request.getEmail()));
        try {
            String ipAddress = extractIpAddress(httpRequest);
            String userAgent = httpRequest.getHeader("User-Agent");
            LoginResponse response = authService.login(request, ipAddress, userAgent);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Login failed for {}: {} - {}", maskEmail(request.getEmail()), e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify registration OTP", description = "Verifies the OTP sent during registration and returns JWT tokens upon success")
    public ResponseEntity<LoginResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        log.info("OTP verification request for email: {}", maskEmail(request.getEmail()));
        LoginResponse response = authService.verifyRegistrationOtp(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend OTP", description = "Generates a new OTP and sends it to the user's email")
    public ResponseEntity<java.util.Map<String, Object>> resendOtp(@RequestBody java.util.Map<String, String> body) {
        String email = body.get("email");
        log.info("Resend OTP request for email: {}", maskEmail(email));
        authService.resendOtp(email);
        return ResponseEntity.ok(java.util.Map.of("success", true, "message", "OTP resent to " + email));
    }

    @PostMapping("/verify-mfa")
    @Operation(summary = "Verify MFA code", description = "Verifies the TOTP code during MFA-enabled login and returns full JWT tokens")
    public ResponseEntity<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        log.info("MFA verification request");
        TokenResponse response = authService.verifyMfa(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token", description = "Uses a valid refresh token to generate new access and refresh tokens")
    public ResponseEntity<TokenResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        log.info("Token refresh request");
        TokenResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Blacklists the current access token and revokes all refresh tokens for the user")
    public ResponseEntity<MessageResponse> logout(@RequestHeader("Authorization") String authHeader) {
        log.info("Logout request");
        String token = extractToken(authHeader);
        if (token == null) {
            return ResponseEntity.badRequest()
                    .body(MessageResponse.builder().message("Invalid authorization header").build());
        }
        authService.logout(token);
        return ResponseEntity.ok(MessageResponse.builder().message("Logged out successfully").build());
    }

    @GetMapping("/check-email")
    @Operation(summary = "Check if email exists", description = "Returns whether an email is already registered")
    public ResponseEntity<Map<String, Boolean>> checkEmail(@RequestParam String email) {
        boolean exists = authService.emailExists(email);
        return ResponseEntity.ok(Map.of("exists", exists));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset", description = "Generates a password reset token and sends it to the user's email (via notification service)")
    public ResponseEntity<MessageResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        log.info("Forgot password request for email: {}", maskEmail(request.getEmail()));
        authService.forgotPassword(request);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("If the email is registered, a password reset link will be sent")
                .build());
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password", description = "Resets the user's password using the reset token. Validates password policy and history.")
    public ResponseEntity<MessageResponse> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        log.info("Password reset request");
        authService.resetPassword(request);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Password has been reset successfully")
                .build());
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change password", description = "Allows authenticated users to change their password by verifying current password")
    public ResponseEntity<MessageResponse> changePassword(
            @RequestHeader("X-User-UUID") String userUuid,
            @Valid @RequestBody ChangePasswordRequest request) {
        log.info("Change password request for user: {}", userUuid);
        authService.changePassword(userUuid, request);
        return ResponseEntity.ok(MessageResponse.builder()
                .message("Password changed successfully")
                .build());
    }

    @PostMapping("/mfa/setup")
    @Operation(summary = "Setup MFA", description = "Generates a TOTP secret and QR code for Google Authenticator setup")
    public ResponseEntity<?> setupMfa(@RequestHeader("X-User-UUID") String userUuid) {
        log.info("MFA setup request for user: {}", userUuid);
        return ResponseEntity.ok(authService.setupMfa(userUuid));
    }

    @PostMapping("/admin/mfa-setup-complete")
    @Operation(summary = "Complete forced admin MFA setup",
            description = "Finalises TOTP enrolment for a staff account during their first enforced-MFA login and returns full tokens")
    public ResponseEntity<LoginResponse> completeAdminMfaSetup(
            @Valid @RequestBody com.remitz.modules.auth.dto.StaffMfaSetupRequest request,
            HttpServletRequest httpRequest) {
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.completeStaffMfaSetup(
                request.getEmail(), request.getPassword(),
                request.getSecret(), request.getCode(),
                ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/enable")
    @Operation(summary = "Enable MFA", description = "Verifies the TOTP code and activates MFA for the user")
    public ResponseEntity<?> enableMfa(@RequestHeader("X-User-UUID") String userUuid,
                                        @RequestBody Map<String, String> body) {
        String code = body.get("code");
        String secret = body.get("secret");
        log.info("MFA enable request for user: {}", userUuid);
        return ResponseEntity.ok(authService.enableMfa(userUuid, secret, code));
    }

    @PostMapping("/mfa/disable")
    @Operation(summary = "Disable MFA", description = "Disables MFA for the user after verifying their password")
    public ResponseEntity<?> disableMfa(@RequestHeader("X-User-UUID") String userUuid,
                                         @RequestBody Map<String, String> body) {
        String password = body.get("password");
        log.info("MFA disable request for user: {}", userUuid);
        return ResponseEntity.ok(authService.disableMfa(userUuid, password));
    }

    private String extractToken(String authHeader) {
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

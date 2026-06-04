package com.remitz.modules.auth.controller;

import com.remitz.modules.auth.dto.LoginRequest;
import com.remitz.modules.auth.dto.LoginResponse;
import com.remitz.modules.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import static com.remitz.common.util.PiiMasker.maskEmail;

@RestController
@RequestMapping("/api/auth/admin")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Authentication", description = "Admin and staff authentication endpoints")
public class AdminAuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Admin/Staff login", description = "Authenticates staff credentials and validates staff role before issuing JWT tokens")
    public ResponseEntity<LoginResponse> adminLogin(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest) {
        log.info("Admin login request for email: {}", maskEmail(request.getEmail()));
        String ipAddress = extractIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.adminLogin(request, ipAddress, userAgent);
        return ResponseEntity.ok(response);
    }

    private String extractIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}

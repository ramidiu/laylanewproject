package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.UserStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.dto.SystemConfigAuditResponse;
import com.remitz.modules.user.dto.SystemConfigUpdateRequest;
import com.remitz.modules.user.entity.SystemConfig;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.user.service.RiskScoringService;
import com.remitz.modules.user.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Admin Management", description = "APIs for admin operations - stats, risk scoring, system config")
public class AdminController {

    private final UserRepository userRepository;
    private final RiskScoringService riskScoringService;
    // Code added by Naresh: System Controls Phase 2 — repository usage moved into
    // SystemConfigService so typed validation, cache and versioning live in one place.
    private final SystemConfigService systemConfigService;

    // ==================== User Statistics ====================

    @Operation(summary = "Get user statistics", description = "Get aggregate user statistics (admin only)")
    @GetMapping("/admin/stats")
    @PreAuthorize("hasPermission(null, 'user:view')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUserStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus(UserStatus.ACTIVE);
        long kycPending = userRepository.countByKycTier(KycTier.TIER_0);
        long kycVerified = userRepository.countByKycTierIn(
                List.of(KycTier.TIER_1, KycTier.TIER_2, KycTier.TIER_3));
        long basicTier = userRepository.countByKycTier(KycTier.TIER_0);
        long verifiedTier = userRepository.countByKycTier(KycTier.TIER_1);
        long premiumTier = userRepository.countByKycTierIn(
                List.of(KycTier.TIER_2, KycTier.TIER_3));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", totalUsers);
        stats.put("activeUsers", activeUsers);
        stats.put("kycPending", kycPending);
        stats.put("kycVerified", kycVerified);
        stats.put("basicTier", basicTier);
        stats.put("verifiedTier", verifiedTier);
        stats.put("premiumTier", premiumTier);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(stats)
                .message("User statistics retrieved successfully")
                .build());
    }

    // ==================== Risk Score ====================

    @Operation(summary = "Calculate risk score", description = "Calculate and return risk score for a user (admin only)")
    @GetMapping("/{userId}/risk-score")
    @PreAuthorize("hasPermission(null, 'user:view')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRiskScore(
            @PathVariable Long userId) {
        Map<String, Object> riskScore = riskScoringService.calculateRiskScore(userId);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(riskScore)
                .message("Risk score calculated successfully")
                .build());
    }

    @Operation(summary = "Override risk score", description = "Override risk score for a user (super admin only)")
    @PutMapping("/{userId}/risk-override")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overrideRiskScore(
            @PathVariable Long userId,
            @RequestBody Map<String, String> body) {
        String riskLevel = body.get("riskLevel");
        if (riskLevel == null || riskLevel.isBlank()) {
            throw new RemitzException("riskLevel is required", HttpStatus.BAD_REQUEST);
        }

        if (!riskLevel.equals("LOW") && !riskLevel.equals("MEDIUM") && !riskLevel.equals("HIGH")) {
            throw new RemitzException("riskLevel must be LOW, MEDIUM, or HIGH", HttpStatus.BAD_REQUEST);
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        user.setRiskOverride(riskLevel);
        user.setRiskScore(riskLevel);
        userRepository.save(user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("riskScore", riskLevel);
        result.put("overridden", true);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .message("Risk score overridden successfully")
                .build());
    }

    // ==================== System Config ====================

    @Operation(summary = "List system configs", description = "List all system configurations (super admin only)")
    @GetMapping("/admin/system-config")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    public ResponseEntity<ApiResponse<List<SystemConfig>>> getSystemConfigs() {
        List<SystemConfig> configs = systemConfigService.findAll();
        return ResponseEntity.ok(ApiResponse.<List<SystemConfig>>builder()
                .success(true)
                .data(configs)
                .message("System configurations retrieved successfully")
                .build());
    }

    @Operation(summary = "Update system config", description = "Update a system configuration value (super admin only)")
    @PutMapping("/admin/system-config/{key}")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    public ResponseEntity<ApiResponse<SystemConfig>> updateSystemConfig(
            @PathVariable String key,
            @RequestBody SystemConfigUpdateRequest body,
            Authentication authentication) {
        if (body == null || body.value() == null) {
            throw new RemitzException("value is required", HttpStatus.BAD_REQUEST);
        }

        String actor = authentication != null ? authentication.getName() : "system";
        SystemConfig saved = systemConfigService.updateValue(key, body.value(), body.version(), actor, body.reason());

        return ResponseEntity.ok(ApiResponse.<SystemConfig>builder()
                .success(true)
                .data(saved)
                .message("System configuration updated successfully")
                .build());
    }

    // Code added by Naresh: System Controls Phase 3 — audit history per config key.
    @Operation(summary = "Get system config change history",
            description = "Return the audit trail for a single config key, newest first (super admin only)")
    @GetMapping("/admin/system-config/{key}/history")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    public ResponseEntity<ApiResponse<List<SystemConfigAuditResponse>>> getSystemConfigHistory(
            @PathVariable String key) {
        List<SystemConfigAuditResponse> history = systemConfigService.findHistory(key).stream()
                .map(SystemConfigAuditResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.<List<SystemConfigAuditResponse>>builder()
                .success(true)
                .data(history)
                .message("System configuration history retrieved successfully")
                .build());
    }
}

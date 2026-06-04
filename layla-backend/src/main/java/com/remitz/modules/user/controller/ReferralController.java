package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.ReferralCodeEntity;
import com.remitz.modules.user.entity.ReferralConfigEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.user.service.ReferralService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referralService;
    private final UserRepository userRepository;

    // ── Customer Endpoints ──────────────────────────────────────────────

    @GetMapping("/my-code")
    public ResponseEntity<ApiResponse<ReferralCodeEntity>> getMyCode(
            @RequestHeader("X-User-UUID") String uuid) {
        Long userId = resolveUserId(uuid);
        ReferralCodeEntity code = referralService.getOrCreateCode(userId);
        return ResponseEntity.ok(ApiResponse.<ReferralCodeEntity>builder()
                .success(true)
                .data(code)
                .message("Referral code retrieved successfully")
                .build());
    }

    @GetMapping("/validate/{code}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateCode(
            @PathVariable String code,
            @RequestParam(required = false) Long corridorId) {
        Map<String, Object> result = referralService.validateCode(code, corridorId);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .message("Referral code validation completed")
                .build());
    }

    // ── Service-to-Service Endpoints ────────────────────────────────────

    @PostMapping("/on-completed")
    public ResponseEntity<ApiResponse<Void>> onTransactionCompleted(
            @RequestParam String referralCode,
            @RequestParam(required = false) Long corridorId,
            @RequestParam(required = false) String transactionRef) {

        referralService.processReferralReward(referralCode, corridorId, transactionRef);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Referral transaction completed successfully")
                .build());
    }

    // ── Admin Endpoints ─────────────────────────────────────────────────

    @GetMapping("/admin/configs")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<List<ReferralConfigEntity>>> getAdminConfigs() {
        List<ReferralConfigEntity> configs = referralService.getAdminConfigs();
        return ResponseEntity.ok(ApiResponse.<List<ReferralConfigEntity>>builder()
                .success(true)
                .data(configs)
                .message("Referral configs retrieved successfully")
                .build());
    }

    @PostMapping("/admin/configs")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<ReferralConfigEntity>> saveAdminConfig(@RequestBody Map<String, Object> body) {
        Long corridorId = body.get("corridorId") != null ? Long.valueOf(body.get("corridorId").toString()) : null;
        BigDecimal rateBoost = new BigDecimal(body.get("rateBoostPercentage").toString());
        BigDecimal creditAmount = new BigDecimal(body.get("referrerCreditAmount").toString());
        String currency = body.getOrDefault("creditCurrency", "GBP").toString();
        Boolean isActive = body.get("isActive") != null ? Boolean.valueOf(body.get("isActive").toString()) : true;

        ReferralConfigEntity config = referralService.saveAdminConfig(corridorId, rateBoost, creditAmount, currency, isActive);
        return ResponseEntity.ok(ApiResponse.<ReferralConfigEntity>builder()
                .success(true)
                .data(config)
                .message("Referral config saved successfully")
                .build());
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private Long resolveUserId(String uuid) {
        return userRepository.findByUuid(uuid)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", uuid));
    }
}

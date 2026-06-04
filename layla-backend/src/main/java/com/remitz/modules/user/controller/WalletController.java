package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.WalletEntity;
import com.remitz.modules.user.entity.WalletLedgerEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.user.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;
    private final UserRepository userRepository;

    // ── Customer Endpoints ──────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<WalletEntity>> getMyWallet(
            @RequestHeader("X-User-UUID") String uuid) {
        Long userId = resolveUserId(uuid);
        WalletEntity wallet = walletService.getOrCreateWallet(userId);
        return ResponseEntity.ok(ApiResponse.<WalletEntity>builder()
                .success(true)
                .data(wallet)
                .message("Wallet retrieved successfully")
                .build());
    }

    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<List<WalletLedgerEntity>>> getMyTransactions(
            @RequestHeader("X-User-UUID") String uuid) {
        Long userId = resolveUserId(uuid);
        List<WalletLedgerEntity> transactions = walletService.getTransactions(userId);
        return ResponseEntity.ok(ApiResponse.<List<WalletLedgerEntity>>builder()
                .success(true)
                .data(transactions)
                .message("Transactions retrieved successfully")
                .build());
    }

    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<WalletEntity>> debit(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.getOrDefault("description", "").toString();
        String referenceId = body.getOrDefault("referenceId", "").toString();
        String referenceType = body.getOrDefault("referenceType", "").toString();

        WalletEntity wallet = walletService.debit(userId, amount, description, referenceId, referenceType);
        return ResponseEntity.ok(ApiResponse.<WalletEntity>builder()
                .success(true)
                .data(wallet)
                .message("Wallet debited successfully")
                .build());
    }

    @PostMapping("/credit")
    public ResponseEntity<ApiResponse<WalletEntity>> credit(@RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.getOrDefault("description", "").toString();
        String referenceId = body.getOrDefault("referenceId", "").toString();
        String referenceType = body.getOrDefault("referenceType", "").toString();

        WalletEntity wallet = walletService.credit(userId, amount, description, referenceId, referenceType);
        return ResponseEntity.ok(ApiResponse.<WalletEntity>builder()
                .success(true)
                .data(wallet)
                .message("Wallet credited successfully")
                .build());
    }

    // ── Admin Endpoints ─────────────────────────────────────────────────

    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<WalletEntity>>> getAllWallets() {
        List<WalletEntity> wallets = walletService.getAllWallets();
        return ResponseEntity.ok(ApiResponse.<List<WalletEntity>>builder()
                .success(true)
                .data(wallets)
                .message("All wallets retrieved successfully")
                .build());
    }

    @GetMapping("/admin/{userId}/transactions")
    public ResponseEntity<ApiResponse<List<WalletLedgerEntity>>> getAdminTransactions(
            @PathVariable Long userId) {
        List<WalletLedgerEntity> transactions = walletService.getTransactions(userId);
        return ResponseEntity.ok(ApiResponse.<List<WalletLedgerEntity>>builder()
                .success(true)
                .data(transactions)
                .message("Transactions retrieved successfully")
                .build());
    }

    @PostMapping("/admin/{userId}/credit")
    public ResponseEntity<ApiResponse<WalletEntity>> adminCredit(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.getOrDefault("description", "Admin credit").toString();

        WalletEntity wallet = walletService.adminCredit(userId, amount, description);
        return ResponseEntity.ok(ApiResponse.<WalletEntity>builder()
                .success(true)
                .data(wallet)
                .message("Admin credit applied successfully")
                .build());
    }

    @PostMapping("/admin/{userId}/debit")
    public ResponseEntity<ApiResponse<WalletEntity>> adminDebit(
            @PathVariable Long userId,
            @RequestBody Map<String, Object> body) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String description = body.getOrDefault("description", "Admin debit").toString();

        WalletEntity wallet = walletService.adminDebit(userId, amount, description);
        return ResponseEntity.ok(ApiResponse.<WalletEntity>builder()
                .success(true)
                .data(wallet)
                .message("Admin debit applied successfully")
                .build());
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private Long resolveUserId(String uuid) {
        return userRepository.findByUuid(uuid)
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", uuid));
    }
}

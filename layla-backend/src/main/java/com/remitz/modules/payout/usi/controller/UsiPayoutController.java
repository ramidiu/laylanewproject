package com.remitz.modules.payout.usi.controller;

import com.remitz.modules.payout.usi.dto.CollectionPointResponse;
import com.remitz.modules.payout.usi.dto.RemitterVerifyResponse;
import com.remitz.modules.payout.usi.dto.UsiAdminTransactionDto;
import com.remitz.modules.payout.usi.service.UsiPayoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payout/usi")
@RequiredArgsConstructor
@Tag(name = "USI Money — Payout",
        description = "USI Money XML API integration — create/verify remitters, create beneficiaries, create/confirm/poll transactions, fetch collection points.")
public class UsiPayoutController {

    private final UsiPayoutService service;

    // ── Admin list page ────────────────────────────────────────────────

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List USI-eligible transactions (admin USI Money page)",
            description = "Layla transactions whose destination is in a USI corridor (UG/TR/EG/QA/SA/AE) or that already have a USI mirror row. Includes USI-side state when known.")
    public ResponseEntity<List<UsiAdminTransactionDto>> listAdmin(
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "200")  int limit) {
        return ResponseEntity.ok(service.listAdminTransactions(status, limit));
    }

    // ── Remitter ──────────────────────────────────────────────────────

    @PostMapping("/remitter/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Create or fetch a USI remitter for a Layla user")
    public ResponseEntity<Map<String, Object>> createRemitter(@PathVariable Long userId) {
        return ResponseEntity.ok(service.createRemitter(userId));
    }

    @PostMapping("/remitter/{userId}/verify")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Verify a USI remitter — pulls latest verified-status from USI")
    public ResponseEntity<RemitterVerifyResponse> verifyRemitter(@PathVariable Long userId) {
        return ResponseEntity.ok(service.verifyRemitter(userId));
    }

    // ── Beneficiary ───────────────────────────────────────────────────

    @PostMapping("/beneficiary/{beneficiaryId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Create or fetch a USI beneficiary for a local beneficiary")
    public ResponseEntity<Map<String, Object>> createBeneficiary(@PathVariable Long beneficiaryId) {
        return ResponseEntity.ok(service.createBeneficiary(beneficiaryId));
    }

    @GetMapping("/beneficiary/{usiBeneficiaryId}/search")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Search a beneficiary by USI id — returns raw XML")
    public ResponseEntity<String> searchBeneficiary(@PathVariable String usiBeneficiaryId) {
        return ResponseEntity.ok(service.searchBeneficiary(usiBeneficiaryId));
    }

    // ── Transactions ──────────────────────────────────────────────────

    @PostMapping("/transaction/{referenceNumber}/create")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Create a USI transaction for a Layla transaction (by TXN reference)")
    public ResponseEntity<Map<String, Object>> createTransaction(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(service.createTransaction(referenceNumber));
    }

    @PostMapping("/transaction/{referenceNumber}/confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Confirm a USI transaction (after createTransaction succeeded)")
    public ResponseEntity<Map<String, Object>> confirmTransaction(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(service.confirmTransaction(referenceNumber));
    }

    @GetMapping("/transaction/{referenceNumber}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Poll USI for the latest status of a transaction")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String referenceNumber) {
        return ResponseEntity.ok(service.getTransactionStatus(referenceNumber));
    }

    @PostMapping("/transaction/bulk-create")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Create many USI transactions in one call")
    public ResponseEntity<List<Map<String, Object>>> bulkCreate(@RequestBody List<String> referenceNumbers) {
        return ResponseEntity.ok(service.createMultipleTransactions(referenceNumbers));
    }

    @PostMapping("/transaction/bulk-confirm")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Confirm many USI transactions in one call")
    public ResponseEntity<List<Map<String, Object>>> bulkConfirm(@RequestBody List<String> referenceNumbers) {
        return ResponseEntity.ok(service.confirmMultipleTransactions(referenceNumbers));
    }

    @PostMapping("/transaction/bulk-status")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN', 'PAYOUT_PARTNER')")
    @Operation(summary = "Poll many USI transactions in one call")
    public ResponseEntity<List<Map<String, Object>>> bulkStatus(@RequestBody List<String> referenceNumbers) {
        return ResponseEntity.ok(service.getMultipleTransactionStatus(referenceNumbers));
    }

    // ── Collection points ─────────────────────────────────────────────

    @GetMapping("/collection-points")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List USI collection points for a destination country",
            description = "Open to any logged-in user — the customer Add Recipient form calls this to populate the Cash Collection Point dropdown for USI-routed corridors.")
    public ResponseEntity<List<CollectionPointResponse>> collectionPoints(@RequestParam String country) {
        return ResponseEntity.ok(service.getCollectionPoints(country));
    }
}

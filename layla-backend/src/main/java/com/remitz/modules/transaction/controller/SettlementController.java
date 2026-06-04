package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.PayinPartner;
import com.remitz.modules.transaction.entity.PayoutPartner;
import com.remitz.modules.transaction.entity.Settlement;
import com.remitz.modules.transaction.repository.PayinPartnerRepository;
import com.remitz.modules.transaction.repository.PayoutPartnerRepository;
import com.remitz.modules.transaction.repository.SettlementRepository;
import com.remitz.modules.transaction.service.PartnerLedgerService;
import com.remitz.modules.transaction.service.PlatformLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions/settlements")
@RequiredArgsConstructor
@Tag(name = "Settlements", description = "Settlement management")
public class SettlementController {

    private final SettlementRepository settlementRepository;
    private final PayoutPartnerRepository payoutPartnerRepository;
    private final PayinPartnerRepository payinPartnerRepository;
    private final PartnerLedgerService partnerLedgerService;
    private final PlatformLedgerService platformLedgerService;

    @PostMapping("/admin-to-payout")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Initiate admin-to-payout partner settlement")
    public ResponseEntity<ApiResponse<Settlement>> initiateAdminToPayout(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long partnerId = Long.valueOf(body.get("partnerId").toString());
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String reference = body.get("notes") != null ? body.get("notes").toString()
                : (body.get("reference") != null ? body.get("reference").toString() : null);
        String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";

        PayoutPartner partner = payoutPartnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutPartner", "id", partnerId));

        Settlement settlement = Settlement.builder()
                .settlementType("ADMIN_TO_PAYOUT")
                .fromParty("ADMIN")
                .fromPartyId(null)
                .toParty("PAYOUT_PARTNER")
                .toPartyId(partnerId)
                .amount(amount)
                .currency(currency)
                .reference(reference)
                .status("PENDING")
                .initiatedBy(authentication.getName())
                .build();

        Settlement saved = settlementRepository.save(settlement);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Settlement>builder()
                        .success(true)
                        .data(saved)
                        .message("Settlement initiated successfully")
                        .build());
    }

    @PostMapping("/payin-to-admin")
    @Operation(summary = "Initiate pay-in-to-admin settlement")
    public ResponseEntity<ApiResponse<Settlement>> initiatePayinToAdmin(
            @RequestBody Map<String, Object> body,
            Authentication authentication,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        // Accept either `notes` (admin form) or `reference` (legacy) as the narrative field.
        String reference = body.get("notes") != null ? body.get("notes").toString()
                : (body.get("reference") != null ? body.get("reference").toString() : null);
        String currency = body.get("currency") != null ? body.get("currency").toString() : "USD";

        // Resolve the pay-in partner. Priority:
        //   1. partnerId in request body (admin picking from dropdown)
        //   2. X-Partner-Id header (admin impersonating partner portal)
        //   3. caller's JWT userId (actual partner user calling directly)
        PayinPartner partner;
        Object bodyPartnerId = body.get("partnerId");
        if (bodyPartnerId != null) {
            Long pid = Long.valueOf(bodyPartnerId.toString());
            partner = payinPartnerRepository.findById(pid)
                    .orElseThrow(() -> new RemitzException("Pay-in partner not found: " + pid, HttpStatus.NOT_FOUND));
        } else if (adminPartnerId != null) {
            partner = payinPartnerRepository.findById(adminPartnerId)
                    .orElseThrow(() -> new RemitzException("Pay-in partner not found: " + adminPartnerId, HttpStatus.NOT_FOUND));
        } else {
            partner = payinPartnerRepository.findByUserId(userId)
                    .orElseThrow(() -> new RemitzException("Pay-in partner not found", HttpStatus.NOT_FOUND));
        }

        Settlement settlement = Settlement.builder()
                .settlementType("PAYIN_TO_ADMIN")
                .fromParty("PAYIN_PARTNER")
                .fromPartyId(partner.getId())
                .toParty("ADMIN")
                .toPartyId(null)
                .amount(amount)
                .currency(currency)
                .reference(reference)
                .status("PENDING")
                .initiatedBy(authentication != null ? authentication.getName() : String.valueOf(userId))
                .build();

        Settlement saved = settlementRepository.save(settlement);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<Settlement>builder()
                        .success(true)
                        .data(saved)
                        .message("Settlement initiated successfully")
                        .build());
    }

    @PutMapping("/{id}/approve")
    
    @Operation(summary = "Approve settlement and reflect in ledgers")
    public ResponseEntity<ApiResponse<Settlement>> approveSettlement(@PathVariable Long id,
                                                                      Authentication authentication) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", "id", id));

        if (!"PENDING".equals(settlement.getStatus())) {
            throw new RemitzException("Settlement is not in PENDING status", HttpStatus.BAD_REQUEST);
        }

        settlement.setStatus("APPROVED");
        settlement.setApprovedBy(authentication.getName());

        // Reflect in ledgers based on type
        if ("ADMIN_TO_PAYOUT".equals(settlement.getSettlementType())) {
            partnerLedgerService.addPartnerEntry(
                    settlement.getToPartyId(), null, null,
                    "CREDIT", settlement.getAmount(), settlement.getCurrency(),
                    settlement.getAmount(), BigDecimal.ONE,
                    "Settlement approved: " + settlement.getReference());

            platformLedgerService.addEntry(
                    null, null,
                    "DEBIT", settlement.getAmount(), settlement.getCurrency(),
                    settlement.getAmount(), BigDecimal.ONE,
                    "Settlement to payout partner: " + settlement.getReference(), "PAYOUT");
        } else if ("PAYIN_TO_ADMIN".equals(settlement.getSettlementType())) {
            partnerLedgerService.addPayinPartnerEntry(
                    settlement.getFromPartyId(), null, null,
                    "DEBIT", settlement.getAmount(), settlement.getCurrency(),
                    settlement.getAmount(), BigDecimal.ONE,
                    "Settlement approved: " + settlement.getReference());

            platformLedgerService.addEntry(
                    null, null,
                    "CREDIT", settlement.getAmount(), settlement.getCurrency(),
                    settlement.getAmount(), BigDecimal.ONE,
                    "Settlement from payin partner: " + settlement.getReference(), "HOLDING");
        }

        Settlement saved = settlementRepository.save(settlement);
        return ResponseEntity.ok(ApiResponse.<Settlement>builder()
                .success(true)
                .data(saved)
                .message("Settlement approved successfully")
                .build());
    }

    @PutMapping("/{id}/reject")
    
    @Operation(summary = "Reject settlement")
    public ResponseEntity<ApiResponse<Settlement>> rejectSettlement(@PathVariable Long id,
                                                                     @RequestBody Map<String, String> body) {
        Settlement settlement = settlementRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement", "id", id));

        if (!"PENDING".equals(settlement.getStatus())) {
            throw new RemitzException("Settlement is not in PENDING status", HttpStatus.BAD_REQUEST);
        }

        settlement.setStatus("REJECTED");
        settlement.setRejectedReason(body.get("reason"));

        Settlement saved = settlementRepository.save(settlement);
        return ResponseEntity.ok(ApiResponse.<Settlement>builder()
                .success(true)
                .data(saved)
                .message("Settlement rejected")
                .build());
    }

    @GetMapping("/all")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Get all settlements")
    public ResponseEntity<ApiResponse<List<Settlement>>> getAllSettlements() {
        List<Settlement> settlements = settlementRepository.findAllByOrderByCreatedAtDesc();
        return ResponseEntity.ok(ApiResponse.<List<Settlement>>builder()
                .success(true)
                .data(settlements)
                .build());
    }

    @GetMapping("/pending")
    @Operation(summary = "Get pending settlements")
    public ResponseEntity<ApiResponse<List<Settlement>>> getPendingSettlements() {
        List<Settlement> settlements = settlementRepository.findByStatusOrderByCreatedAtDesc("PENDING");
        return ResponseEntity.ok(ApiResponse.<List<Settlement>>builder()
                .success(true)
                .data(settlements)
                .build());
    }

    @GetMapping("/my")
    @Operation(summary = "Get my settlements")
    public ResponseEntity<ApiResponse<List<Settlement>>> getMySettlements(
            @RequestHeader(value = "X-User-UUID", required = false) String userUuid) {
        List<Settlement> all = settlementRepository.findAllByOrderByCreatedAtDesc();
        long userHash = userUuid != null ? (long) userUuid.hashCode() : 0L;
        List<Settlement> mine = all.stream()
                .filter(s -> (userUuid != null && userUuid.equals(s.getInitiatedBy()))
                        || (s.getFromPartyId() != null && (s.getFromPartyId().equals(userHash) || s.getFromPartyId().toString().equals(userUuid)))
                        || (s.getToPartyId() != null && (s.getToPartyId().equals(userHash) || s.getToPartyId().toString().equals(userUuid))))
                .toList();

        return ResponseEntity.ok(ApiResponse.<List<Settlement>>builder()
                .success(true)
                .data(mine)
                .build());
    }
}

package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.config.RedisPublisher;
import com.remitz.modules.transaction.entity.PayinPartner;
import com.remitz.modules.transaction.entity.PayinPartnerLedger;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.PayinPartnerRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.auth.dto.RegisterPartnerRequest;
import com.remitz.modules.auth.dto.RegisterResponse;
import com.remitz.modules.auth.service.AuthService;
import com.remitz.modules.transaction.service.FeeDistributionService;
import com.remitz.modules.transaction.service.LedgerService;
import com.remitz.modules.transaction.service.PartnerLedgerService;
import com.remitz.modules.transaction.service.PlatformLedgerService;
import com.remitz.modules.transaction.service.TransactionStateMachine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions/corridors")
@Tag(name = "Pay-in Partners", description = "Pay-in partner management")
@Slf4j
public class PayinPartnerController {

    private final PayinPartnerRepository payinPartnerRepository;
    private final TransactionRepository transactionRepository;
    private final PartnerLedgerService partnerLedgerService;
    private final LedgerService ledgerService;
    private final PlatformLedgerService platformLedgerService;
    private final TransactionStateMachine stateMachine;
    private final RedisPublisher redisPublisher;
    private final FeeDistributionService feeDistributionService;
    private final AuthService authService;
    // Code added by Naresh: userRepository for partner resolution by UUID -> email fallback
    private final com.remitz.modules.auth.repository.UserRepository userRepository;
    // Code added by Naresh: System Controls Phase 7 — runtime payin master switch.
    private final com.remitz.modules.user.service.SystemConfigService systemConfigService;

    public PayinPartnerController(PayinPartnerRepository payinPartnerRepository,
                                   TransactionRepository transactionRepository,
                                   PartnerLedgerService partnerLedgerService,
                                   LedgerService ledgerService,
                                   PlatformLedgerService platformLedgerService,
                                   TransactionStateMachine stateMachine,
                                   RedisPublisher redisPublisher,
                                   FeeDistributionService feeDistributionService,
                                   AuthService authService,
                                   com.remitz.modules.auth.repository.UserRepository userRepository,
                                   com.remitz.modules.user.service.SystemConfigService systemConfigService) {
        this.payinPartnerRepository = payinPartnerRepository;
        this.transactionRepository = transactionRepository;
        this.partnerLedgerService = partnerLedgerService;
        this.ledgerService = ledgerService;
        this.platformLedgerService = platformLedgerService;
        this.stateMachine = stateMachine;
        this.redisPublisher = redisPublisher;
        this.feeDistributionService = feeDistributionService;
        this.authService = authService;
        this.userRepository = userRepository;
        this.systemConfigService = systemConfigService;
    }

    /**
     * Code added by Naresh: Read runtime control from system_config with safe fallback.
     * Gate for payin-partner write actions (received-funds, reject, release-compliance).
     */
    private void ensurePayinEnabled() {
        if (!systemConfigService.getBoolean("payin.enabled", true)) {
            throw new com.remitz.common.exception.RemitzException(
                    "Pay-in actions are temporarily disabled.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @GetMapping("/payin-partners")
    @PreAuthorize("hasPermission(null, 'partner:manage_payin')")
    @Operation(summary = "List all pay-in partners")
    public ResponseEntity<ApiResponse<List<PayinPartner>>> listPayinPartners() {
        List<PayinPartner> partners = payinPartnerRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<PayinPartner>>builder()
                .success(true)
                .data(partners)
                .build());
    }

    @PostMapping("/payin-partners")
    @PreAuthorize("hasPermission(null, 'partner:manage_payin')")
    @Operation(summary = "Create pay-in partner")
    public ResponseEntity<ApiResponse<PayinPartner>> createPayinPartner(@RequestBody Map<String, Object> request) {
        // Extract partner fields
        PayinPartner partner = new PayinPartner();
        partner.setPartnerName((String) request.get("partnerName"));
        partner.setContactEmail((String) request.get("contactEmail"));
        partner.setContactPhone((String) request.get("contactPhone"));

        String password = (String) request.get("password");

        // Save partner first
        PayinPartner saved = payinPartnerRepository.save(partner);

        // Register user account via auth module
        if (password != null && !password.isBlank()) {
            try {
                RegisterResponse authResponse = authService.registerPartner(
                        RegisterPartnerRequest.builder()
                                .email(saved.getContactEmail())
                                .password(password)
                                .firstName(saved.getPartnerName())
                                .lastName("Partner")
                                .phone(saved.getContactPhone() != null ? saved.getContactPhone() : "")
                                .role("PAYIN_PARTNER")
                                .build());
                if (authResponse != null && authResponse.getUuid() != null) {
                    // Resolve the actual DB id from the UUID so resolvePayinPartner() can match by userId
                    userRepository.findByUuid(authResponse.getUuid()).ifPresent(u -> {
                        saved.setUserId(u.getId());
                        payinPartnerRepository.save(saved);
                    });
                    log.info("Created user account for pay-in partner {} with UUID: {}", saved.getPartnerName(), authResponse.getUuid());
                }
            } catch (Exception e) {
                log.error("Failed to create user account for pay-in partner {}: {}", saved.getPartnerName(), e.getMessage());
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PayinPartner>builder()
                        .success(true)
                        .data(saved)
                        .message("Pay-in partner created successfully")
                        .build());
    }

    @PutMapping("/payin-partners/{id}")
    @PreAuthorize("hasPermission(null, 'partner:manage_payin')")
    @Operation(summary = "Edit pay-in partner")
    public ResponseEntity<ApiResponse<PayinPartner>> editPayinPartner(@PathVariable Long id,
                                                                       @RequestBody PayinPartner request) {
        PayinPartner partner = payinPartnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayinPartner", "id", id));

        if (request.getPartnerName() != null) partner.setPartnerName(request.getPartnerName());
        if (request.getUserId() != null) partner.setUserId(request.getUserId());
        if (request.getContactEmail() != null) partner.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) partner.setContactPhone(request.getContactPhone());
        if (request.getIsActive() != null) partner.setIsActive(request.getIsActive());

        PayinPartner saved = payinPartnerRepository.save(partner);
        return ResponseEntity.ok(ApiResponse.<PayinPartner>builder()
                .success(true)
                .data(saved)
                .message("Pay-in partner updated successfully")
                .build());
    }

    @PutMapping("/my-transactions/{txnId}/received")
    @PreAuthorize("hasRole('PAYIN_PARTNER') or hasAuthority('partner:manage_payin')")
    @Operation(summary = "Pay-in partner marks funds received for a transaction")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> markFundsReceived(
            @PathVariable Long txnId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId,
            @RequestBody(required = false) Map<String, String> body) {

        ensurePayinEnabled();
        PayinPartner partner = resolvePayinPartner(userId, adminPartnerId);

        TransactionEntity tx = transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", txnId));

        // Ownership check — transaction must be routed to this pay-in partner.
        if (!partner.getId().equals(tx.getPayinPartnerId())) {
            throw new RemitzException("Transaction not assigned to this pay-in partner", HttpStatus.FORBIDDEN);
        }

        // RULE (Pay-In stage): partner can act ONLY when ACTIVE. INACTIVE → admin handles.
        if (!Boolean.TRUE.equals(partner.getIsActive())) {
            throw new RemitzException(
                    "This pay-in partner is INACTIVE. Admin handles pay-in. Activate the partner to take over.",
                    HttpStatus.FORBIDDEN);
        }

        // Status guard — same rule as admin funds-received.
        if (tx.getStatus() != TransactionStatus.PENDING && tx.getStatus() != TransactionStatus.CREATED) {
            throw new RemitzException(
                    "Transaction must be in PENDING or CREATED status. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String paymentReference = body != null ? body.get("paymentReference") : null;
        if (paymentReference != null && !paymentReference.isBlank()) {
            tx.setPaymentReference(paymentReference);
        }

        // CREATED → PENDING → PROCESSING (mirrors admin funds-received).
        if (tx.getStatus() == TransactionStatus.CREATED) {
            tx = stateMachine.transition(tx, TransactionStatus.PENDING, userId, ActorType.PAYIN_PARTNER,
                    "Auto-advanced to PENDING by pay-in partner");
        }
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.PROCESSING, userId,
                ActorType.PAYIN_PARTNER, "Funds received by pay-in partner");

        // Ledger entries (identical to admin path so audit trail is consistent).
        ledgerService.createEntry(updated.getId(), "SENDER:" + updated.getSenderId(), "PLATFORM:HOLDING",
                updated.getSendAmount(), updated.getSendCurrency(), "TRANSFER",
                "Payment received for " + updated.getReferenceNumber());

        if (updated.getFeeAmount() != null && updated.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.createEntry(updated.getId(), "SENDER:" + updated.getSenderId(), "PLATFORM:REVENUE",
                    updated.getFeeAmount(), updated.getFeeCurrency(), "FEE",
                    "Fee collected for " + updated.getReferenceNumber());
        }

        // Platform ledger CREDIT (holding account funded by pay-in partner) + fee split
        // bookkeeping. Identical to the admin path so audit & balances stay symmetric.
        try {
            BigDecimal settlementRate = feeDistributionService.getSettlementRate(updated.getSendCurrency());
            BigDecimal sendAmountUsd = feeDistributionService.convertToUsd(updated.getSendAmount(), settlementRate);

            platformLedgerService.addEntry(updated.getId(), updated.getReferenceNumber(),
                    "CREDIT", updated.getSendAmount(), updated.getSendCurrency(),
                    sendAmountUsd, settlementRate,
                    "Payment received via pay-in partner " + partner.getPartnerName(), "HOLDING");

            // Fee split: payin commission, payout share allocation, admin revenue.
            feeDistributionService.processFeeDistribution(updated, settlementRate);
        } catch (Exception e) {
            log.warn("Failed to create platform/partner ledger entries for tx {}: {}", updated.getId(), e.getMessage());
        }

        // Admin-visible event (same channel admin's funds-received uses).
        redisPublisher.publishTransactionEvent("FUNDS_RECEIVED", Map.of(
                "transactionId", updated.getId(),
                "referenceNumber", updated.getReferenceNumber(),
                "senderId", updated.getSenderId(),
                "amount", updated.getSendAmount().toString(),
                "currency", updated.getSendCurrency(),
                "payinPartnerId", partner.getId()
        ));

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Funds received recorded successfully")
                .build());
    }

    @PutMapping("/my-transactions/{txnId}/reject")
    @PreAuthorize("hasRole('PAYIN_PARTNER') or hasAuthority('partner:manage_payin')")
    @Operation(summary = "Pay-in partner rejects a transaction (transitions to CANCELLED)")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> rejectTransaction(
            @PathVariable Long txnId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId,
            @RequestBody(required = false) Map<String, String> body) {

        ensurePayinEnabled();
        PayinPartner partner = resolvePayinPartner(userId, adminPartnerId);

        TransactionEntity tx = transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", txnId));

        if (!partner.getId().equals(tx.getPayinPartnerId())) {
            throw new RemitzException("Transaction not assigned to this pay-in partner", HttpStatus.FORBIDDEN);
        }

        if (tx.getStatus() != TransactionStatus.PENDING
                && tx.getStatus() != TransactionStatus.CREATED
                && tx.getStatus() != TransactionStatus.COMPLIANCE_HOLD) {
            throw new RemitzException(
                    "Transaction cannot be rejected from status " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String reason = body != null && body.get("reason") != null ? body.get("reason") : "Rejected by pay-in partner";
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.CANCELLED, userId,
                ActorType.PAYIN_PARTNER, reason);

        redisPublisher.publishTransactionEvent("TRANSACTION_REJECTED", Map.of(
                "transactionId", updated.getId(),
                "referenceNumber", updated.getReferenceNumber(),
                "senderId", updated.getSenderId(),
                "payinPartnerId", partner.getId(),
                "reason", reason
        ));

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Transaction rejected")
                .build());
    }

    @PutMapping("/my-transactions/{txnId}/release-compliance")
    @PreAuthorize("hasRole('PAYIN_PARTNER') or hasAuthority('partner:manage_payin')")
    @Operation(summary = "Pay-in partner releases their assigned transaction from COMPLIANCE_HOLD back to PENDING")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> releaseFromCompliance(
            @PathVariable Long txnId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId,
            @RequestBody(required = false) Map<String, String> body) {

        ensurePayinEnabled();
        PayinPartner partner = resolvePayinPartner(userId, adminPartnerId);

        TransactionEntity tx = transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", txnId));

        if (!partner.getId().equals(tx.getPayinPartnerId())) {
            throw new RemitzException("Transaction not assigned to this pay-in partner", HttpStatus.FORBIDDEN);
        }

        if (tx.getStatus() != TransactionStatus.COMPLIANCE_HOLD) {
            throw new RemitzException(
                    "Transaction is not on COMPLIANCE_HOLD. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String reason = body != null && body.get("reason") != null ? body.get("reason") : "Compliance cleared by pay-in partner";
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.PENDING, userId,
                ActorType.PAYIN_PARTNER, reason);

        redisPublisher.publishTransactionEvent("COMPLIANCE_RELEASED", Map.of(
                "transactionId", updated.getId(),
                "referenceNumber", updated.getReferenceNumber(),
                "senderId", updated.getSenderId(),
                "payinPartnerId", partner.getId(),
                "reason", reason
        ));

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Transaction released from compliance hold")
                .build());
    }

    @GetMapping("/my-transactions")
    @Operation(summary = "Get pay-in partner's transactions")
    public ResponseEntity<ApiResponse<List<TransactionEntity>>> getMyTransactions(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        PayinPartner partner = resolvePayinPartner(userId, adminPartnerId);

        List<TransactionEntity> transactions = transactionRepository.findByPayinPartnerId(partner.getId());
        return ResponseEntity.ok(ApiResponse.<List<TransactionEntity>>builder()
                .success(true)
                .data(transactions)
                .build());
    }

    @GetMapping("/my-ledger")
    @Operation(summary = "Get pay-in partner ledger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyLedger(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        PayinPartner partner = resolvePayinPartner(userId, adminPartnerId);

        List<PayinPartnerLedger> ledger = partnerLedgerService.getPayinPartnerLedger(partner.getId());
        BigDecimal balance = partnerLedgerService.getPayinPartnerBalance(partner.getId());

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(Map.of("entries", ledger, "balance", balance))
                .build());
    }

    @GetMapping("/payin-balances")
    @PreAuthorize("hasPermission(null, 'partner:manage_payin')")
    @Operation(summary = "Get all pay-in partner balances")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPayinBalances() {
        List<PayinPartner> partners = payinPartnerRepository.findAll();
        List<Map<String, Object>> balances = partners.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("partnerId", p.getId());
                    map.put("partnerName", p.getPartnerName());
                    map.put("balance", partnerLedgerService.getPayinPartnerBalance(p.getId()));
                    return map;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(balances)
                .build());
    }

    private PayinPartner resolvePayinPartner(Long userId, Long adminPartnerId) {
        if (adminPartnerId != null) {
            return payinPartnerRepository.findById(adminPartnerId)
                    .orElseThrow(() -> new RemitzException("Pay-in partner not found: " + adminPartnerId, HttpStatus.NOT_FOUND));
        }
        if (userId != null) {
            java.util.Optional<PayinPartner> byUser = payinPartnerRepository.findByUserId(userId);
            if (byUser.isPresent()) return byUser.get();
        }
        // Code added by Naresh: email + UUID fallback for monolith (JWT has no numeric userId claim,
        // and some seeded partner rows have a stale user_id that doesn't match users.id).
        try {
            org.springframework.security.core.Authentication auth =
                    org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getName() != null) {
                String uuid = auth.getName();
                java.util.Optional<com.remitz.modules.auth.entity.UserEntity> userOpt =
                        userRepository.findByUuid(uuid);
                if (userOpt.isPresent()) {
                    com.remitz.modules.auth.entity.UserEntity u = userOpt.get();
                    java.util.Optional<PayinPartner> byResolvedId = payinPartnerRepository.findByUserId(u.getId());
                    if (byResolvedId.isPresent()) return byResolvedId.get();
                    if (u.getEmail() != null) {
                        java.util.Optional<PayinPartner> byEmail = payinPartnerRepository.findByContactEmail(u.getEmail());
                        if (byEmail.isPresent()) return byEmail.get();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Fallback partner lookup failed: {}", e.getMessage());
        }
        // Strict: never silently fall back to "first partner" — that would let an
        // unmapped caller act on someone else's transactions.
        throw new RemitzException("Pay-in partner not resolvable for caller", HttpStatus.FORBIDDEN);
    }
}

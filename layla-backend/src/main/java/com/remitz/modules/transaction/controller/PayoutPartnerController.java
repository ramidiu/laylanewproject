package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.*;
import com.remitz.modules.transaction.repository.*;
import com.remitz.modules.auth.dto.RegisterPartnerRequest;
import com.remitz.modules.auth.dto.RegisterResponse;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.auth.service.AuthService;
import com.remitz.modules.transaction.service.PartnerLedgerService;
import com.remitz.modules.transaction.service.PlatformLedgerService;
import com.remitz.modules.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions/partners")
@Tag(name = "Payout Partners", description = "Payout partner management")
@Slf4j
public class PayoutPartnerController {

    private final PayoutPartnerRepository payoutPartnerRepository;
    private final PayoutPartnerCountryRepository payoutPartnerCountryRepository;
    private final TransactionRepository transactionRepository;
    private final PartnerLedgerService partnerLedgerService;
    private final PlatformLedgerService platformLedgerService;
    private final SettlementRateRepository settlementRateRepository;
    private final TransactionService transactionService;
    private final BeneficiaryRepository beneficiaryRepository;
    private final AuthService authService;
    private final UserRepository userRepository;
    // Code added by Naresh: System Controls Phase 7 — runtime payout master switch.
    private final com.remitz.modules.user.service.SystemConfigService systemConfigService;

    public PayoutPartnerController(PayoutPartnerRepository payoutPartnerRepository,
                                    PayoutPartnerCountryRepository payoutPartnerCountryRepository,
                                    TransactionRepository transactionRepository,
                                    PartnerLedgerService partnerLedgerService,
                                    PlatformLedgerService platformLedgerService,
                                    SettlementRateRepository settlementRateRepository,
                                    TransactionService transactionService,
                                    BeneficiaryRepository beneficiaryRepository,
                                    AuthService authService,
                                    UserRepository userRepository,
                                    com.remitz.modules.user.service.SystemConfigService systemConfigService) {
        this.payoutPartnerRepository = payoutPartnerRepository;
        this.payoutPartnerCountryRepository = payoutPartnerCountryRepository;
        this.transactionRepository = transactionRepository;
        this.partnerLedgerService = partnerLedgerService;
        this.platformLedgerService = platformLedgerService;
        this.settlementRateRepository = settlementRateRepository;
        this.transactionService = transactionService;
        this.beneficiaryRepository = beneficiaryRepository;
        this.authService = authService;
        this.userRepository = userRepository;
        this.systemConfigService = systemConfigService;
    }

    /**
     * Code added by Naresh: Read runtime control from system_config with safe fallback.
     * Gate for payout-partner completion actions.
     */
    private void ensurePayoutEnabled() {
        if (!systemConfigService.getBoolean("payout.enabled", true)) {
            throw new com.remitz.common.exception.RemitzException(
                    "Pay-out actions are temporarily disabled.",
                    org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'partner:manage_payout')")
    @Operation(summary = "Create payout partner")
    public ResponseEntity<ApiResponse<PayoutPartner>> createPartner(@RequestBody Map<String, Object> request) {
        // Extract partner fields
        PayoutPartner partner = new PayoutPartner();
        partner.setPartnerName((String) request.get("partnerName"));
        partner.setContactEmail((String) request.get("contactEmail"));
        partner.setContactPhone((String) request.get("contactPhone"));

        String password = (String) request.get("password");

        // Save partner first
        PayoutPartner saved = payoutPartnerRepository.save(partner);

        // Register user account via auth-service
        if (password != null && !password.isBlank()) {
            try {
                RegisterResponse authResponse = authService.registerPartner(
                        RegisterPartnerRequest.builder()
                                .email(saved.getContactEmail())
                                .password(password)
                                .firstName(saved.getPartnerName())
                                .lastName("Partner")
                                .phone(saved.getContactPhone() != null ? saved.getContactPhone() : "")
                                .role("PAYOUT_PARTNER")
                                .build());
                if (authResponse != null && authResponse.getUuid() != null) {
                    saved.setUserId((long) authResponse.getUuid().hashCode());
                    payoutPartnerRepository.save(saved);
                    log.info("Created user account for payout partner {} with UUID: {}", saved.getPartnerName(), authResponse.getUuid());
                }
            } catch (Exception e) {
                log.error("Failed to create user account for payout partner {}: {}", saved.getPartnerName(), e.getMessage());
                // Partner is still created, but user account failed - log the error
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PayoutPartner>builder()
                        .success(true)
                        .data(saved)
                        .message("Payout partner created successfully")
                        .build());
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'partner:manage_payout')")
    @Operation(summary = "List all payout partners")
    public ResponseEntity<ApiResponse<List<PayoutPartner>>> listPartners() {
        List<PayoutPartner> partners = payoutPartnerRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<PayoutPartner>>builder()
                .success(true)
                .data(partners)
                .build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission(null, 'partner:manage_payout')")
    @Operation(summary = "Edit payout partner")
    public ResponseEntity<ApiResponse<PayoutPartner>> editPartner(@PathVariable Long id,
                                                                   @RequestBody PayoutPartner request) {
        PayoutPartner partner = payoutPartnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutPartner", "id", id));

        if (request.getPartnerName() != null) partner.setPartnerName(request.getPartnerName());
        if (request.getUserId() != null) partner.setUserId(request.getUserId());
        if (request.getContactEmail() != null) partner.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null) partner.setContactPhone(request.getContactPhone());

        PayoutPartner saved = payoutPartnerRepository.save(partner);
        return ResponseEntity.ok(ApiResponse.<PayoutPartner>builder()
                .success(true)
                .data(saved)
                .message("Payout partner updated successfully")
                .build());
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasPermission(null, 'partner:manage_payout')")
    @Operation(summary = "Toggle partner active status")
    public ResponseEntity<ApiResponse<PayoutPartner>> togglePartner(@PathVariable Long id) {
        PayoutPartner partner = payoutPartnerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutPartner", "id", id));

        partner.setIsActive(!partner.getIsActive());
        PayoutPartner saved = payoutPartnerRepository.save(partner);
        return ResponseEntity.ok(ApiResponse.<PayoutPartner>builder()
                .success(true)
                .data(saved)
                .message("Partner status toggled successfully")
                .build());
    }

    @PostMapping("/{id}/countries")
    @PreAuthorize("hasPermission(null, 'partner:manage_payout')")
    @Operation(summary = "Assign country to partner")
    public ResponseEntity<ApiResponse<PayoutPartnerCountry>> assignCountry(@PathVariable Long id,
                                                                           @RequestBody PayoutPartnerCountry country) {
        if (!payoutPartnerRepository.existsById(id)) {
            throw new ResourceNotFoundException("PayoutPartner", "id", id);
        }
        country.setPartnerId(id);
        PayoutPartnerCountry saved = payoutPartnerCountryRepository.save(country);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<PayoutPartnerCountry>builder()
                        .success(true)
                        .data(saved)
                        .message("Country assigned to partner successfully")
                        .build());
    }

    @DeleteMapping("/{id}/countries/{countryId}")
    @PreAuthorize("hasPermission(null, 'partner:manage_payout')")
    @Operation(summary = "Remove country from partner")
    public ResponseEntity<ApiResponse<Void>> removeCountry(@PathVariable Long id, @PathVariable Long countryId) {
        payoutPartnerCountryRepository.deleteById(countryId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Country removed from partner successfully")
                .build());
    }

    @GetMapping("/{id}/countries")
    @Operation(summary = "List partner's countries")
    public ResponseEntity<ApiResponse<List<PayoutPartnerCountry>>> listCountries(@PathVariable Long id) {
        List<PayoutPartnerCountry> countries = payoutPartnerCountryRepository.findByPartnerId(id);
        return ResponseEntity.ok(ApiResponse.<List<PayoutPartnerCountry>>builder()
                .success(true)
                .data(countries)
                .build());
    }

    @GetMapping("/my-transactions")
    @Operation(summary = "Get pending payout transactions (PROCESSING / FUNDS_RECEIVED / SENT_TO_PAYOUT)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyTransactions(
            @RequestHeader(value = "X-User-UUID", required = false) String userUuid,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        PayoutPartner partner = findPartnerForUser(userUuid, userId, userEmail, adminPartnerId);

        List<TransactionEntity> transactions = transactionRepository.findByPayoutPartnerIdAndStatusIn(
                partner.getId(),
                java.util.List.of(
                        TransactionStatus.PROCESSING,
                        TransactionStatus.FUNDS_RECEIVED,
                        TransactionStatus.SENT_TO_PAYOUT));
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(transactions.stream().map(this::enrichWithBeneficiary).toList())
                .build());
    }

    /**
     * Hydrate a transaction with beneficiary details so the payout partner UI can show
     * who the money is going to (name, bank, account, mobile, etc.) — TransactionEntity
     * only carries beneficiaryId.
     */
    private Map<String, Object> enrichWithBeneficiary(TransactionEntity tx) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("id", tx.getId());
        map.put("referenceNumber", tx.getReferenceNumber());
        map.put("status", tx.getStatus() != null ? tx.getStatus().name() : null);
        map.put("deliveryMethod", tx.getDeliveryMethod() != null ? tx.getDeliveryMethod().name() : null);
        map.put("sendAmount", tx.getSendAmount());
        map.put("sendCurrency", tx.getSendCurrency());
        map.put("receiveAmount", tx.getReceiveAmount());
        map.put("receiveCurrency", tx.getReceiveCurrency());
        map.put("feeAmount", tx.getFeeAmount());
        map.put("paymentMethodType", tx.getPaymentMethodType());
        map.put("payinPartnerId", tx.getPayinPartnerId());
        map.put("payoutPartnerId", tx.getPayoutPartnerId());
        map.put("createdAt", tx.getCreatedAt());
        map.put("updatedAt", tx.getUpdatedAt());
        map.put("senderName", tx.getSenderName());
        map.put("beneficiaryId", tx.getBeneficiaryId());
        if (tx.getBeneficiaryId() != null) {
            beneficiaryRepository.findById(tx.getBeneficiaryId()).ifPresent(b -> {
                map.put("beneficiaryName", b.getFullName());
                map.put("beneficiaryCountry", b.getCountry());
                map.put("beneficiaryBankName", b.getBankName());
                map.put("beneficiaryAccountNumber", b.getAccountNumber());
                map.put("beneficiarySwiftBic", b.getSwiftBic());
                map.put("beneficiaryIban", b.getIban());
                map.put("beneficiaryMobileNumber", b.getMobileNumber());
                map.put("beneficiaryMobileProvider", b.getMobileProvider());
                map.put("beneficiaryBranch", b.getSortCode());
                map.put("beneficiaryBranchState", b.getBranchState());
                map.put("beneficiaryBranchCity", b.getBranchCity());
                map.put("beneficiaryAddress", b.getAddress());
            });
        }
        return map;
    }

    @GetMapping("/my-completed")
    @Operation(summary = "Get completed transactions for payout partner (PAID + COMPLETED)")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getMyCompleted(
            @RequestHeader(value = "X-User-UUID", required = false) String userUuid,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        PayoutPartner partner = findPartnerForUser(userUuid, userId, userEmail, adminPartnerId);

        List<TransactionEntity> transactions = transactionRepository.findByPayoutPartnerIdAndStatusIn(
                partner.getId(),
                java.util.List.of(TransactionStatus.PAID, TransactionStatus.COMPLETED));
        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(transactions.stream().map(this::enrichWithBeneficiary).toList())
                .build());
    }

    @GetMapping("/my-ledger")
    @Operation(summary = "Get partner ledger")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyLedger(
            @RequestHeader(value = "X-User-UUID", required = false) String userUuid,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        PayoutPartner partner = findPartnerForUser(userUuid, userId, userEmail, adminPartnerId);

        List<PartnerLedger> ledger = partnerLedgerService.getPartnerLedger(partner.getId());
        java.math.BigDecimal balance = partnerLedgerService.getPartnerBalance(partner.getId());

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(Map.of("entries", ledger, "balance", balance))
                .build());
    }

    @PutMapping("/payout/{txnId}")
    @PreAuthorize("hasRole('PAYOUT_PARTNER') or hasAuthority('partner:manage_payout')")
    @Operation(summary = "Mark transaction as PAID by payout partner")
    public ResponseEntity<ApiResponse<Void>> markAsPaid(@PathVariable Long txnId,
                                                         @RequestHeader(value = "X-User-UUID", required = false) String userUuid,
                                                         @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                         @RequestHeader(value = "X-User-Email", required = false) String userEmail,
                                                         @RequestHeader(value = "X-Partner-Id", required = false) Long adminPartnerId) {
        ensurePayoutEnabled();
        PayoutPartner partner = findPartnerForUser(userUuid, userId, userEmail, adminPartnerId);

        TransactionEntity tx = transactionRepository.findById(txnId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", txnId));

        if (!partner.getId().equals(tx.getPayoutPartnerId())) {
            throw new RemitzException("Transaction not assigned to this partner", HttpStatus.FORBIDDEN);
        }

        // RULE (Pay-Out stage): partner can act ONLY when ACTIVE. INACTIVE → admin handles.
        if (!Boolean.TRUE.equals(partner.getIsActive())) {
            throw new RemitzException(
                    "This payout partner is INACTIVE. Admin handles payout. Activate the partner to take over.",
                    HttpStatus.FORBIDDEN);
        }

        // State machine: FUNDS_RECEIVED → SENT_TO_PAYOUT → PAID; PROCESSING → PAID directly
        if (tx.getStatus() == TransactionStatus.FUNDS_RECEIVED) {
            transactionService.updateStatus(txnId,
                    com.remitz.common.dto.TransactionStatusUpdateRequest.builder()
                            .status(TransactionStatus.SENT_TO_PAYOUT)
                            .reason("Sending to payout by partner")
                            .build(),
                    userId, com.remitz.common.enums.ActorType.PAYOUT_PARTNER);
        }
        transactionService.updateStatus(txnId,
                com.remitz.common.dto.TransactionStatusUpdateRequest.builder()
                        .status(TransactionStatus.PAID)
                        .reason("Marked as paid by payout partner")
                        .build(),
                userId, com.remitz.common.enums.ActorType.PAYOUT_PARTNER);

        // Create partner ledger CREDIT entry (platform owes partner for the payout)
        java.math.BigDecimal receiveAmount = tx.getReceiveAmount() != null ? tx.getReceiveAmount() : java.math.BigDecimal.ZERO;
        String receiveCurrency = tx.getReceiveCurrency() != null ? tx.getReceiveCurrency() : "GBP";

        // Get settlement rate for currency conversion to USD
        java.math.BigDecimal fxRate = java.math.BigDecimal.ONE;
        try {
            fxRate = settlementRateRepository.findByCurrency(receiveCurrency)
                    .map(r -> r.getRateToUsd()).orElse(java.math.BigDecimal.ONE);
        } catch (Exception e) { /* use default */ }

        java.math.BigDecimal usdAmount = receiveAmount.multiply(fxRate).setScale(4, java.math.RoundingMode.HALF_UP);

        partnerLedgerService.addPartnerEntry(
                partner.getId(), tx.getId(), tx.getReferenceNumber(),
                "CREDIT", receiveAmount, receiveCurrency,
                usdAmount, fxRate,
                "Payout completed for " + tx.getReferenceNumber());

        // Platform ledger DEBIT (cash outflow for payout)
        platformLedgerService.addEntry(
                tx.getId(), tx.getReferenceNumber(),
                "DEBIT", receiveAmount, receiveCurrency,
                usdAmount, fxRate,
                "Payout to partner for " + tx.getReferenceNumber(), "PAYOUT");

        // Final settlement step: PAID → COMPLETED + admin notification
        transactionService.completeTransaction(txnId, userId, com.remitz.common.enums.ActorType.PAYOUT_PARTNER);

        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Transaction marked as PAID and completed")
                .build());
    }

    private PayoutPartner findPartnerForUser(String userUuid, Long userId) {
        return findPartnerForUser(userUuid, userId, null);
    }

    private PayoutPartner findPartnerForUser(String userUuid, Long userId, String userEmail) {
        return findPartnerForUser(userUuid, userId, userEmail, null);
    }

    private PayoutPartner findPartnerForUser(String userUuid, Long userId, String userEmail, Long adminPartnerId) {
        // Admin override: when an admin views the partner portal, X-Partner-Id is sent
        if (adminPartnerId != null) {
            java.util.Optional<PayoutPartner> byAdmin = payoutPartnerRepository.findById(adminPartnerId);
            if (byAdmin.isPresent()) return byAdmin.get();
        }
        // Try by userId first
        if (userId != null) {
            java.util.Optional<PayoutPartner> byId = payoutPartnerRepository.findByUserId(userId);
            if (byId.isPresent()) return byId.get();
        }
        // Try by contact email (gateway sends X-User-Email)
        if (userEmail != null && !userEmail.isBlank()) {
            java.util.Optional<PayoutPartner> byEmail = payoutPartnerRepository.findByContactEmail(userEmail);
            if (byEmail.isPresent()) return byEmail.get();
        }
        // Try to resolve userId from UserRepository via UUID
        if (userUuid != null && !userUuid.isBlank()) {
            try {
                java.util.Optional<UserEntity> userOpt = userRepository.findByUuid(userUuid);
                if (userOpt.isPresent()) {
                    java.util.Optional<PayoutPartner> byResolved = payoutPartnerRepository.findByUserId(userOpt.get().getId());
                    if (byResolved.isPresent()) return byResolved.get();
                }
            } catch (Exception e) {
                log.warn("Failed to resolve user from UUID {}: {}", userUuid, e.getMessage());
            }
        }
        // Strict: never silently fall back to "first partner" — that would let an
        // unmapped caller act on another partner's transactions.
        throw new RemitzException("Payout partner not resolvable for caller", org.springframework.http.HttpStatus.FORBIDDEN);
    }

    private Long extractUserId(Authentication authentication) {
        String principal = authentication.getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            return (long) principal.hashCode();
        }
    }
}

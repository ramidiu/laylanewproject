package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.config.RedisPublisher;
import com.remitz.modules.transaction.entity.*;
import com.remitz.modules.transaction.repository.*;
import com.remitz.modules.transaction.service.LedgerService;
import com.remitz.modules.transaction.entity.PartnerLedger;
import com.remitz.modules.transaction.service.FeeDistributionService;
import com.remitz.modules.transaction.service.PartnerLedgerService;
import com.remitz.modules.transaction.service.PlatformLedgerService;
import com.remitz.modules.transaction.service.TransactionService;
import com.remitz.modules.transaction.service.TransactionStateMachine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/transactions/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Transactions", description = "Admin transaction and ledger management")
@Slf4j
public class AdminTransactionController {

    private final PlatformLedgerService platformLedgerService;
    private final LedgerService ledgerService;
    private final PartnerLedgerService partnerLedgerService;
    private final PayoutPartnerRepository payoutPartnerRepository;
    private final PayinPartnerRepository payinPartnerRepository;
    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final TransactionStateMachine stateMachine;
    private final SettlementRateRepository settlementRateRepository;
    private final CorridorFeeConfigRepository corridorFeeConfigRepository;
    private final RedisPublisher redisPublisher;
    private final TransactionService transactionService;
    private final FeeDistributionService feeDistributionService;

    // ─── Existing endpoints ──────────────────────────────────────────────

    @GetMapping("/platform-ledger")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "Get platform ledger with balances")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPlatformLedger() {
        List<PlatformLedger> entries = platformLedgerService.getAllEntries();
        BigDecimal balance = platformLedgerService.getCurrentBalance();

        Map<String, Object> data = new HashMap<>();
        data.put("entries", entries);
        data.put("balance", balance);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(data)
                .build());
    }

    @GetMapping("/ledger/{transactionId}")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "Get ledger entries for a transaction")
    public ResponseEntity<ApiResponse<List<LedgerEntry>>> getTransactionLedger(
            @PathVariable Long transactionId) {
        List<LedgerEntry> entries = ledgerService.getEntriesByTransactionId(transactionId);
        return ResponseEntity.ok(ApiResponse.<List<LedgerEntry>>builder()
                .success(true)
                .data(entries)
                .build());
    }

    @GetMapping("/partner-balances")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "Get all partner balances")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPartnerBalances() {
        List<PayoutPartner> partners = payoutPartnerRepository.findAll();
        List<Map<String, Object>> balances = partners.stream()
                .map(p -> {
                    List<PartnerLedger> ledger = partnerLedgerService.getPartnerLedger(p.getId());
                    java.math.BigDecimal totalDisbursed = ledger.stream()
                            .filter(e -> "CREDIT".equals(e.getEntryType()))
                            .map(PartnerLedger::getUsdAmount)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    java.math.BigDecimal totalSettled = ledger.stream()
                            .filter(e -> "DEBIT".equals(e.getEntryType()))
                            .map(PartnerLedger::getUsdAmount)
                            .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                    long txnCount = transactionRepository.countByPayoutPartnerId(p.getId());

                    Map<String, Object> map = new HashMap<>();
                    map.put("partnerId", p.getId());
                    map.put("partnerName", p.getPartnerName());
                    map.put("balance", partnerLedgerService.getPartnerBalance(p.getId()));
                    map.put("totalDisbursed", totalDisbursed);
                    map.put("totalSettled", totalSettled);
                    map.put("transactionCount", txnCount);
                    map.put("currency", "USD");
                    return map;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(balances)
                .build());
    }

    // ─── New endpoints ───────────────────────────────────────────────────

    @GetMapping("/all")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "List all transactions with pagination and filters")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllTransactions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        Pageable pageable = PageRequest.of(page, size);

        TransactionStatus txStatus = null;
        if (status != null && !status.isBlank()) {
            try { txStatus = TransactionStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        LocalDateTime start = null;
        if (startDate != null && !startDate.isBlank()) {
            try { start = LocalDateTime.parse(startDate + "T00:00:00"); } catch (Exception ignored) {}
        }
        LocalDateTime end = null;
        if (endDate != null && !endDate.isBlank()) {
            try { end = LocalDateTime.parse(endDate + "T23:59:59"); } catch (Exception ignored) {}
        }
        String searchTerm = (search != null && !search.isBlank()) ? search : null;

        Page<TransactionEntity> txnPage = (txStatus != null || searchTerm != null || start != null || end != null)
                ? transactionRepository.searchTransactions(null, txStatus, null, start, end, searchTerm, pageable)
                : transactionRepository.findAllByOrderByCreatedAtDesc(pageable);

        // Enrich with sender and beneficiary names
        List<Map<String, Object>> enriched = txnPage.getContent().stream().map(tx -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", tx.getId());
            map.put("referenceNumber", tx.getReferenceNumber());
            map.put("senderId", tx.getSenderId());
            map.put("beneficiaryId", tx.getBeneficiaryId());
            map.put("corridorId", tx.getCorridorId());
            map.put("status", tx.getStatus() != null ? tx.getStatus().name() : null);
            map.put("deliveryMethod", tx.getDeliveryMethod() != null ? tx.getDeliveryMethod().name() : null);
            map.put("sendAmount", tx.getSendAmount());
            map.put("sendCurrency", tx.getSendCurrency());
            map.put("receiveAmount", tx.getReceiveAmount());
            map.put("receiveCurrency", tx.getReceiveCurrency());
            map.put("exchangeRate", tx.getExchangeRate());
            map.put("appliedRate", tx.getAppliedRate());
            map.put("feeAmount", tx.getFeeAmount());
            map.put("totalDebitAmount", tx.getTotalDebitAmount());
            map.put("paymentMethodType", tx.getPaymentMethodType());
            map.put("payoutPartnerId", tx.getPayoutPartnerId());
            map.put("payinPartnerId", tx.getPayinPartnerId());
            // STRICT RULE exposure: PAUSED (isActive=false) → partner handles; ACTIVE → admin handles.
            // UI gates its buttons using these flags.
            if (tx.getPayinPartnerId() != null) {
                payinPartnerRepository.findById(tx.getPayinPartnerId()).ifPresent(p -> {
                    map.put("payinPartnerActive", p.getIsActive());
                    map.put("payinPartnerName", p.getPartnerName());
                });
            }
            if (tx.getPayoutPartnerId() != null) {
                payoutPartnerRepository.findById(tx.getPayoutPartnerId()).ifPresent(p -> {
                    map.put("payoutPartnerActive", p.getIsActive());
                    map.put("payoutPartnerName", p.getPartnerName());
                });
            }
            map.put("createdAt", tx.getCreatedAt());
            map.put("updatedAt", tx.getUpdatedAt());
            // Resolve beneficiary name + bank details (so admins can see account / swift / bank)
            if (tx.getBeneficiaryId() != null) {
                beneficiaryRepository.findById(tx.getBeneficiaryId()).ifPresent(b -> {
                    map.put("beneficiaryName", b.getFullName());
                    map.put("beneficiaryPhone", b.getMobileNumber());
                    map.put("beneficiaryCountry", b.getCountry());
                    map.put("beneficiaryCity", b.getBranchCity());
                    map.put("beneficiaryBankName", b.getBankName());
                    map.put("beneficiaryAccountNumber",
                            (b.getAccountNumber() != null && !b.getAccountNumber().isBlank())
                                    ? b.getAccountNumber() : b.getIban());
                    map.put("beneficiaryBranch", b.getBranchState());
                    map.put("beneficiarySwift", b.getSwiftBic());
                });
            }
            // Sender name
            map.put("senderName", tx.getSenderName() != null ? tx.getSenderName() : "Customer #" + tx.getSenderId());
            map.put("senderEmail", tx.getSenderEmail());
            return map;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("content", enriched);
        result.put("totalElements", txnPage.getTotalElements());
        result.put("totalPages", txnPage.getTotalPages());
        result.put("number", txnPage.getNumber());
        result.put("size", txnPage.getSize());

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping("/stats")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "Get transaction statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getTransactionStats() {
        long totalTransactions = transactionRepository.count();
        long paidCount = transactionRepository.countByStatus(TransactionStatus.PAID);
        long completedOnlyCount = transactionRepository.countByStatus(TransactionStatus.COMPLETED);
        // "Completed" for dashboard purposes = PAID or COMPLETED (both are terminal-success states
        // — PAID is the intermediate state, COMPLETED is the final state after auto-advance).
        long completedCount = paidCount + completedOnlyCount;
        long processingCount = transactionRepository.countByStatus(TransactionStatus.PROCESSING);
        long pendingCount = transactionRepository.countByStatus(TransactionStatus.PENDING);
        long failedCount = transactionRepository.countByStatus(TransactionStatus.FAILED);
        long cancelledCount = transactionRepository.countByStatus(TransactionStatus.CANCELLED);
        long complianceHoldCount = transactionRepository.countByStatus(TransactionStatus.COMPLIANCE_HOLD);
        long createdCount = transactionRepository.countByStatus(TransactionStatus.CREATED);
        long fundsReceivedCount = transactionRepository.countByStatus(TransactionStatus.FUNDS_RECEIVED);
        long sentToPayoutCount = transactionRepository.countByStatus(TransactionStatus.SENT_TO_PAYOUT);
        long refundedCount = transactionRepository.countByStatus(TransactionStatus.REFUNDED);
        BigDecimal totalVolume = transactionRepository.sumPaidVolume();
        BigDecimal totalRevenue = transactionRepository.sumPaidRevenue();
        BigDecimal totalFxMargin = transactionRepository.sumPaidFxMargin();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalTransactions", totalTransactions);
        stats.put("completedCount", completedCount);
        stats.put("processingCount", processingCount);
        stats.put("pendingCount", pendingCount);
        stats.put("failedCount", failedCount);
        stats.put("cancelledCount", cancelledCount);
        stats.put("totalVolume", totalVolume);
        stats.put("totalRevenue", totalRevenue);
        stats.put("totalFxMargin", totalFxMargin);
        // Compat aliases the finance dashboard reads
        stats.put("paidVolume", totalVolume);
        stats.put("totalFees", totalRevenue);

        // Keyed breakdown — used by the dashboard chart. Keys match TransactionStatus enum names.
        Map<String, Long> byStatus = new LinkedHashMap<>();
        byStatus.put("CREATED", createdCount);
        byStatus.put("PENDING", pendingCount);
        byStatus.put("COMPLIANCE_HOLD", complianceHoldCount);
        byStatus.put("PROCESSING", processingCount);
        byStatus.put("FUNDS_RECEIVED", fundsReceivedCount);
        byStatus.put("SENT_TO_PAYOUT", sentToPayoutCount);
        byStatus.put("PAID", paidCount);
        byStatus.put("COMPLETED", completedOnlyCount);
        byStatus.put("FAILED", failedCount);
        byStatus.put("CANCELLED", cancelledCount);
        byStatus.put("REFUNDED", refundedCount);
        stats.put("byStatus", byStatus);

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(stats)
                .build());
    }

    @PutMapping("/{id}/release-from-compliance")
    @PreAuthorize("hasAuthority('compliance:view_alerts') or hasAuthority('config:manage_corridors') or hasAuthority('compliance:file_sar')")
    @Operation(summary = "Release a transaction from COMPLIANCE_HOLD back to PENDING")
    @Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> releaseFromCompliance(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        if (tx.getStatus() != TransactionStatus.COMPLIANCE_HOLD) {
            throw new RemitzException(
                    "Transaction is not on COMPLIANCE_HOLD. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String reason = body != null && body.get("reason") != null
                ? body.get("reason")
                : "Compliance cleared";

        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.PENDING,
                0L, ActorType.ADMIN, reason);

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Transaction released from compliance hold")
                .build());
    }

    @PutMapping("/{id}/funds-received")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Mark transaction as funds received")
    @Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> fundsReceived(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // RULE (Pay-In stage, independent): ACTIVE → partner handles, INACTIVE → admin handles.
        if (tx.getPayinPartnerId() != null) {
            PayinPartner p = payinPartnerRepository.findById(tx.getPayinPartnerId()).orElse(null);
            boolean payinPartnerActive = p != null && Boolean.TRUE.equals(p.getIsActive());
            if (payinPartnerActive) {
                throw new RemitzException(
                        "Pay-in partner is ACTIVE and handling this transaction. Admin cannot act.",
                        HttpStatus.FORBIDDEN);
            }
            // Partner is INACTIVE → admin handles (fall through).
        }

        // Validate status
        if (tx.getStatus() != TransactionStatus.PENDING && tx.getStatus() != TransactionStatus.CREATED) {
            throw new RemitzException(
                    "Transaction must be in PENDING or CREATED status to mark funds received. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // Update payment reference if provided
        String paymentReference = body != null ? body.get("paymentReference") : null;
        if (paymentReference != null && !paymentReference.isBlank()) {
            tx.setPaymentReference(paymentReference);
        }

        // If CREATED, first transition to PENDING, then to PROCESSING
        if (tx.getStatus() == TransactionStatus.CREATED) {
            tx = stateMachine.transition(tx, TransactionStatus.PENDING, 0L, ActorType.ADMIN, "Auto-advanced to PENDING by admin");
        }

        // Transition to PROCESSING via state machine
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.PROCESSING, 0L, ActorType.ADMIN, "Funds received by admin");

        // Create basic ledger entries
        // TRANSFER: SENDER -> PLATFORM:HOLDING
        ledgerService.createEntry(updated.getId(), "SENDER:" + updated.getSenderId(), "PLATFORM:HOLDING",
                updated.getSendAmount(), updated.getSendCurrency(), "TRANSFER",
                "Payment received for " + updated.getReferenceNumber());

        // FEE: SENDER -> PLATFORM:REVENUE
        if (updated.getFeeAmount() != null && updated.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.createEntry(updated.getId(), "SENDER:" + updated.getSenderId(), "PLATFORM:REVENUE",
                    updated.getFeeAmount(), updated.getFeeCurrency(), "FEE",
                    "Fee collected for " + updated.getReferenceNumber());
        }

        // Create platform ledger entries
        try {
            BigDecimal settlementRate = getSettlementRate(updated.getSendCurrency());
            BigDecimal sendAmountUsd = convertToUsd(updated.getSendAmount(), settlementRate);

            // CREDIT to HOLDING account
            platformLedgerService.addEntry(updated.getId(), updated.getReferenceNumber(),
                    "CREDIT", updated.getSendAmount(), updated.getSendCurrency(),
                    sendAmountUsd, settlementRate,
                    "Payment received for " + updated.getReferenceNumber(), "HOLDING");

            // Process corridor fee config for fee splitting
            if (updated.getFeeAmount() != null && updated.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
                feeDistributionService.processFeeDistribution(updated, settlementRate);
            }
        } catch (Exception e) {
            log.warn("Failed to create platform/partner ledger entries for transaction {}: {}",
                    updated.getId(), e.getMessage());
        }

        // Publish Redis event
        redisPublisher.publishTransactionEvent("FUNDS_RECEIVED", Map.of(
                "transactionId", updated.getId(),
                "referenceNumber", updated.getReferenceNumber(),
                "senderId", updated.getSenderId(),
                "amount", updated.getSendAmount().toString(),
                "currency", updated.getSendCurrency()
        ));

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Funds received recorded successfully")
                .build());
    }

    @PutMapping("/{id}/mark-paid")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Admin fallback — mark transaction PAID then COMPLETED when no payout partner is configured")
    @Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> adminMarkPaid(@PathVariable Long id) {

        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // RULE (Pay-Out stage, independent): ACTIVE → partner handles, INACTIVE → admin handles.
        if (tx.getPayoutPartnerId() != null) {
            PayoutPartner p = payoutPartnerRepository.findById(tx.getPayoutPartnerId()).orElse(null);
            boolean payoutPartnerActive = p != null && Boolean.TRUE.equals(p.getIsActive());
            if (payoutPartnerActive) {
                throw new RemitzException(
                        "Payout partner is ACTIVE and handling this transaction. Admin cannot act.",
                        HttpStatus.FORBIDDEN);
            }
            // Partner is INACTIVE → admin handles (fall through).
        }

        if (tx.getStatus() != TransactionStatus.PROCESSING
                && tx.getStatus() != TransactionStatus.FUNDS_RECEIVED
                && tx.getStatus() != TransactionStatus.SENT_TO_PAYOUT) {
            throw new RemitzException(
                    "Transaction must be in PROCESSING, FUNDS_RECEIVED, or SENT_TO_PAYOUT status to mark paid. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // PROCESSING → PAID → COMPLETED (same terminal state the payout-partner flow produces).
        TransactionEntity paid = stateMachine.transition(tx, TransactionStatus.PAID, 0L, ActorType.ADMIN, "Marked PAID by admin");

        // Platform ledger DEBIT — cash outflow for the payout (equivalent to partner path).
        try {
            BigDecimal receiveAmount = paid.getReceiveAmount() != null ? paid.getReceiveAmount() : BigDecimal.ZERO;
            String receiveCurrency = paid.getReceiveCurrency() != null ? paid.getReceiveCurrency() : "GBP";
            BigDecimal fxRate = settlementRateRepository.findByCurrency(receiveCurrency)
                    .map(r -> r.getRateToUsd()).orElse(BigDecimal.ONE);
            BigDecimal usdAmount = receiveAmount.multiply(fxRate).setScale(4, RoundingMode.HALF_UP);

            platformLedgerService.addEntry(paid.getId(), paid.getReferenceNumber(),
                    "DEBIT", receiveAmount, receiveCurrency,
                    usdAmount, fxRate,
                    "Payout by admin for " + paid.getReferenceNumber(), "PAYOUT");
        } catch (Exception e) {
            log.warn("Failed to create platform ledger DEBIT for admin mark-paid tx {}: {}", paid.getId(), e.getMessage());
        }

        // Final settlement step: PAID → COMPLETED + admin notification event
        transactionService.completeTransaction(id, 0L, ActorType.ADMIN);
        TransactionEntity completed = transactionRepository.findById(id).orElse(paid);

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(completed)
                .message("Transaction marked as PAID and completed")
                .build());
    }

    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Cancel a transaction (admin)")
    @Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> cancelTransaction(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Validate status
        if (tx.getStatus() != TransactionStatus.PENDING
                && tx.getStatus() != TransactionStatus.CREATED
                && tx.getStatus() != TransactionStatus.COMPLIANCE_HOLD) {
            throw new RemitzException(
                    "Transaction must be in PENDING, CREATED, or COMPLIANCE_HOLD status to cancel. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String reason = body != null ? body.get("reason") : null;
        String cancelReason = reason != null && !reason.isBlank() ? reason : "Cancelled by admin";

        // Transition to CANCELLED
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.CANCELLED, 0L, ActorType.ADMIN, cancelReason);

        // If there are existing ledger entries, create REVERSAL entries
        List<LedgerEntry> existingEntries = ledgerService.getEntriesByTransactionId(updated.getId());
        if (!existingEntries.isEmpty()) {
            for (LedgerEntry entry : existingEntries) {
                if (!"REVERSAL".equals(entry.getEntryType())) {
                    ledgerService.createEntry(updated.getId(), entry.getCreditAccount(), entry.getDebitAccount(),
                            entry.getAmount(), entry.getCurrency(), "REVERSAL",
                            "Reversal of " + entry.getEntryType() + " for cancelled transaction " + updated.getReferenceNumber());
                }
            }
        }

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Transaction cancelled successfully")
                .build());
    }

    @PutMapping("/{id}/refund")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Refund a transaction (admin)")
    @Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> refundTransaction(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {

        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Validate status - funds were received but not yet paid out
        if (tx.getStatus() != TransactionStatus.PROCESSING
                && tx.getStatus() != TransactionStatus.FUNDS_RECEIVED) {
            throw new RemitzException(
                    "Transaction must be in PROCESSING or FUNDS_RECEIVED status to refund. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        String reason = body != null ? body.get("reason") : null;
        String refundReason = reason != null && !reason.isBlank() ? reason : "Refund initiated by admin";

        // Transition to REFUNDED
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.REFUNDED, 0L, ActorType.ADMIN, refundReason);

        // Create REVERSAL ledger entries: PLATFORM:HOLDING -> SENDER
        ledgerService.createEntry(updated.getId(), "PLATFORM:HOLDING", "SENDER:" + updated.getSenderId(),
                updated.getSendAmount(), updated.getSendCurrency(), "REVERSAL",
                "Refund for transaction " + updated.getReferenceNumber());

        // Create platform ledger DEBIT entry for refund
        try {
            BigDecimal settlementRate = getSettlementRate(updated.getSendCurrency());
            BigDecimal sendAmountUsd = convertToUsd(updated.getSendAmount(), settlementRate);

            platformLedgerService.addEntry(updated.getId(), updated.getReferenceNumber(),
                    "DEBIT", updated.getSendAmount(), updated.getSendCurrency(),
                    sendAmountUsd, settlementRate,
                    "Refund for " + updated.getReferenceNumber(), "HOLDING");
        } catch (Exception e) {
            log.warn("Failed to create platform ledger debit for refund of transaction {}: {}",
                    updated.getId(), e.getMessage());
        }

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Transaction refunded successfully")
                .build());
    }

    @PutMapping("/{id}/archive")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Archive a transaction with no payment received")
    @Transactional
    public ResponseEntity<ApiResponse<TransactionEntity>> archiveTransaction(
            @PathVariable Long id) {

        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Validate: must be PENDING and no payment received
        if (tx.getStatus() != TransactionStatus.PENDING) {
            throw new RemitzException(
                    "Transaction must be in PENDING status to archive. Current: " + tx.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        // Set failure reason before transition
        tx.setFailureReason("Archived: no payment received");

        // Transition to CANCELLED
        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.CANCELLED, 0L, ActorType.ADMIN, "Archived: no payment received");

        return ResponseEntity.ok(ApiResponse.<TransactionEntity>builder()
                .success(true)
                .data(updated)
                .message("Transaction archived successfully")
                .build());
    }

    // ─── Private helpers ─────────────────────────────────────────────────

    private BigDecimal getSettlementRate(String currency) {
        if ("USD".equalsIgnoreCase(currency)) {
            return BigDecimal.ONE;
        }
        return settlementRateRepository.findByCurrency(currency)
                .map(SettlementRate::getRateToUsd)
                .orElseThrow(() -> new RemitzException(
                        "Settlement rate not found for currency: " + currency, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    private BigDecimal convertToUsd(BigDecimal amount, BigDecimal rateToUsd) {
        if (rateToUsd.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return amount.divide(rateToUsd, 4, RoundingMode.HALF_UP);
    }

    private void processFeeDistribution(TransactionEntity tx, BigDecimal settlementRate) {
        BigDecimal feeAmount = tx.getFeeAmount();
        BigDecimal feeAmountUsd = convertToUsd(feeAmount, settlementRate);

        List<CorridorFeeConfig> feeConfigs = corridorFeeConfigRepository
                .findByFromCurrencyAndToCurrency(tx.getSendCurrency(), tx.getReceiveCurrency());

        CorridorFeeConfig feeConfig = feeConfigs.stream()
                .filter(fc -> Boolean.TRUE.equals(fc.getIsActive()))
                .findFirst()
                .orElse(null);

        if (feeConfig == null) {
            // No corridor fee config - entire fee goes to admin/platform revenue
            platformLedgerService.addEntry(tx.getId(), tx.getReferenceNumber(),
                    "CREDIT", feeAmount, tx.getFeeCurrency(),
                    feeAmountUsd, settlementRate,
                    "Admin fee for " + tx.getReferenceNumber(), "REVENUE");
            return;
        }

        // Calculate payin share
        BigDecimal payinShare = BigDecimal.ZERO;
        if (feeConfig.getPayinShareValue() != null && feeConfig.getPayinShareValue().compareTo(BigDecimal.ZERO) > 0) {
            if ("PERCENTAGE".equalsIgnoreCase(feeConfig.getPayinShareType())) {
                payinShare = feeAmount.multiply(feeConfig.getPayinShareValue())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            } else {
                // FLAT
                payinShare = feeConfig.getPayinShareValue();
            }
        }

        // Calculate payout share
        BigDecimal payoutShare = BigDecimal.ZERO;
        if (feeConfig.getPayoutShareValue() != null && feeConfig.getPayoutShareValue().compareTo(BigDecimal.ZERO) > 0) {
            if ("PERCENTAGE".equalsIgnoreCase(feeConfig.getPayoutShareType())) {
                payoutShare = feeAmount.multiply(feeConfig.getPayoutShareValue())
                        .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            } else {
                // FLAT
                payoutShare = feeConfig.getPayoutShareValue();
            }
        }

        // Admin share = fee - payinShare - payoutShare
        BigDecimal adminShare = feeAmount.subtract(payinShare).subtract(payoutShare);
        BigDecimal adminShareUsd = convertToUsd(adminShare, settlementRate);

        // DEBIT entry for admin fee share to REVENUE
        if (adminShare.compareTo(BigDecimal.ZERO) > 0) {
            platformLedgerService.addEntry(tx.getId(), tx.getReferenceNumber(),
                    "CREDIT", adminShare, tx.getFeeCurrency(),
                    adminShareUsd, settlementRate,
                    "Admin fee share for " + tx.getReferenceNumber(), "REVENUE");
        }

        // If payinPartnerId exists: create PayinPartnerLedger CREDIT (collected) and DEBIT (commission)
        if (feeConfig.getPayinPartnerId() != null && payinShare.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payinShareUsd = convertToUsd(payinShare, settlementRate);

            // CREDIT: amount collected by payin partner
            partnerLedgerService.addPayinPartnerEntry(
                    feeConfig.getPayinPartnerId(), tx.getId(), tx.getReferenceNumber(),
                    "CREDIT", tx.getSendAmount(), tx.getSendCurrency(),
                    convertToUsd(tx.getSendAmount(), settlementRate), settlementRate,
                    "Payment collected for " + tx.getReferenceNumber());

            // DEBIT: commission earned by payin partner
            partnerLedgerService.addPayinPartnerEntry(
                    feeConfig.getPayinPartnerId(), tx.getId(), tx.getReferenceNumber(),
                    "DEBIT", payinShare, tx.getFeeCurrency(),
                    payinShareUsd, settlementRate,
                    "Payin commission for " + tx.getReferenceNumber());
        }

        // Payout partner ledger entries are NOT created here - they are created when the partner marks PAID
    }
}

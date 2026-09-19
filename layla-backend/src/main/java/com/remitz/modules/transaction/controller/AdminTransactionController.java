package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.auth.repository.UserRepository;
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
    private final UserRepository userRepository;
    private final com.remitz.modules.payout.usi.repository.UsiTransactionRepository usiTransactionRepository;
    private final com.remitz.modules.payin.customer.repository.PayinCustomerRepository payinCustomerRepository;

    /**
     * Resolve the sender's display name. Prefers the real first+last name from
     * the users table (so imported transactions that stored an email local-part
     * like "abdulmohammed73" or a null name still show the full name), and only
     * falls back to the stored name / "Customer #id".
     */
    private String resolveSenderName(Long senderId, String storedName, String senderEmail) {
        if (senderId != null) {
            String full = userRepository.findById(senderId).map(u -> {
                String fn = u.getFirstName() != null ? u.getFirstName().trim() : "";
                String ln = u.getLastName() != null ? u.getLastName().trim() : "";
                return (fn + " " + ln).trim();
            }).orElse("");
            if (full != null && !full.isBlank()) return full;
        }
        if (storedName != null && !storedName.isBlank()) return storedName;
        if (senderEmail != null && !senderEmail.isBlank()) return senderEmail;
        return senderId != null ? "Customer #" + senderId : "—";
    }

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
                : transactionRepository.findByStatusNotOrderByCreatedAtDesc(TransactionStatus.ARCHIVED, pageable);

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
                    // The recipient's actual city — was wrongly reading branch_city ("Any Branch").
                    map.put("beneficiaryCity",
                            (b.getCity() != null && !b.getCity().isBlank()) ? b.getCity() : b.getBranchCity());
                    map.put("beneficiaryBankName", b.getBankName());
                    map.put("beneficiaryAccountNumber",
                            (b.getAccountNumber() != null && !b.getAccountNumber().isBlank())
                                    ? b.getAccountNumber() : b.getIban());
                    map.put("beneficiaryBranch", b.getBranchState());
                    map.put("beneficiarySwift", b.getSwiftBic());
                });
            }
            // Sender name — prefer real full name from users table (imported txns
            // may have stored an email local-part or null).
            map.put("senderName", resolveSenderName(tx.getSenderId(), tx.getSenderName(), tx.getSenderEmail()));
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

    /**
     * How many PAID USI transactions exist — the admin UI uses this to only show the
     * "USI CSV" download button when there is at least one paid USI payout to export.
     */
    @GetMapping("/usi-csv/count")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "Count of PAID USI transactions available for CSV export")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getUsiCsvCount() {
        long count = transactionRepository.countPaidTransactions();
        Map<String, Object> data = new HashMap<>();
        data.put("count", count);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(data)
                .build());
    }

    /**
     * USI CSV export — the report USI Money requires. Includes ONLY transactions that were
     * pushed to USI (have a usi_transactions row) AND are PAID (money delivered to the
     * beneficiary — PAID or its post-completion COMPLETED state); it never includes non-USI
     * or unpaid transactions. Columns match the USI-supplied template exactly. An optional
     * startDate/endDate (yyyy-MM-dd) narrows the export to the admin's selected date range.
     */
    @GetMapping("/usi-csv")
    @PreAuthorize("hasPermission(null, 'ledger:view')")
    @Operation(summary = "Download ALL PAID transactions as CSV in the USI-required column format")
    public ResponseEntity<byte[]> downloadUsiCsv(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        LocalDateTime start = null;
        if (startDate != null && !startDate.isBlank()) {
            try { start = LocalDateTime.parse(startDate + "T00:00:00"); } catch (Exception ignored) {}
        }
        LocalDateTime end = null;
        if (endDate != null && !endDate.isBlank()) {
            try { end = LocalDateTime.parse(endDate + "T23:59:59"); } catch (Exception ignored) {}
        }

        List<TransactionEntity> txns = transactionRepository.findPaidTransactionsForExport(start, end);

        java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

        // Header row — exactly the columns in the USI-supplied Excel template.
        String[] headers = {
                "Date of transaction (internal)", "Trans No. (internal)", "Remitter's Internal ID No",
                "Remitter's Full Name", "Remitter's DoB", "Remitter's Address in UK", "Amount (GBP)",
                "Payment Method", "Purpose of Transaction", "Source of Income", "Beneficiary Name",
                "Destination", "Transfer Type", "Receiving Amount", "Currency", "Status"
        };

        StringBuilder sb = new StringBuilder();
        // BOM so Excel opens UTF-8 correctly.
        sb.append('\uFEFF');
        sb.append(String.join(",", java.util.Arrays.stream(headers).map(this::csv).toList())).append("\r\n");

        for (TransactionEntity tx : txns) {
            var user = tx.getSenderId() != null ? userRepository.findById(tx.getSenderId()).orElse(null) : null;
            var bnf = tx.getBeneficiaryId() != null ? beneficiaryRepository.findById(tx.getBeneficiaryId()).orElse(null) : null;
            var usi = usiTransactionRepository.findByTransactionId(tx.getReferenceNumber()).orElse(null);

            String remitterName = resolveSenderName(tx.getSenderId(), tx.getSenderName(), tx.getSenderEmail());
            // Remitter DoB: prefer the users table; fall back to the pay-in customer record
            // (matched by uuid = payin customerId) for transactions created via the pay-in flow,
            // whose DoB is only captured on payin_customers.
            java.time.LocalDate dobDate = (user != null) ? user.getDateOfBirth() : null;
            if (dobDate == null && user != null && user.getUuid() != null) {
                dobDate = payinCustomerRepository.findByCustomerId(user.getUuid())
                        .map(com.remitz.modules.payin.customer.entity.PayinCustomerEntity::getDob)
                        .orElse(null);
            }
            String dob = dobDate != null ? dobDate.format(dateFmt) : "";
            String address = user != null ? ukAddress(user.getAddressLine1(), user.getAddressLine2(), user.getCity(), user.getPostcode()) : "";
            String beneficiaryName = bnf != null ? bnf.getFullName() : (usi != null ? usi.getBeneficiaryName() : "");
            String destination = bnf != null ? bnf.getCountry() : (usi != null ? usi.getDestinationCountry() : "");
            // Prefer the exact trans_type recorded by USI; fall back to our delivery method.
            String transferType = (usi != null && usi.getTransType() != null && !usi.getTransType().isBlank())
                    ? usi.getTransType()
                    : (tx.getDeliveryMethod() != null ? tx.getDeliveryMethod().name() : "");

            String[] row = {
                    tx.getCreatedAt() != null ? tx.getCreatedAt().format(dateFmt) : "",
                    tx.getReferenceNumber(),
                    tx.getSenderId() != null ? String.valueOf(tx.getSenderId()) : "",
                    remitterName,
                    dob,
                    address,
                    tx.getSendAmount() != null ? tx.getSendAmount().toPlainString() : "",
                    paymentMethodLabel(tx.getPaymentMethodType() != null ? tx.getPaymentMethodType().name() : null),
                    // Declared by the remitter on the send-money form. Historical rows (captured before
                    // these fields existed) fall back to the previous constant defaults.
                    (tx.getPurposeOfTransaction() != null && !tx.getPurposeOfTransaction().isBlank())
                            ? tx.getPurposeOfTransaction() : "Family Maintenance",
                    (tx.getSourceOfIncome() != null && !tx.getSourceOfIncome().isBlank())
                            ? tx.getSourceOfIncome() : "Salary",
                    beneficiaryName,
                    destination,
                    transferType,
                    tx.getReceiveAmount() != null ? tx.getReceiveAmount().toPlainString() : "",
                    tx.getReceiveCurrency(),
                    tx.getStatus() != null ? tx.getStatus().name() : ""
            };
            sb.append(String.join(",", java.util.Arrays.stream(row).map(this::csv).toList())).append("\r\n");
        }

        byte[] bytes = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"paid-transactions.csv\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(bytes);
    }

    /** RFC-4180 CSV field escaping: wrap in quotes when the value has a comma/quote/newline. */
    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /** Human-readable pay-in method for the USI report (mirrors the admin transactions list). */
    private String paymentMethodLabel(String type) {
        if (type == null) return "";
        return switch (type) {
            case "CARD" -> "Card";
            case "OPEN_BANKING", "BANK_TRANSFER" -> "Bank Transfer";
            case "AGENT_CASH" -> "Cash";
            case "WALLET" -> "Wallet";
            default -> type;
        };
    }

    /** Compose the remitter's UK address from its parts, skipping blanks. */
    private String ukAddress(String line1, String line2, String city, String postcode) {
        List<String> parts = new ArrayList<>();
        for (String p : new String[]{line1, line2, city, postcode}) {
            if (p != null && !p.isBlank()) parts.add(p.trim());
        }
        return String.join(", ", parts);
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

        // Platform ledger — value the payout in GBP as the principal actually sent (send - fee);
        // the fee stays behind as profit. Also ensures the matching "money in" credit exists.
        try {
            platformLedgerService.recordPayout(paid.getId(), paid.getReferenceNumber(),
                    paid.getSendAmount(), paid.getFeeAmount());
        } catch (Exception e) {
            log.warn("Failed to create platform ledger payout for admin mark-paid tx {}: {}", paid.getId(), e.getMessage());
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

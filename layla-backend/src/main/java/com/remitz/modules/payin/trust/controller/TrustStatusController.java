package com.remitz.modules.payin.trust.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.payin.trust.service.TrustStatusService;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.service.TransactionStateMachine;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * READ-ONLY Trust Payments status checker.
 *
 * <p>Deliberately separate from {@link TrustPaymentController} so the live STPP hosted-page payment
 * flow and the webhook/callback handler are left completely untouched. Performs a TRANSACTIONQUERY via
 * {@link TrustStatusService} and returns the latest transaction details. Admin-guarded; the Trust
 * Webservices credentials never leave the server.
 */
@RestController
@RequestMapping("/api/trust-payment")
@RequiredArgsConstructor
@Slf4j
public class TrustStatusController {

    private final TrustStatusService trustStatusService;
    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine;

    @PostMapping("/status-check")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Read-only: query Trust Payments (TRANSACTIONQUERY) for the latest status of a transaction by orderreference")
    public ResponseEntity<ApiResponse<Map<String, Object>>> statusCheck(@RequestBody Map<String, String> body) {
        String orderReference = body != null ? body.get("orderreference") : null;
        log.info("Trust status-check requested for orderreference={}", orderReference);
        Map<String, Object> result = trustStatusService.queryByOrderReference(orderReference);
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .message("Trust status retrieved")
                .build());
    }

    /**
     * On-demand settlement status check for one transaction.
     *
     * <p>Accepts (in order of precedence) an internal {@code transactionId}, a {@code referenceNumber},
     * or a raw Trust {@code transactionReference}. Loads the transaction, reads its saved Trust
     * transactionreference ({@link TransactionEntity#getPaymentReference()}), runs a TRANSACTIONQUERY,
     * and reports the settlement state. When Trust reports the payment as SETTLED and the transaction
     * is still at PROCESSING, it is advanced to FUNDS_RECEIVED via the shared state machine — the same
     * transition the live callback performs — so this is a safe, on-demand reconciliation. No other
     * status is ever changed here, and the existing payment/callback logic is untouched.
     */
    @PostMapping("/settlement-status")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Transactional
    @Operation(summary = "Read-only + reconcile: check Trust settlement status by internal transaction id / referenceNumber / transactionReference")
    public ResponseEntity<ApiResponse<Map<String, Object>>> settlementStatus(@RequestBody(required = false) Map<String, String> body) {
        String transactionIdRaw   = value(body, "transactionId");
        String referenceNumber    = value(body, "referenceNumber");
        String transactionRefIn   = value(body, "transactionReference");

        // 1) Resolve the internal transaction (optional if only a raw transactionReference is supplied).
        TransactionEntity tx = resolveTransaction(transactionIdRaw, referenceNumber, transactionRefIn);

        // 2) Determine the Trust transactionreference to query: the one saved on the txn, else the input.
        String transactionRef = (tx != null && tx.getPaymentReference() != null && !tx.getPaymentReference().isBlank())
                ? tx.getPaymentReference()
                : transactionRefIn;

        if (transactionRef == null || transactionRef.isBlank()) {
            log.info("Trust settlement-status: no transactionreference available (txnId={}, ref={})", transactionIdRaw, referenceNumber);
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .data(buildResponse(null, tx, "NOT_FOUND", null, null, false))
                    .message("No Trust Payments transactionreference is saved for this transaction yet")
                    .build());
        }

        log.info("Trust settlement-status requested for transactionreference={} (txnId={})", transactionRef, transactionIdRaw);

        // 3) Query Trust (TRANSACTIONQUERY). Any API/query failure returns a safe error response.
        TrustStatusService.SettlementView view;
        try {
            view = trustStatusService.queryByTransactionReference(transactionRef);
        } catch (RemitzException e) {
            log.warn("Trust settlement-status query failed for txnref={}: {}", transactionRef, e.getMessage());
            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                    .success(false)
                    .data(buildResponse(transactionRef, tx, "ERROR", null, null, false))
                    .message(e.getMessage())
                    .build());
        }

        // 4) Amount verification: the charged baseamount from Trust MUST equal our expected total debit.
        boolean amountVerified = false;
        if (tx != null && "SETTLED".equals(view.settlementState())) {
            BigDecimal expected = tx.getTotalDebitAmount() != null ? tx.getTotalDebitAmount() : tx.getSendAmount();
            BigDecimal charged = TrustStatusService.baseAmountToMajor(view.baseAmount());
            amountVerified = charged != null && expected != null && charged.compareTo(expected) == 0;

            // Reconcile ONLY when settled AND amount-verified AND we own a still-PROCESSING transaction.
            if (amountVerified && tx.getStatus() == TransactionStatus.PROCESSING) {
                tx = stateMachine.transition(tx, TransactionStatus.FUNDS_RECEIVED, 0L, ActorType.SYSTEM,
                        "Trust settlement confirmed via TRANSACTIONQUERY (settlestatus=" + view.settleStatus()
                                + ", amount verified " + charged + ")");
                log.info("Trust settlement-status: advanced {} to FUNDS_RECEIVED (amount verified {})",
                        tx.getReferenceNumber(), charged);
            } else if (!amountVerified) {
                log.warn("Trust settlement-status: {} SETTLED but amount NOT verified (charged {} vs expected {}) — not reconciling",
                        tx.getReferenceNumber(), charged, expected);
            }
        }

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(buildResponse(view.transactionReference(), tx, view.settlementState(),
                        view.settleStatus(), view.settlementDueDate(), amountVerified))
                .message("NOT_FOUND".equals(view.settlementState())
                        ? "Transaction not found at Trust Payments" : "Trust settlement status retrieved")
                .build());
    }

    /** Resolve the internal transaction by id, referenceNumber, or saved Trust transactionReference. */
    private TransactionEntity resolveTransaction(String transactionIdRaw, String referenceNumber, String transactionRefIn) {
        if (transactionIdRaw != null && !transactionIdRaw.isBlank()) {
            final long id;
            try {
                id = Long.parseLong(transactionIdRaw.trim());
            } catch (NumberFormatException nfe) {
                throw new RemitzException("transactionId must be a number", HttpStatus.BAD_REQUEST);
            }
            return transactionRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
        }
        if (referenceNumber != null && !referenceNumber.isBlank()) {
            return transactionRepository.findByReferenceNumber(referenceNumber.trim())
                    .orElseThrow(() -> new ResourceNotFoundException("Transaction", "referenceNumber", referenceNumber.trim()));
        }
        if (transactionRefIn != null && !transactionRefIn.isBlank()) {
            // Best-effort link to a local txn; absence is not an error (raw-reference lookups are allowed).
            return transactionRepository.findByPaymentReference(transactionRefIn.trim()).stream().findFirst().orElse(null);
        }
        throw new RemitzException("Provide one of: transactionId, referenceNumber, or transactionReference",
                HttpStatus.BAD_REQUEST);
    }

    /** Assemble the response body with exactly the fields the client needs (no secrets). */
    private Map<String, Object> buildResponse(String transactionReference, TransactionEntity tx,
                                              String settlementState, String settleStatus, String settlementDueDate,
                                              boolean amountVerified) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("transactionReference", transactionReference);
        out.put("settlementStatus", settleStatus);        // raw Trust settlestatus (e.g. "100")
        out.put("settlementState", settlementState);       // mapped (SETTLED / SCHEDULED / FAILED / UNKNOWN / NOT_FOUND / ERROR)
        out.put("transactionStatus", tx != null ? tx.getStatus().name() : null);
        out.put("settlementDueDate", settlementDueDate);
        out.put("amountVerified", amountVerified);         // true only when Trust baseamount == our total debit
        return out;
    }

    private static String value(Map<String, String> body, String key) {
        return body == null ? null : body.get(key);
    }
}

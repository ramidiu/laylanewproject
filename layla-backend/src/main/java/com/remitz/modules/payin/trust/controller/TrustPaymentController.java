package com.remitz.modules.payin.trust.controller;

import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.modules.payin.trust.entity.TrustPaymentEntity;
import com.remitz.modules.payin.trust.repository.TrustPaymentRepository;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.service.TransactionStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/trust-payment")
@RequiredArgsConstructor
@Slf4j
public class TrustPaymentController {

    private final TrustPaymentRepository trustPaymentRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionStateMachine stateMachine;

    /**
     * Called by the frontend after Trust Payments redirects back (success or failure).
     * Saves the payment record and updates the transaction status accordingly.
     */
    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<Map<String, Object>> confirmCardPayment(@RequestBody Map<String, String> params) {
        String errorCode       = params.getOrDefault("errorcode", "");
        String orderRef        = params.get("orderreference");
        String txRef           = params.get("transactionreference");
        String referenceNumber = params.get("referenceNumber"); // our internal transaction ref (frontend-injected)

        log.info("Trust card confirm: errorcode={} orderRef={} txRef={} ref={}",
                "0".equals(errorCode), orderRef, txRef, referenceNumber);

        saveTrustRecord(referenceNumber, orderRef, txRef, params);

        PayOutcome outcome = applyCardPaymentResult("confirm", referenceNumber, txRef, errorCode, params.get("baseamount"));

        return ResponseEntity.ok(Map.of(
                "success", outcome.accepted(),
                "pending", !outcome.hasResult(),
                "referenceNumber", referenceNumber != null ? referenceNumber : ""
        ));
    }

    /**
     * Server-to-server URL notification (webhook) from Trust Payments. Unlike the browser
     * /confirm, this does not depend on the customer's browser completing the redirect.
     *
     * The notification does not carry our internal referenceNumber, so we resolve it from the
     * orderreference (once the payment form starts sending orderreference=<TXN ref>), falling
     * back to a row the browser /confirm already linked for this transactionreference.
     */
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Void> handleCallback(@RequestBody Map<String, String> params) {
        String errorCode = params.getOrDefault("errorcode", "");
        String orderRef  = params.get("orderreference");
        String txRef     = params.get("transactionreference");

        log.info("Trust payment callback (webhook): errorcode={} orderRef={} txRef={}", errorCode, orderRef, txRef);

        saveTrustRecord(params.get("transactionref"), orderRef, txRef, params);

        String internalRef = resolveInternalRef(orderRef, txRef);
        if (internalRef == null) {
            log.warn("Trust callback: could not resolve internal transaction for orderRef={} txRef={} — recorded only", orderRef, txRef);
            return ResponseEntity.ok().build();
        }

        applyCardPaymentResult("callback", internalRef, txRef, errorCode, params.get("baseamount"));
        return ResponseEntity.ok().build();
    }

    // ── shared logic ──────────────────────────────────────────────────────

    /** Result of applying a card payment to a transaction. */
    private record PayOutcome(boolean accepted, boolean hasResult) {}

    /**
     * Single source of truth for turning a Trust Payments result into a transaction status change,
     * used by BOTH the browser /confirm and the server-to-server /callback. Applies the same guards:
     *   0) no errorcode      -> don't guess (leave status untouched)
     *   1) reference reuse   -> a transactionreference that already funded another txn is rejected
     *   2) amount mismatch   -> when baseamount is sent, it must equal the expected debit
     */
    private PayOutcome applyCardPaymentResult(String source, String referenceNumber, String txRef,
                                              String errorCode, String baseamount) {
        if (referenceNumber == null || referenceNumber.isBlank()) {
            return new PayOutcome(false, errorCode != null && !errorCode.isBlank());
        }
        Optional<TransactionEntity> txOpt = transactionRepository.findByReferenceNumber(referenceNumber);
        if (txOpt.isEmpty()) {
            log.warn("Trust {} : transaction not found for ref={}", source, referenceNumber);
            return new PayOutcome(false, errorCode != null && !errorCode.isBlank());
        }
        TransactionEntity tx = txOpt.get();

        // GUARD 0 — no definite result: if Trust did not send an errorcode (lost/empty redirect,
        // browser closed, partial webhook) we must NOT guess. Cancelling could kill a paid txn;
        // funding is impossible without a success code. Leave status, let reconciliation settle it.
        if (errorCode == null || errorCode.isBlank()) {
            log.warn("Trust {} : no errorcode for ref={} — leaving status {} unchanged (needs reconciliation)",
                    source, referenceNumber, tx.getStatus());
            return new PayOutcome(false, false);
        }

        boolean isSuccess    = "0".equals(errorCode);
        String declineReason = isSuccess ? null : ("Card payment declined by Trust Payments. Error code: " + errorCode);

        try {
            // GUARD 1 — reference reuse: never fund a transaction off a Trust transactionreference
            // that already funded a DIFFERENT transaction (observed: £8 ref 57-70-147105728 wrongly
            // funded a £255 txn). A declined card can otherwise be funded by replaying a success.
            if (isSuccess && txRef != null && !txRef.isBlank()) {
                boolean referenceReused = transactionRepository.findByPaymentReference(txRef).stream()
                        .anyMatch(other -> !referenceNumber.equals(other.getReferenceNumber()));
                if (referenceReused) {
                    isSuccess = false;
                    declineReason = "Card payment not accepted: Trust reference " + txRef
                            + " already used by another transaction (duplicate/declined card payment).";
                    log.error("Trust {} REJECTED for ref={}: transactionreference {} already used by another transaction",
                            source, referenceNumber, txRef);
                }
            }

            // GUARD 2 — amount match: when Trust sends the charged amount it must equal the
            // transaction's expected debit. (Best-effort: skipped when baseamount is absent.)
            if (isSuccess) {
                if (baseamount != null && !baseamount.isBlank()) {
                    try {
                        BigDecimal charged = new BigDecimal(baseamount).movePointLeft(2);
                        BigDecimal expected = tx.getTotalDebitAmount() != null
                                ? tx.getTotalDebitAmount() : tx.getSendAmount();
                        if (expected != null && charged.compareTo(expected) != 0) {
                            isSuccess = false;
                            declineReason = "Card payment amount mismatch: charged " + charged
                                    + " but transaction expects " + expected + ".";
                            log.error("Trust {} REJECTED for ref={}: charged {} != expected {}",
                                    source, referenceNumber, charged, expected);
                        }
                    } catch (NumberFormatException nfe) {
                        log.warn("Trust {} : unparseable baseamount '{}' for ref={}", source, baseamount, referenceNumber);
                    }
                } else {
                    log.warn("Trust {} : no baseamount for ref={} — amount not verified against the charge", source, referenceNumber);
                }
            }

            if (isSuccess) {
                // Accept PENDING or COMPLIANCE_HOLD — card funds are confirmed either way.
                if (tx.getStatus() == TransactionStatus.PENDING
                        || tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                    tx.setPaymentReference(txRef);
                    if (tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                        tx = stateMachine.transition(tx, TransactionStatus.PENDING,
                                0L, ActorType.SYSTEM, "Card payment received — releasing compliance hold for manual review");
                    }
                    tx = stateMachine.transition(tx, TransactionStatus.PROCESSING,
                            0L, ActorType.SYSTEM, "Card payment processing via Trust Payments (" + source + ")");
                    tx = stateMachine.transition(tx, TransactionStatus.FUNDS_RECEIVED,
                            0L, ActorType.SYSTEM, "Card funds confirmed. Trust ref: " + txRef);
                }
                // else: already funded / advanced — idempotent no-op, still "accepted".
            } else {
                // Declined or guard-rejected — do NOT fund; cancel if still awaiting payment.
                if (tx.getStatus() == TransactionStatus.PENDING
                        || tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                    String reason = declineReason != null ? declineReason
                            : ("Card payment declined by Trust Payments. Error code: " + errorCode);
                    tx.setFailureReason(reason);
                    tx = stateMachine.transition(tx, TransactionStatus.CANCELLED,
                            0L, ActorType.SYSTEM, reason);
                }
            }
            log.info("Trust {} : transaction {} -> {}", source, referenceNumber, tx.getStatus());
        } catch (Exception e) {
            log.error("Trust {} : failed to update transaction status for ref={}: {}", source, referenceNumber, e.getMessage(), e);
        }
        return new PayOutcome(isSuccess, true);
    }

    /** Resolve OUR internal transaction ref for a webhook that lacks it. */
    private String resolveInternalRef(String orderRef, String txRef) {
        // 1) orderreference is our transaction ref once the payment form sends it.
        if (orderRef != null && !orderRef.isBlank()
                && transactionRepository.findByReferenceNumber(orderRef).isPresent()) {
            return orderRef;
        }
        // 2) Fallback: a row the browser /confirm already linked for this transactionreference.
        if (txRef != null && !txRef.isBlank()) {
            return trustPaymentRepository.findByTransactionReference(txRef).stream()
                    .map(TrustPaymentEntity::getTransactionRef)
                    .filter(r -> r != null && !r.isBlank())
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    /** Persist the raw attempt for audit (never throws into the request flow). */
    private void saveTrustRecord(String ourRef, String orderRef, String txRef, Map<String, String> params) {
        try {
            BigDecimal amount = null;
            String baseamount = params.get("baseamount");
            if (baseamount != null && !baseamount.isBlank()) {
                try { amount = new BigDecimal(baseamount).movePointLeft(2); } catch (NumberFormatException ignored) {}
            }
            TrustPaymentEntity entity = TrustPaymentEntity.builder()
                    .transactionRef(ourRef)
                    .orderReference(orderRef)
                    .requestReference(params.get("requestreference"))
                    .transactionReference(txRef)
                    .amount(amount)
                    .currencyIso(params.getOrDefault("currencyiso3a", "GBP"))
                    .paymentStatus(params.get("errorcode"))
                    .settleStatus(params.get("settlestatus"))
                    .errorCode(params.get("errorcode"))
                    .rawParams(params.toString())
                    .build();
            trustPaymentRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save trust payment record: {}", e.getMessage());
        }
    }
}

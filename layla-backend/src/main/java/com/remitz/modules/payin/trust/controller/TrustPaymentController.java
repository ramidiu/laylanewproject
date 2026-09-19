package com.remitz.modules.payin.trust.controller;

import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.modules.payin.trust.entity.TrustPaymentEntity;
import com.remitz.modules.payin.trust.repository.TrustPaymentRepository;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.service.TransactionStateMachine;
import com.remitz.modules.payin.trust.service.TrustSignatureVerifier;
import com.remitz.modules.payin.trust.service.TrustJwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final TrustSignatureVerifier signatureVerifier;
    private final TrustJwtService trustJwtService;

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

        PayOutcome outcome = applyCardPaymentResult("confirm", referenceNumber, txRef, errorCode,
                params.get("baseamount"), params.get("settlestatus"));

        return ResponseEntity.ok(Map.of(
                "success", outcome.accepted(),
                "pending", !outcome.hasResult(),
                "referenceNumber", referenceNumber != null ? referenceNumber : ""
        ));
    }

    /**
     * Server-to-server URL notification (webhook) from Trust Payments. Unlike the browser
     * /confirm, this does not depend on the customer's browser completing the redirect — it is the
     * reliable source of truth, and in particular the SETTLEMENT notification (settlestatus 10/100)
     * that moves a PROCESSING transaction to FUNDS_RECEIVED arrives here even if the customer closed
     * the tab.
     *
     * <p>Trust posts {@code application/x-www-form-urlencoded} (NOT JSON), so we bind with
     * {@link RequestParam} — which also captures the fields if they arrive on the query string.
     * We must return HTTP 200 within 8 seconds or Trust will retry the notification.
     *
     * <p>The notification does not carry our internal referenceNumber, so we resolve it from the
     * orderreference (the payment form sends orderreference=&lt;TXN ref&gt;), falling back to a row
     * the browser /confirm already linked for this transactionreference.
     */
    // Trust hits this both server-to-server (POST, form-encoded) and occasionally as a browser
    // redirect (GET, query string). Accept BOTH — a GET-only hit previously threw an unhandled
    // 405 ("Unhandled exception on GET /api/trust-payment/callback").
    @RequestMapping(value = "/callback", method = {RequestMethod.POST, RequestMethod.GET},
            consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE, MediaType.ALL_VALUE})
    @Transactional
    public ResponseEntity<Void> handleCallback(@RequestParam Map<String, String> params) {
        String errorCode = params.getOrDefault("errorcode", "");
        String orderRef  = params.get("orderreference");
        String txRef     = params.get("transactionreference");

        log.info("Trust payment callback (webhook): errorcode={} orderRef={} txRef={}", errorCode, orderRef, txRef);

        saveTrustRecord(params.get("transactionref"), orderRef, txRef, params);

        // SEC-04: authenticate the webhook via Trust's responsesitesecurity hash. Log-only by default
        // (app.trust.verify-signature=false) — reject() is only ever true once enforcement is switched on.
        TrustSignatureVerifier.Result sig = signatureVerifier.verify(params, "callback");
        if (sig.reject()) {
            log.error("Trust callback REJECTED by signature verification: {}", sig.reason());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String internalRef = resolveInternalRef(orderRef, txRef);
        if (internalRef == null) {
            log.warn("Trust callback: could not resolve internal transaction for orderRef={} txRef={} — recorded only", orderRef, txRef);
            return ResponseEntity.ok().build();
        }

        applyCardPaymentResult("callback", internalRef, txRef, errorCode,
                params.get("baseamount"), params.get("settlestatus"));
        return ResponseEntity.ok().build();
    }

    // ── TRU Connect JWT (st.js) — secure, server-signed card flow ──────────

    /**
     * Issue a signed request JWT for the TRU Connect (st.js) card flow. The amount is read from the
     * transaction server-side, so the browser cannot alter what will be charged (this is the fix for
     * the tampered-amount root cause). Returns 409 if the JWT integration is not configured.
     */
    @PostMapping("/jwt")
    public ResponseEntity<Map<String, Object>> issueJwt(@RequestBody Map<String, String> body) {
        String referenceNumber = body.get("referenceNumber");
        if (referenceNumber == null || referenceNumber.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "referenceNumber is required"));
        }
        if (!trustJwtService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Trust JWT integration is not configured"));
        }
        Optional<TransactionEntity> txOpt = transactionRepository.findByReferenceNumber(referenceNumber);
        if (txOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "transaction not found: " + referenceNumber));
        }
        String jwt = trustJwtService.createRequestJwt(txOpt.get());
        return ResponseEntity.ok(Map.of(
                "jwt", jwt,
                "sitereference", trustJwtService.getSiteReference(),
                "livestatus", trustJwtService.getLiveStatus()));
    }

    /**
     * Receive the signed response JWT from st.js, VERIFY its signature, and apply the AUTH result
     * through the SAME guards as the redirect flow (amount match, settlement gate, reference reuse).
     * Because the amount here is cryptographically signed by Trust, a mismatch is genuine fraud or
     * misconfiguration — never a tampered browser form. An unverifiable JWT is rejected outright.
     */
    @PostMapping("/jwt/response")
    @Transactional
    public ResponseEntity<Map<String, Object>> jwtResponse(@RequestBody Map<String, String> body) {
        String jwt = body.get("jwt");
        if (jwt == null || jwt.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "jwt is required"));
        }
        if (!trustJwtService.isEnabled()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Trust JWT integration is not configured"));
        }
        TrustJwtService.AuthResult auth;
        try {
            auth = trustJwtService.verifyAndExtract(jwt);
        } catch (Exception e) {
            log.error("Trust JWT response REJECTED — signature/parse failure: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "invalid JWT signature"));
        }
        if (auth == null) {
            return ResponseEntity.ok(Map.of("success", false, "pending", true, "referenceNumber", ""));
        }

        // Persist the raw attempt for audit, then drive status through the shared guards.
        saveTrustRecord(auth.orderReference(), auth.orderReference(), auth.transactionReference(),
                Map.of("errorcode", nv(auth.errorCode()), "settlestatus", nv(auth.settleStatus()),
                        "baseamount", nv(auth.baseAmount()), "orderreference", nv(auth.orderReference()),
                        "transactionreference", nv(auth.transactionReference()), "source", "jwt"));

        PayOutcome outcome = applyCardPaymentResult("jwt", auth.orderReference(), auth.transactionReference(),
                auth.errorCode(), auth.baseAmount(), auth.settleStatus());

        return ResponseEntity.ok(Map.of(
                "success", outcome.accepted(),
                "pending", !outcome.hasResult(),
                "referenceNumber", auth.orderReference() != null ? auth.orderReference() : ""));
    }

    private static String nv(String s) { return s == null ? "" : s; }

    // ── shared logic ──────────────────────────────────────────────────────

    /** Result of applying a card payment to a transaction. */
    private record PayOutcome(boolean accepted, boolean hasResult) {}

    /** Trust Payments settlement outcome derived from the {@code settlestatus} field. */
    private enum Settlement { SETTLED, SCHEDULED, FAILED, UNKNOWN }

    /**
     * Classify Trust's {@code settlestatus}. Per Trust Payments documentation, with errorcode=0
     * (request processed successfully) the settlestatus reports the settlement lifecycle:
     *   0, 1, 10 -> SCHEDULED: the transaction is scheduled to settle (no further action needed);
     *               funds are authorised and on their way -> we move the txn to PROCESSING.
     *   100      -> SETTLED: the transaction has settled; no further updates possible -> FUNDS_RECEIVED.
     *   2, 3     -> FAILED: suspended / cancelled — the money will NOT settle -> decline.
     */
    private Settlement classifySettlement(String settleStatus) {
        if (settleStatus == null || settleStatus.isBlank()) return Settlement.UNKNOWN;
        switch (settleStatus.trim()) {
            case "100":                        return Settlement.SETTLED;
            case "0":  case "1":  case "10":   return Settlement.SCHEDULED;
            case "2":  case "3":               return Settlement.FAILED;
            default:                            return Settlement.UNKNOWN;
        }
    }

    /**
     * Single source of truth for turning a Trust Payments result into a transaction status change,
     * used by BOTH the browser /confirm and the server-to-server /callback. Applies the guards:
     *   0) no errorcode      -> don't guess (leave status untouched)
     *   1) reference reuse   -> a transactionreference that already funded another txn is rejected
     *   2) amount match      -> best-effort: if Trust echoes a baseamount it MUST equal the expected
     *                           debit (reject on mismatch); a missing amount funds on authorisation
     *                           using our own expected amount, flagged for reconciliation
     *   3) settlement gate   -> settlestatus 0/1/10 (scheduled to settle) -> PROCESSING; 100 (settled)
     *                           -> FUNDS_RECEIVED; 2/3 (suspended/cancelled) -> not funded.
     */
    private PayOutcome applyCardPaymentResult(String source, String referenceNumber, String txRef,
                                              String errorCode, String baseamount, String settleStatus) {
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

            // GUARD 2 — amount match (best-effort): if Trust echoes a baseamount we verify it equals
            // the expected debit and REJECT on mismatch. But Trust's notifications for this account
            // (site laylalondo147951, DELTA card) do NOT carry a baseamount, so a missing/unparseable
            // amount must NOT block funding — we fund on the authorisation (errorcode=0) using OUR own
            // expected amount and flag the txn for reconciliation instead. Fail-closed blocking here
            // stalled ALL card payments from 2026-09-07 ("no baseamount ... leaving PENDING").
            boolean amountVerified = false;
            if (isSuccess) {
                BigDecimal expected = tx.getTotalDebitAmount() != null
                        ? tx.getTotalDebitAmount() : tx.getSendAmount();
                if (baseamount == null || baseamount.isBlank()) {
                    log.warn("Trust {} : no baseamount for ref={} — funding on authorisation using expected {} "
                            + "(amount NOT independently verified; flagged for reconciliation)",
                            source, referenceNumber, expected);
                } else {
                    try {
                        BigDecimal charged = new BigDecimal(baseamount).movePointLeft(2);
                        if (expected != null && charged.compareTo(expected) != 0) {
                            isSuccess = false;
                            declineReason = "Card payment amount mismatch: charged " + charged
                                    + " but transaction expects " + expected + ".";
                            log.error("Trust {} REJECTED for ref={}: charged {} != expected {}",
                                    source, referenceNumber, charged, expected);
                        } else {
                            amountVerified = true;
                        }
                    } catch (NumberFormatException nfe) {
                        log.warn("Trust {} : unparseable baseamount '{}' for ref={} — funding on authorisation "
                                + "(amount NOT verified; flagged for reconciliation)", source, baseamount, referenceNumber);
                    }
                }
            }

            // GUARD 3 — settlement gate: an authorised card (errorcode=0) with settlestatus 0/1/10 is
            // scheduled to settle (still moving forward); only settlestatus=100 means the funds have
            // actually settled. A suspended or cancelled settlement (2,3) means the money will never
            // arrive — treat as not funded and decline.
            Settlement settlement = classifySettlement(settleStatus);
            if (isSuccess && settlement == Settlement.FAILED) {
                isSuccess = false;
                declineReason = "Card payment not settled by Trust Payments (settlestatus=" + settleStatus + ").";
                log.error("Trust {} REJECTED for ref={}: settlement failed, settlestatus={}",
                        source, referenceNumber, settleStatus);
            }

            if (isSuccess) {
                // Phase 1 — authorised + amount-verified: move to PROCESSING (funds authorised, but
                // NOT yet received). Release a compliance hold first if present.
                if (tx.getStatus() == TransactionStatus.PENDING
                        || tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                    tx.setPaymentReference(txRef);
                    if (tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                        tx = stateMachine.transition(tx, TransactionStatus.PENDING,
                                0L, ActorType.SYSTEM, "Card payment authorised — releasing compliance hold for manual review");
                    }
                    tx = stateMachine.transition(tx, TransactionStatus.PROCESSING,
                            0L, ActorType.SYSTEM, "Card payment authorised via Trust Payments (" + source
                                    + "), settlestatus=" + settleStatus);
                }

                // Phase 2 — only a SETTLED notification (settlestatus=100) recognises the money as
                // actually received (PROCESSING -> FUNDS_RECEIVED). A SCHEDULED payment (settlestatus
                // 0/1/10) is authorised and on its way to settle, so it correctly rests at PROCESSING
                // per Trust's lifecycle — the transaction is already moving forward and no further
                // action is required until (if) a settlement callback arrives.
                if (settlement == Settlement.SETTLED) {
                    if (tx.getStatus() == TransactionStatus.PROCESSING) {
                        tx = stateMachine.transition(tx, TransactionStatus.FUNDS_RECEIVED,
                                0L, ActorType.SYSTEM, "Card funds settled (settlestatus=" + settleStatus + ")"
                                        + (amountVerified ? "" : " [amount unverified — reconcile]")
                                        + ". Trust ref: " + txRef);
                    }
                    // else: already at/past FUNDS_RECEIVED — idempotent no-op.
                } else {
                    log.info("Trust {} : ref={} authorised & scheduled to settle (settlestatus={}) — at {}, "
                            + "moving forward{}", source, referenceNumber, settleStatus, tx.getStatus(),
                            amountVerified ? "" : " [amount unverified — reconcile]");
                }
            } else {
                // Declined or guard-rejected — do NOT fund; cancel if still awaiting payment.
                if (tx.getStatus() == TransactionStatus.PENDING
                        || tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                    String reason = declineReason != null ? declineReason
                            : ("Card payment declined by Trust Payments. Error code: " + errorCode);
                    tx.setFailureReason(reason);
                    tx = stateMachine.transition(tx, TransactionStatus.CANCELLED,
                            0L, ActorType.SYSTEM, reason);
                } else {
                    log.warn("Trust {} : ref={} not fundable (settlestatus={}, reason={}) but already at {} — "
                            + "needs manual review", source, referenceNumber, settleStatus, declineReason, tx.getStatus());
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

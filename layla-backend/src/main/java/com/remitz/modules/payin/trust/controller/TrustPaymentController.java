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
        String referenceNumber = params.get("referenceNumber"); // our internal transaction ref
        boolean isSuccess      = "0".equals(errorCode);

        log.info("Trust card confirm: success={} errorcode={} orderRef={} txRef={} ref={}",
                isSuccess, errorCode, orderRef, txRef, referenceNumber);

        // Save the trust payment record
        try {
            BigDecimal amount = null;
            String baseamount = params.get("baseamount");
            if (baseamount != null && !baseamount.isBlank()) {
                try { amount = new BigDecimal(baseamount).movePointLeft(2); } catch (NumberFormatException ignored) {}
            }
            TrustPaymentEntity entity = TrustPaymentEntity.builder()
                    .transactionRef(referenceNumber)
                    .orderReference(orderRef)
                    .requestReference(params.get("requestreference"))
                    .transactionReference(txRef)
                    .amount(amount)
                    .currencyIso(params.getOrDefault("currencyiso3a", "GBP"))
                    .paymentStatus(errorCode)
                    .settleStatus(params.get("settlestatus"))
                    .errorCode(errorCode)
                    .rawParams(params.toString())
                    .build();
            trustPaymentRepository.save(entity);
        } catch (Exception e) {
            log.warn("Failed to save trust payment record: {}", e.getMessage());
        }

        // Update the transaction status
        if (referenceNumber != null && !referenceNumber.isBlank()) {
            Optional<TransactionEntity> txOpt = transactionRepository.findByReferenceNumber(referenceNumber);
            if (txOpt.isPresent()) {
                TransactionEntity tx = txOpt.get();
                try {
                    if (isSuccess) {
                        // Accept PENDING or COMPLIANCE_HOLD — card funds are confirmed either way
                        if (tx.getStatus() == TransactionStatus.PENDING
                                || tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                            tx.setPaymentReference(txRef);
                            // COMPLIANCE_HOLD must go back to PENDING before PROCESSING
                            if (tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                                tx = stateMachine.transition(tx, TransactionStatus.PENDING,
                                        0L, ActorType.SYSTEM, "Card payment received — releasing compliance hold for manual review");
                            }
                            tx = stateMachine.transition(tx, TransactionStatus.PROCESSING,
                                    0L, ActorType.SYSTEM, "Card payment processing via Trust Payments");
                            tx = stateMachine.transition(tx, TransactionStatus.FUNDS_RECEIVED,
                                    0L, ActorType.SYSTEM, "Card funds confirmed. Trust ref: " + txRef);
                        }
                    } else {
                        // Payment failed — cancel the transaction
                        if (tx.getStatus() == TransactionStatus.PENDING
                                || tx.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                            tx.setFailureReason("Card payment declined by Trust Payments. Error code: " + errorCode);
                            tx = stateMachine.transition(tx, TransactionStatus.CANCELLED,
                                    0L, ActorType.SYSTEM, "Card payment failed. Error code: " + errorCode);
                        }
                    }
                    log.info("Transaction {} updated to {}", referenceNumber, tx.getStatus());
                } catch (Exception e) {
                    log.error("Failed to update transaction status for ref={}: {}", referenceNumber, e.getMessage(), e);
                }
            } else {
                log.warn("Transaction not found for referenceNumber={}", referenceNumber);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", isSuccess,
                "referenceNumber", referenceNumber != null ? referenceNumber : ""
        ));
    }

    /**
     * Legacy callback endpoint — kept for backwards compatibility / server-side webhook use.
     */
    @PostMapping("/callback")
    public ResponseEntity<Void> handleCallback(@RequestBody Map<String, String> params) {
        log.info("Trust payment callback: code={} orderRef={}", params.get("code"), params.get("orderreference"));
        try {
            BigDecimal amount = null;
            String baseamount = params.get("baseamount");
            if (baseamount != null && !baseamount.isBlank()) {
                try { amount = new BigDecimal(baseamount).movePointLeft(2); } catch (NumberFormatException ignored) {}
            }
            TrustPaymentEntity entity = TrustPaymentEntity.builder()
                    .transactionRef(params.get("transactionref"))
                    .orderReference(params.get("orderreference"))
                    .requestReference(params.get("requestreference"))
                    .transactionReference(params.get("transactionreference"))
                    .amount(amount)
                    .currencyIso(params.getOrDefault("currencyiso3a", "GBP"))
                    .paymentStatus(params.get("errorcode"))
                    .settleStatus(params.get("settlestatus"))
                    .errorCode(params.get("errorcode"))
                    .rawParams(params.toString())
                    .build();
            trustPaymentRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to save trust payment callback: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok().build();
    }
}

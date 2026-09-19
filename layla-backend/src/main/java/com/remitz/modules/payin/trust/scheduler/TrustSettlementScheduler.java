package com.remitz.modules.payin.trust.scheduler;

import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.modules.payin.trust.repository.TrustPaymentRepository;
import com.remitz.modules.payin.trust.service.TrustStatusService;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.service.TransactionStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Polls Trust Payments (TRANSACTIONQUERY) for PENDING card (Trust) transactions and, when Trust
 * reports the payment as SETTLED ({@code settlestatus=100}), advances the transaction from PENDING
 * to PROCESSING via the shared state machine — the same forward move the live callback performs.
 *
 * <p>Scope is deliberately narrow and safe:
 * <ul>
 *   <li>ONLY {@link PaymentMethodType#CARD} (Trust) transactions.</li>
 *   <li>ONLY transactions currently at {@link TransactionStatus#PENDING}.</li>
 *   <li>ONLY on a confirmed {@code settlestatus=100} (SETTLED); every other status is left untouched.</li>
 *   <li>Read-only against Trust; the only local write is the PENDING → PROCESSING transition.</li>
 * </ul>
 *
 * <p>Recovers card transactions whose settlement notification was missed (e.g. the customer closed
 * the browser before the redirect). No-op when disabled ({@code app.trust.settlement-poll.enabled=false})
 * or when the Trust Webservices credentials are not configured.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrustSettlementScheduler {

    private final TransactionRepository transactionRepository;
    private final TrustPaymentRepository trustPaymentRepository;
    private final TrustStatusService trustStatusService;
    private final TransactionStateMachine stateMachine;

    @Value("${app.trust.settlement-poll.enabled:true}")
    private boolean enabled;

    /** Trust's SETTLED settlestatus — the only value that advances a transaction here. */
    private static final String SETTLED = "100";

    @Scheduled(fixedDelayString = "${app.trust.settlement-poll.interval-ms:300000}")
    public void pollPendingCardSettlements() {
        if (!enabled) {
            return;
        }
        if (!trustStatusService.isConfigured()) {
            log.debug("Trust settlement poll — skipped (Webservices credentials not configured)");
            return;
        }

        List<TransactionEntity> pending =
                transactionRepository.findByStatusAndPaymentMethodType(TransactionStatus.PENDING, PaymentMethodType.CARD);
        if (pending.isEmpty()) {
            log.debug("Trust settlement poll — no PENDING card transactions");
            return;
        }

        log.info("Trust settlement poll — checking {} PENDING card transactions", pending.size());
        int advanced = 0;
        for (TransactionEntity tx : pending) {
            try {
                if (checkAndAdvance(tx)) {
                    advanced++;
                }
            } catch (Exception e) {
                // One bad transaction must never stop the sweep.
                log.warn("Trust settlement poll — check failed for {}: {}", tx.getReferenceNumber(), e.getMessage());
            }
        }
        if (advanced > 0) {
            log.info("Trust settlement poll — advanced {} transaction(s) to PROCESSING", advanced);
        }
    }

    /** Resolve the Trust transactionreference, query Trust, and advance to PROCESSING iff SETTLED. */
    private boolean checkAndAdvance(TransactionEntity tx) {
        String transactionRef = resolveTransactionRef(tx);
        if (transactionRef == null || transactionRef.isBlank()) {
            // No Trust reference on file (e.g. customer never completed the card payment) — nothing to query.
            log.debug("Trust settlement poll — {} has no Trust transactionreference, skipping", tx.getReferenceNumber());
            return false;
        }

        TrustStatusService.SettlementView view = trustStatusService.queryByTransactionReference(transactionRef);
        if (!SETTLED.equals(view.settleStatus())) {
            log.debug("Trust settlement poll — {} not settled yet (settlestatus={}, state={})",
                    tx.getReferenceNumber(), view.settleStatus(), view.settlementState());
            return false;
        }

        // Amount verification: the charged baseamount (from Trust's TRANSACTIONQUERY) MUST equal our
        // expected total debit. Fail-closed — a missing/unparseable/mismatching amount is NOT advanced.
        BigDecimal expected = tx.getTotalDebitAmount() != null ? tx.getTotalDebitAmount() : tx.getSendAmount();
        BigDecimal charged = TrustStatusService.baseAmountToMajor(view.baseAmount());
        if (charged == null) {
            log.warn("Trust settlement poll — {} SETTLED but Trust returned no verifiable baseamount ('{}') — NOT advancing, needs review",
                    tx.getReferenceNumber(), view.baseAmount());
            return false;
        }
        if (expected == null || charged.compareTo(expected) != 0) {
            log.error("Trust settlement poll — {} SETTLED but amount mismatch: charged {} != expected {} — NOT advancing, needs review",
                    tx.getReferenceNumber(), charged, expected);
            return false;
        }

        // Re-read guard: only advance if still PENDING (a concurrent callback may have moved it already).
        if (tx.getStatus() != TransactionStatus.PENDING) {
            return false;
        }
        if (tx.getPaymentReference() == null || tx.getPaymentReference().isBlank()) {
            tx.setPaymentReference(transactionRef);
        }
        stateMachine.transition(tx, TransactionStatus.PROCESSING, 0L, ActorType.SYSTEM,
                "Trust settlement confirmed by scheduler (settlestatus=100, amount verified " + charged
                        + ") — advancing to PROCESSING");
        log.info("Trust settlement poll — {} SETTLED & amount verified ({}), advanced PENDING → PROCESSING",
                tx.getReferenceNumber(), charged);
        return true;
    }

    /** Prefer the reference stamped on the transaction; fall back to the saved trust_payments row. */
    private String resolveTransactionRef(TransactionEntity tx) {
        if (tx.getPaymentReference() != null && !tx.getPaymentReference().isBlank()) {
            return tx.getPaymentReference();
        }
        return trustPaymentRepository.findByOrderReference(tx.getReferenceNumber())
                .map(r -> r.getTransactionReference())
                .orElse(null);
    }
}

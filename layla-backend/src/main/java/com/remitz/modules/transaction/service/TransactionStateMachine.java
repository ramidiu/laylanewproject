package com.remitz.modules.transaction.service;

import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.exception.InvalidStateTransitionException;
import com.remitz.modules.remitone.service.RemitOneService;
import com.remitz.modules.transaction.config.RedisPublisher;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.entity.TransactionStatusHistoryEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.repository.TransactionStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Enforces the legal state transitions for a {@link TransactionEntity}.
 *
 * <p>The transaction lifecycle is the single most critical piece of business
 * logic in the platform — money only moves when a transaction passes through
 * a specific sequence of states, and invalid jumps must be rejected outright.
 *
 * <h2>Legal transitions</h2>
 * <pre>
 *   CREATED          → PENDING, CANCELLED
 *   PENDING          → COMPLIANCE_HOLD, PROCESSING, CANCELLED
 *   COMPLIANCE_HOLD  → PENDING, PROCESSING, CANCELLED
 *   PROCESSING       → FUNDS_RECEIVED, SENT_TO_PAYOUT, PAID, FAILED, REFUNDED
 *   FUNDS_RECEIVED   → SENT_TO_PAYOUT, FAILED, REFUNDED
 *   SENT_TO_PAYOUT   → PAID, FAILED
 *   PAID             → REFUNDED
 *   FAILED           → REFUNDED
 * </pre>
 *
 * <p>Any transition not in this map is rejected with
 * {@link com.remitz.common.exception.InvalidStateTransitionException}.
 * The {@code transition} method also writes a row to
 * {@code transaction_status_history} for audit, and publishes a Redis event
 * so downstream services (compliance, notification, partners) can react.
 *
 * <h2>Threading and transactions</h2>
 * All transitions happen inside a Spring transaction so the status change and
 * its history row commit atomically. Callers must not hold their own database
 * connection when invoking this — JPA will reuse the caller's context.
 *
 * <h2>Adding a new status</h2>
 * 1. Add the enum value to {@code remittance-common/enums/TransactionStatus}.<br>
 * 2. Add the source status to {@link #ALLOWED_TRANSITIONS} with an entry for
 *    each legal target.<br>
 * 3. Update the frontend status badge colours in
 *    {@code remitz-ui/.../transactions/*}.<br>
 * 4. Add a regression test covering both the happy path and the rejection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionStateMachine {

    private static final Map<TransactionStatus, Set<TransactionStatus>> ALLOWED_TRANSITIONS = Map.of(
            TransactionStatus.CREATED, Set.of(TransactionStatus.PENDING, TransactionStatus.CANCELLED),
            TransactionStatus.PENDING, Set.of(TransactionStatus.COMPLIANCE_HOLD, TransactionStatus.PROCESSING, TransactionStatus.CANCELLED),
            TransactionStatus.COMPLIANCE_HOLD, Set.of(TransactionStatus.PENDING, TransactionStatus.PROCESSING, TransactionStatus.CANCELLED),
            TransactionStatus.PROCESSING, Set.of(TransactionStatus.FUNDS_RECEIVED, TransactionStatus.SENT_TO_PAYOUT, TransactionStatus.PAID, TransactionStatus.FAILED, TransactionStatus.REFUNDED),
            TransactionStatus.FUNDS_RECEIVED, Set.of(TransactionStatus.SENT_TO_PAYOUT, TransactionStatus.FAILED, TransactionStatus.REFUNDED),
            TransactionStatus.SENT_TO_PAYOUT, Set.of(TransactionStatus.PAID, TransactionStatus.FAILED),
            TransactionStatus.PAID, Set.of(TransactionStatus.COMPLETED, TransactionStatus.REFUNDED),
            TransactionStatus.COMPLETED, Set.of(TransactionStatus.REFUNDED),
            TransactionStatus.FAILED, Set.of(TransactionStatus.REFUNDED)
    );

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final RedisPublisher redisPublisher;
    private final RemitOneService remitOneService;

    @Transactional
    public TransactionEntity transition(TransactionEntity tx, TransactionStatus target,
                                        Long actorId, ActorType actorType, String reason) {
        TransactionStatus currentStatus = tx.getStatus();

        // Validate the transition is allowed
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(target)) {
            throw new InvalidStateTransitionException(currentStatus.name(), target.name());
        }

        log.info("Transitioning transaction {} from {} to {} by {} ({})",
                tx.getReferenceNumber(), currentStatus, target, actorId, actorType);

        // Update the transaction status
        tx.setStatus(target);
        TransactionEntity savedTx = transactionRepository.save(tx);

        // Create history record
        TransactionStatusHistoryEntity history = TransactionStatusHistoryEntity.builder()
                .transaction(savedTx)
                .fromStatus(currentStatus)
                .toStatus(target)
                .actorId(actorId)
                .actorType(actorType)
                .reason(reason)
                .build();
        statusHistoryRepository.save(history);

        // Trigger RemitOne compliance for Sudan (SDG) transactions moving to PROCESSING
        if (target == TransactionStatus.PROCESSING && "SDG".equalsIgnoreCase(savedTx.getReceiveCurrency())) {
            remitOneService.triggerCompliance(savedTx.getId());
        }

        // Publish event via Redis
        redisPublisher.publishTransactionEvent("STATUS_CHANGED", Map.of(
                "transactionId", savedTx.getId(),
                "referenceNumber", savedTx.getReferenceNumber(),
                "fromStatus", currentStatus.name(),
                "toStatus", target.name(),
                "actorId", actorId != null ? actorId : 0L,
                "actorType", actorType.name(),
                "reason", reason != null ? reason : ""
        ));

        return savedTx;
    }

    public boolean isTransitionAllowed(TransactionStatus from, TransactionStatus to) {
        Set<TransactionStatus> allowed = ALLOWED_TRANSITIONS.get(from);
        return allowed != null && allowed.contains(to);
    }
}

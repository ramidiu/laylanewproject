package com.remitz.modules.payout.usi.scheduler;

import com.remitz.modules.payout.usi.UsiPayoutProperties;
import com.remitz.modules.payout.usi.entity.UsiTransactionEntity;
import com.remitz.modules.payout.usi.repository.UsiTransactionRepository;
import com.remitz.modules.payout.usi.service.UsiPayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Polls USI Money every {@code usi.status-poll-interval-ms} for the latest
 * status of every transaction that's still in "sent for pay". When USI
 * reports PROCESSED / DELETED / ABORTED, the underlying USI row is updated
 * (handled inside {@link UsiPayoutService#getTransactionStatus(String)}).
 *
 * No-op when {@code usi.enabled=false}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UsiTransactionStatusScheduler {

    private final UsiPayoutProperties props;
    private final UsiPayoutService usiPayoutService;
    private final UsiTransactionRepository usiTransactionRepository;

    @Scheduled(fixedDelayString = "${usi.status-poll-interval-ms:300000}")
    public void pollSentForPay() {
        if (!props.isEnabled()) {
            return;
        }

        // Poll every non-terminal status.
        List<UsiTransactionEntity> pending = new java.util.ArrayList<>(usiTransactionRepository.findByStatusIn(
                java.util.List.of("sent for pay", "awaiting_compliance", "initiated")));
        // Also re-check recently-FAILED transactions: USI Support can clear a validation failure
        // (e.g. a duplicate ID document) after the fact, and USI then advances the payout. getTransactionStatus
        // recovers the local record if USI is progressing again; if it's still failed, nothing changes.
        // Bounded to 14 days so we don't re-poll ancient failures forever.
        pending.addAll(usiTransactionRepository.findByStatusInAndUpdatedAtAfter(
                java.util.List.of("failed"), java.time.LocalDateTime.now().minusDays(14)));
        if (pending.isEmpty()) {
            log.debug("USI status poll — no in-flight transactions");
            return;
        }

        log.info("USI status poll — checking {} transactions", pending.size());
        for (UsiTransactionEntity row : pending) {
            try {
                Map<String, Object> result = usiPayoutService.getTransactionStatus(row.getTransactionId());
                log.debug("USI status {} → {}", row.getTransactionId(), result.get("usiStatus"));
            } catch (Exception e) {
                log.warn("USI status check failed for {}: {}", row.getTransactionId(), e.getMessage());
            }
        }
    }
}

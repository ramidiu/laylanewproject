package com.remitz.modules.transaction.scheduler;

import com.remitz.common.enums.TransactionStatus;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Archives transactions that have been stuck in PENDING for more than 3 hours.
 *
 * Archived transactions are hidden from the admin / super-admin transaction list
 * (the list query excludes status = ARCHIVED unless explicitly filtered).
 *
 * Runs every 15 minutes.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingTransactionArchiveScheduler {

    private static final int STALE_PENDING_HOURS = 3;

    private final TransactionRepository transactionRepository;

    @Scheduled(fixedDelay = 15 * 60 * 1000L, initialDelay = 60 * 1000L)
    @Transactional
    public void archiveStalePendingTransactions() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(STALE_PENDING_HOURS);
        List<TransactionEntity> stale =
                transactionRepository.findByStatusAndCreatedAtBefore(TransactionStatus.PENDING, cutoff);
        if (stale.isEmpty()) {
            log.debug("No stale PENDING transactions to archive");
            return;
        }
        log.info("Archiving {} transaction(s) pending for more than {} hours", stale.size(), STALE_PENDING_HOURS);
        for (TransactionEntity tx : stale) {
            try {
                tx.setStatus(TransactionStatus.ARCHIVED);
                tx.setUpdatedAt(LocalDateTime.now());
                transactionRepository.save(tx);
                log.info("Archived stale PENDING transaction {} (id={}, created={})",
                        tx.getReferenceNumber(), tx.getId(), tx.getCreatedAt());
            } catch (Exception e) {
                log.warn("Failed to archive transaction id={}: {}", tx.getId(), e.getMessage());
            }
        }
    }
}

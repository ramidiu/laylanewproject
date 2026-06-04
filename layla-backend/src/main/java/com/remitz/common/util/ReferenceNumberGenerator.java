package com.remitz.common.util;

import com.remitz.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates sequential transaction references in the form "TXN<n>".
 * The sequence is seeded once from MAX(reference_number) at startup / first use
 * and then advanced in-process to avoid a per-call DB scan. A unique-key
 * collision (e.g. multi-pod deploy) is handled by callers via retry.
 */
@Component
@RequiredArgsConstructor
public class ReferenceNumberGenerator {

    public static final String PREFIX = "TXN";
    private static final AtomicLong COUNTER = new AtomicLong(-1);
    private static TransactionRepository repoHolder;

    private final TransactionRepository transactionRepository;

    @Autowired
    void wireRepo() {
        repoHolder = transactionRepository;
    }

    /** Static accessor preserved so existing call sites compile unchanged. */
    public static String generate() {
        if (COUNTER.get() < 0) {
            synchronized (COUNTER) {
                if (COUNTER.get() < 0) {
                    Long max = repoHolder != null ? repoHolder.findMaxTxnSequence() : null;
                    COUNTER.set(max == null ? 0L : max);
                }
            }
        }
        return PREFIX + COUNTER.incrementAndGet();
    }
}

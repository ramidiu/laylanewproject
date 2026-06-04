package com.remitz.modules.auth.scheduler;

import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.PasswordHistoryRepository;
import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Deletes unverified user accounts older than 7 days.
 * Runs daily at 03:00 UTC.
 *
 * This allows customers who registered but never verified their email to
 * register again with the same email after 7 days. For abandoned registrations
 * less than 7 days old, the register endpoint resends the OTP instead.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UnverifiedAccountCleanupScheduler {

    private static final int STALE_UNVERIFIED_DAYS = 7;

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupUnverifiedAccounts() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(STALE_UNVERIFIED_DAYS);
        List<UserEntity> stale = userRepository.findByEmailVerifiedFalseAndCreatedAtBefore(cutoff);
        if (stale.isEmpty()) {
            log.debug("No stale unverified accounts to clean up");
            return;
        }
        log.info("Cleaning up {} unverified accounts older than {} days", stale.size(), STALE_UNVERIFIED_DAYS);
        for (UserEntity user : stale) {
            try {
                passwordHistoryRepository.deleteAll(passwordHistoryRepository.findByUserId(user.getId()));
                userRepository.delete(user);
                log.info("Deleted stale unverified account: {} (id={}, created={})",
                        user.getEmail(), user.getId(), user.getCreatedAt());
            } catch (Exception e) {
                log.warn("Failed to delete unverified account id={}: {}", user.getId(), e.getMessage());
            }
        }
    }
}

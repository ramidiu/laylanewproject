package com.remitz.modules.user.service;

import com.remitz.common.enums.UserStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.ReferralCodeEntity;
import com.remitz.modules.user.entity.ReferralConfigEntity;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.user.repository.ReferralCodeRepository;
import com.remitz.modules.user.repository.ReferralConfigRepository;
import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReferralService {

    private final ReferralCodeRepository referralCodeRepository;
    private final ReferralConfigRepository referralConfigRepository;
    private final UserRepository userRepository;
    private final WalletService walletService;

    private static final String ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Transactional
    public ReferralCodeEntity getOrCreateCode(Long userId) {
        return referralCodeRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserEntity user = userRepository.findById(userId)
                            .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

                    String code = generateUniqueCode(user);

                    ReferralCodeEntity referralCode = ReferralCodeEntity.builder()
                            .userId(userId)
                            .code(code)
                            .usageCount(0)
                            .isActive(true)
                            .build();

                    log.info("Creating referral code for userId={}, code={}", userId, code);
                    return referralCodeRepository.save(referralCode);
                });
    }

    private String generateUniqueCode(UserEntity user) {
        String prefix = "";
        if (user.getFirstName() != null && user.getFirstName().length() >= 2) {
            prefix = user.getFirstName().substring(0, 2).toUpperCase();
        } else if (user.getFirstName() != null && user.getFirstName().length() == 1) {
            prefix = user.getFirstName().toUpperCase() + "X";
        } else {
            prefix = "FB";
        }

        for (int attempt = 0; attempt < 10; attempt++) {
            StringBuilder sb = new StringBuilder(prefix);
            for (int i = 0; i < 4; i++) {
                sb.append(ALPHANUMERIC.charAt(RANDOM.nextInt(ALPHANUMERIC.length())));
            }
            String code = sb.toString();

            if (referralCodeRepository.findByCode(code).isEmpty()) {
                return code;
            }
            log.warn("Referral code collision for code={}, retrying...", code);
        }

        throw new RemitzException("Unable to generate unique referral code after 10 attempts", HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public Map<String, Object> validateCode(String code, Long corridorId) {
        Map<String, Object> result = new HashMap<>();

        Optional<ReferralCodeEntity> referralOpt = referralCodeRepository.findByCodeAndIsActiveTrue(code);
        if (referralOpt.isEmpty()) {
            result.put("valid", false);
            result.put("rateBoostPercentage", BigDecimal.ZERO);
            result.put("referrerUserId", null);
            return result;
        }

        ReferralCodeEntity referral = referralOpt.get();

        // Check referrer user is active
        Optional<UserEntity> referrerOpt = userRepository.findById(referral.getUserId());
        if (referrerOpt.isEmpty() || referrerOpt.get().getStatus() != UserStatus.ACTIVE) {
            result.put("valid", false);
            result.put("rateBoostPercentage", BigDecimal.ZERO);
            result.put("referrerUserId", null);
            return result;
        }

        // Look up config: corridor-specific first, then global fallback
        BigDecimal rateBoost = BigDecimal.ZERO;
        if (corridorId != null) {
            Optional<ReferralConfigEntity> corridorConfig = referralConfigRepository.findByCorridorIdAndIsActiveTrue(corridorId);
            if (corridorConfig.isPresent()) {
                rateBoost = corridorConfig.get().getRateBoostPercentage();
            }
        }
        if (rateBoost.compareTo(BigDecimal.ZERO) == 0) {
            Optional<ReferralConfigEntity> globalConfig = referralConfigRepository.findByCorridorIdIsNullAndIsActiveTrue();
            if (globalConfig.isPresent()) {
                rateBoost = globalConfig.get().getRateBoostPercentage();
            }
        }

        result.put("valid", true);
        result.put("rateBoostPercentage", rateBoost);
        result.put("referrerUserId", referral.getUserId());
        return result;
    }

    /**
     * Called by transaction-service when a referred transaction is completed.
     * Looks up the referrer from the code and credits their wallet.
     */
    @Transactional
    public void processReferralReward(String referralCode, Long corridorId, String transactionRef) {
        Optional<ReferralCodeEntity> referralOpt = referralCodeRepository.findByCodeAndIsActiveTrue(referralCode);
        if (referralOpt.isEmpty()) {
            log.warn("Referral code not found or inactive: {}", referralCode);
            return;
        }

        ReferralCodeEntity referral = referralOpt.get();
        Long referrerUserId = referral.getUserId();

        // Increment usage count
        referral.setUsageCount(referral.getUsageCount() + 1);
        referralCodeRepository.save(referral);

        // Determine credit amount from config (corridor-specific first, then global)
        BigDecimal creditAmount = BigDecimal.ZERO;
        String creditCurrency = "GBP";

        if (corridorId != null) {
            Optional<ReferralConfigEntity> corridorConfig = referralConfigRepository.findByCorridorIdAndIsActiveTrue(corridorId);
            if (corridorConfig.isPresent()) {
                creditAmount = corridorConfig.get().getReferrerCreditAmount();
                creditCurrency = corridorConfig.get().getCreditCurrency();
            }
        }

        if (creditAmount.compareTo(BigDecimal.ZERO) == 0) {
            Optional<ReferralConfigEntity> globalConfig = referralConfigRepository.findByCorridorIdIsNullAndIsActiveTrue();
            if (globalConfig.isPresent()) {
                creditAmount = globalConfig.get().getReferrerCreditAmount();
                creditCurrency = globalConfig.get().getCreditCurrency();
            }
        }

        if (creditAmount.compareTo(BigDecimal.ZERO) > 0) {
            walletService.credit(
                    referrerUserId,
                    creditAmount,
                    "Referral reward — friend used code " + referralCode + " (txn: " + transactionRef + ")",
                    referralCode,
                    "REFERRAL_CREDIT"
            );
            log.info("Credited referrer userId={} amount={} {} for referral code={}, txn={}",
                    referrerUserId, creditAmount, creditCurrency, referralCode, transactionRef);
        } else {
            log.info("No referral credit configured, skipping wallet credit for code={}", referralCode);
        }
    }

    /**
     * @deprecated Use processReferralReward instead
     */
    @Transactional
    public void onTransactionCompleted(String referralCode, Long referrerUserId, BigDecimal transactionAmount) {
        processReferralReward(referralCode, null, null);
    }

    public List<ReferralConfigEntity> getAdminConfigs() {
        return referralConfigRepository.findAll();
    }

    @Transactional
    public ReferralConfigEntity saveAdminConfig(Long corridorId, BigDecimal rateBoost, BigDecimal creditAmount, String currency, Boolean isActive) {
        // Find existing config for this corridor (or global if corridorId is null)
        Optional<ReferralConfigEntity> existingOpt;
        if (corridorId == null) {
            existingOpt = referralConfigRepository.findByCorridorIdIsNullAndIsActiveTrue();
        } else {
            existingOpt = referralConfigRepository.findByCorridorIdAndIsActiveTrue(corridorId);
        }

        ReferralConfigEntity config;
        if (existingOpt.isPresent()) {
            config = existingOpt.get();
            config.setRateBoostPercentage(rateBoost);
            config.setReferrerCreditAmount(creditAmount);
            config.setCreditCurrency(currency);
            config.setIsActive(isActive);
        } else {
            config = ReferralConfigEntity.builder()
                    .corridorId(corridorId)
                    .rateBoostPercentage(rateBoost)
                    .referrerCreditAmount(creditAmount)
                    .creditCurrency(currency)
                    .isActive(isActive)
                    .build();
        }

        log.info("Saving referral config: corridorId={}, rateBoost={}, creditAmount={} {}", corridorId, rateBoost, creditAmount, currency);
        return referralConfigRepository.save(config);
    }
}

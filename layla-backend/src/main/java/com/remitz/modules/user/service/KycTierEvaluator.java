package com.remitz.modules.user.service;

import com.remitz.common.enums.KycDocumentStatus;
import com.remitz.common.enums.KycDocumentType;
import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.UserStatus;
import com.remitz.modules.user.entity.KycAuditLogEntity;
import com.remitz.modules.user.entity.KycDocumentEntity;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.user.config.RedisPublisher;
import com.remitz.modules.user.repository.KycAuditLogRepository;
import com.remitz.modules.user.repository.KycDocumentRepository;
import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class KycTierEvaluator {

    private static final Set<KycDocumentType> ID_DOCUMENTS = EnumSet.of(
            KycDocumentType.PASSPORT,
            KycDocumentType.DRIVING_LICENCE,
            KycDocumentType.NATIONAL_ID
    );

    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final KycAuditLogRepository kycAuditLogRepository;
    private final RedisPublisher redisPublisher;

    @Transactional
    public void evaluateAndUpgrade(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        List<KycDocumentEntity> approvedDocs = kycDocumentRepository
                .findByUserIdAndStatus(userId, KycDocumentStatus.APPROVED);

        Set<KycDocumentType> approvedTypes = approvedDocs.stream()
                .map(KycDocumentEntity::getDocumentType)
                .collect(Collectors.toSet());

        KycTier currentTier = user.getKycTier();
        KycTier newTier = calculateTier(approvedTypes);

        if (newTier.ordinal() > currentTier.ordinal()) {
            log.info("Upgrading user {} from {} to {}", userId, currentTier, newTier);
            user.setKycTier(newTier);
            // Once KYC approval lifts the user to a verified tier (TIER_1+), activate the
            // account so the admin Users list shows ACTIVE/verified instead of
            // "pending verification".
            if (newTier != KycTier.TIER_0 && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                user.setStatus(UserStatus.ACTIVE);
                log.info("Activated user {} after KYC verification (tier {})", userId, newTier);
            }
            userRepository.save(user);

            KycAuditLogEntity auditLog = KycAuditLogEntity.builder()
                    .userId(userId)
                    .action("TIER_UPGRADED")
                    .details(String.format("{\"previousTier\": \"%s\", \"newTier\": \"%s\", \"approvedDocuments\": %d}",
                            currentTier, newTier, approvedDocs.size()))
                    .build();
            kycAuditLogRepository.save(auditLog);

            // Publish tier upgrade notification
            try {
                Map<String, String> vars = new HashMap<>();
                vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "Customer");
                vars.put("newTier", newTier.toString());
                vars.put("previousTier", currentTier.toString());
                redisPublisher.publishKycEvent("KYC_TIER_UPGRADED",
                        user.getId(), user.getEmail(), user.getFirstName(), vars);
            } catch (Exception e) {
                log.warn("Failed to publish tier upgrade event: {}", e.getMessage());
            }
        } else {
            log.debug("User {} remains at tier {} (evaluated tier: {})", userId, currentTier, newTier);
        }
    }

    private KycTier calculateTier(Set<KycDocumentType> approvedTypes) {
        boolean hasIdDocument = approvedTypes.stream().anyMatch(ID_DOCUMENTS::contains);
        boolean hasProofOfAddress = approvedTypes.contains(KycDocumentType.PROOF_OF_ADDRESS);
        boolean hasSourceOfFunds = approvedTypes.contains(KycDocumentType.SOURCE_OF_FUNDS);

        if (hasIdDocument && hasProofOfAddress && hasSourceOfFunds) {
            return KycTier.TIER_3;
        } else if (hasIdDocument && hasProofOfAddress) {
            return KycTier.TIER_2;
        } else if (hasIdDocument) {
            return KycTier.TIER_1;
        } else {
            return KycTier.TIER_0;
        }
    }
}

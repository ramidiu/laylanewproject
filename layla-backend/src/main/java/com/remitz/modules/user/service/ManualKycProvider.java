package com.remitz.modules.user.service;

import com.remitz.common.enums.ScreeningStatus;
import com.remitz.common.enums.VerificationStatus;
import com.remitz.modules.user.dto.ScreeningResult;
import com.remitz.modules.user.dto.VerificationResult;
import com.remitz.modules.user.entity.KycDocumentEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@Slf4j
public class ManualKycProvider implements KycVerificationProvider {

    @Override
    public VerificationResult verifyIdentity(String userId, KycDocumentEntity document) {
        log.info("Manual identity verification requested for user: {}, document type: {}",
                userId, document.getDocumentType());

        return VerificationResult.builder()
                .status(VerificationStatus.PENDING)
                .providerReference("MANUAL-" + UUID.randomUUID().toString().substring(0, 8))
                .resultData("{\"message\": \"Awaiting manual review\"}")
                .build();
    }

    @Override
    public VerificationResult checkLiveness(String userId, byte[] selfieImage) {
        log.info("Manual liveness check requested for user: {}", userId);

        return VerificationResult.builder()
                .status(VerificationStatus.PENDING)
                .providerReference("MANUAL-" + UUID.randomUUID().toString().substring(0, 8))
                .resultData("{\"message\": \"Awaiting manual liveness review\"}")
                .build();
    }

    @Override
    public ScreeningResult screenPEP(String fullName, String dateOfBirth, String country) {
        log.info("Manual PEP screening requested for: {} from {}", fullName, country);

        return ScreeningResult.builder()
                .status(ScreeningStatus.CLEAR)
                .matchScore(BigDecimal.ZERO)
                .matchDetails("{\"message\": \"Manual PEP screening pending review\"}")
                .build();
    }

    @Override
    public ScreeningResult screenSanctions(String fullName, String country) {
        log.info("Manual sanctions screening requested for: {} from {}", fullName, country);

        return ScreeningResult.builder()
                .status(ScreeningStatus.CLEAR)
                .matchScore(BigDecimal.ZERO)
                .matchDetails("{\"message\": \"Manual sanctions screening pending review\"}")
                .build();
    }
}

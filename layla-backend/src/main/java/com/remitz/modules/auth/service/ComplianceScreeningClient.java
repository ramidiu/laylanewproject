package com.remitz.modules.auth.service;

import com.remitz.common.enums.EntityType;
import com.remitz.modules.compliance.service.SanctionsScreeningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComplianceScreeningClient {

    private final SanctionsScreeningService sanctionsScreeningService;

    @Async
    public void screenCustomerAsync(Long userId, String fullName, String country, String dateOfBirth) {
        if (userId == null || fullName == null || fullName.isBlank()) {
            return;
        }
        try {
            sanctionsScreeningService.screen(fullName, country, dateOfBirth, EntityType.CUSTOMER, userId);
            log.info("Compliance screen completed for userId={}", userId);
        } catch (Exception e) {
            log.warn("Compliance screen failed for userId={}: {}", userId, e.getMessage());
        }
    }
}

package com.remitz.modules.user.service;

import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.CountryRiskTier;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.user.repository.CountryRiskTierRepository;
import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskScoringService {

    private static final int DEFAULT_COUNTRY_RISK_POINTS = 5;
    private static final Set<String> LOW_RISK_OCCUPATIONS = Set.of(
            "Employed", "Self-Employed", "Business Owner"
    );
    private static final Set<String> MEDIUM_RISK_OCCUPATIONS = Set.of(
            "Student", "Retired", "Homemaker"
    );

    private final UserRepository userRepository;
    private final CountryRiskTierRepository countryRiskTierRepository;

    @Transactional
    public Map<String, Object> calculateRiskScore(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // Compliance disabled: force MEDIUM for every user and persist.
        if (!"MEDIUM".equals(user.getRiskScore())) {
            user.setRiskScore("MEDIUM");
            user.setRiskPoints(5);
            userRepository.save(user);
        }
        Map<String, Object> forced = new LinkedHashMap<>();
        forced.put("riskScore", "MEDIUM");
        forced.put("riskPoints", 5);
        forced.put("overridden", false);
        forced.put("breakdown", Map.of("message", "Risk scoring disabled — all users are MEDIUM"));
        return forced;
    }

    private Map<String, Object> calculateRiskScoreDisabled(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        // If risk override is set, return that instead
        if (user.getRiskOverride() != null && !user.getRiskOverride().isBlank()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("riskScore", user.getRiskOverride());
            result.put("riskPoints", user.getRiskPoints() != null ? user.getRiskPoints() : 0);
            result.put("overridden", true);
            result.put("breakdown", Map.of("message", "Risk score overridden to " + user.getRiskOverride()));
            return result;
        }

        Map<String, Object> breakdown = new LinkedHashMap<>();

        // 1. Country risk
        int countryRiskPoints = DEFAULT_COUNTRY_RISK_POINTS;
        String countryCode = user.getCountryCode() != null ? user.getCountryCode() : user.getCountry();
        if (countryCode != null) {
            Optional<CountryRiskTier> countryRisk = countryRiskTierRepository.findByCountryCode(countryCode);
            if (countryRisk.isPresent()) {
                countryRiskPoints = countryRisk.get().getRiskPoints();
            }
        }
        breakdown.put("countryRisk", countryRiskPoints);

        // 2. KYC status
        int kycPoints;
        String kycTier = user.getKycTier() != null ? user.getKycTier().name() : "";
        if (kycTier.equals("TIER_2") || kycTier.equals("TIER_3")) {
            kycPoints = 0; // VERIFIED equivalent
        } else if (kycTier.equals("TIER_1")) {
            kycPoints = 2; // PENDING equivalent
        } else {
            kycPoints = 4;
        }
        breakdown.put("kycStatus", kycPoints);

        // 3. Occupation
        int occupationPoints;
        String occupation = user.getOccupation();
        if (occupation != null && LOW_RISK_OCCUPATIONS.contains(occupation)) {
            occupationPoints = 0;
        } else if (occupation != null && MEDIUM_RISK_OCCUPATIONS.contains(occupation)) {
            occupationPoints = 2;
        } else {
            occupationPoints = 3;
        }
        breakdown.put("occupation", occupationPoints);

        // 4. Account age
        int accountAgePoints;
        if (user.getCreatedAt() != null) {
            long daysSinceCreation = ChronoUnit.DAYS.between(user.getCreatedAt(), LocalDateTime.now());
            if (daysSinceCreation > 90) {
                accountAgePoints = 0;
            } else if (daysSinceCreation >= 31) {
                accountAgePoints = 1;
            } else {
                accountAgePoints = 2;
            }
        } else {
            accountAgePoints = 2;
        }
        breakdown.put("accountAge", accountAgePoints);

        // Calculate total
        // Thresholds: ≤4=LOW, ≤9=MEDIUM, >9=HIGH
        // UK users with no KYC and no occupation score ~7-9 → MEDIUM (expected for new unverified users)
        int totalPoints = countryRiskPoints + kycPoints + occupationPoints + accountAgePoints;
        String riskLevel;
        if (totalPoints <= 4) {
            riskLevel = "LOW";
        } else if (totalPoints <= 9) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "HIGH";
        }

        // Update user
        user.setRiskScore(riskLevel);
        user.setRiskPoints(totalPoints);
        userRepository.save(user);

        log.info("Risk score calculated for userId={}: {} (points={})", userId, riskLevel, totalPoints);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("riskScore", riskLevel);
        result.put("riskPoints", totalPoints);
        result.put("overridden", false);
        result.put("breakdown", breakdown);
        return result;
    }
}

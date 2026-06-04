package com.remitz.modules.compliance.service;

import com.remitz.common.enums.EntityType;
import com.remitz.common.enums.RiskLevel;
import com.remitz.modules.compliance.entity.RiskScoreEntity;
import com.remitz.modules.compliance.repository.RiskScoreRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceRiskScoringService {

    private static final List<String> HIGH_RISK_COUNTRIES = Arrays.asList(
            "IRN", "PRK", "SYR", "AFG", "YEM", "LBY", "SOM", "SDN", "VEN", "MMR");

    private final RiskScoreRepository riskScoreRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RiskScoreEntity calculateRiskScore(EntityType entityType, Long entityId,
                                               Map<String, Object> factors) {
        log.info("Calculating risk score for {}:{}", entityType, entityId);

        int totalScore = 0;
        int factorCount = 0;

        // Country risk factor (0-30 points)
        String country = getStringFactor(factors, "country");
        if (country != null) {
            factorCount++;
            if (HIGH_RISK_COUNTRIES.contains(country.toUpperCase())) {
                totalScore += 30;
            } else {
                totalScore += 5;
            }
        }

        // Transaction pattern factor (0-25 points)
        Integer transactionCount = getIntFactor(factors, "recentTransactionCount");
        if (transactionCount != null) {
            factorCount++;
            if (transactionCount > 20) {
                totalScore += 25;
            } else if (transactionCount > 10) {
                totalScore += 15;
            } else if (transactionCount > 5) {
                totalScore += 8;
            } else {
                totalScore += 2;
            }
        }

        // KYC tier factor (0-20 points)
        String kycTier = getStringFactor(factors, "kycTier");
        if (kycTier != null) {
            factorCount++;
            totalScore += switch (kycTier.toUpperCase()) {
                case "TIER_0" -> 20;
                case "TIER_1" -> 12;
                case "TIER_2" -> 5;
                case "TIER_3" -> 0;
                default -> 10;
            };
        }

        // Screening results factor (0-15 points)
        Boolean hasScreeningMatch = getBooleanFactor(factors, "hasScreeningMatch");
        if (hasScreeningMatch != null) {
            factorCount++;
            totalScore += hasScreeningMatch ? 15 : 0;
        }

        // Amount factor (0-10 points)
        Double transactionAmount = getDoubleFactor(factors, "transactionAmount");
        if (transactionAmount != null) {
            factorCount++;
            if (transactionAmount > 10000) {
                totalScore += 10;
            } else if (transactionAmount > 5000) {
                totalScore += 7;
            } else if (transactionAmount > 1000) {
                totalScore += 3;
            }
        }

        // Normalize score to 0-100
        int normalizedScore = factorCount > 0 ? Math.min(totalScore, 100) : 0;
        RiskLevel riskLevel = determineRiskLevel(normalizedScore);

        String factorsJson;
        try {
            factorsJson = objectMapper.writeValueAsString(factors);
        } catch (JsonProcessingException e) {
            factorsJson = "{}";
        }

        RiskScoreEntity riskScore = RiskScoreEntity.builder()
                .entityType(entityType)
                .entityId(entityId)
                .score(normalizedScore)
                .riskLevel(riskLevel)
                .factors(factorsJson)
                .calculatedAt(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(30))
                .build();

        riskScore = riskScoreRepository.save(riskScore);
        log.info("Risk score calculated for {}:{} = {} ({})", entityType, entityId,
                normalizedScore, riskLevel);

        return riskScore;
    }

    @Transactional(readOnly = true)
    public RiskScoreEntity getRiskScore(EntityType entityType, Long entityId) {
        return riskScoreRepository.findTopByEntityTypeAndEntityIdOrderByCalculatedAtDesc(entityType, entityId)
                .orElseThrow(() -> new RuntimeException(
                        "Risk score not found for " + entityType + ":" + entityId));
    }

    private RiskLevel determineRiskLevel(int score) {
        if (score <= 25) return RiskLevel.LOW;
        if (score <= 50) return RiskLevel.MEDIUM;
        if (score <= 75) return RiskLevel.HIGH;
        return RiskLevel.CRITICAL;
    }

    private String getStringFactor(Map<String, Object> factors, String key) {
        Object value = factors.get(key);
        return value instanceof String ? (String) value : null;
    }

    private Integer getIntFactor(Map<String, Object> factors, String key) {
        Object value = factors.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private Double getDoubleFactor(Map<String, Object> factors, String key) {
        Object value = factors.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private Boolean getBooleanFactor(Map<String, Object> factors, String key) {
        Object value = factors.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return null;
    }
}

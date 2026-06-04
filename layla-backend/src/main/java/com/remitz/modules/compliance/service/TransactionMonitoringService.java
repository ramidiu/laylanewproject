package com.remitz.modules.compliance.service;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import com.remitz.common.enums.MonitoringRuleType;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.entity.MonitoringRuleEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import com.remitz.modules.compliance.repository.MonitoringRuleRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Evaluates configurable monitoring rules (velocity, amount threshold,
 * structuring, corridor risk, etc.) against a single transaction and creates
 * {@link ComplianceAlertEntity} rows for each rule that fires.
 *
 * <p>This service holds the <b>rules that live inside compliance-service</b>.
 * Simpler inline rules that need direct access to the transactions table
 * (baseline anomaly, round-number, rapid-succession, new-beneficiary) are
 * implemented in {@code transaction-service/.../TransactionService} and call
 * the internal {@code /internal/compliance/alerts} endpoint when they fire.
 *
 * <h2>Rule configuration</h2>
 * Active rules are stored in the {@code monitoring_rules} table. Each row has
 * a {@code rule_type} (enum: VELOCITY, AMOUNT_THRESHOLD, STRUCTURING,
 * CORRIDOR_RISK, PATTERN, BASELINE_ANOMALY, ROUND_NUMBER, RAPID_SUCCESSION),
 * a JSON {@code parameters} blob, and a severity. The rule type determines
 * which {@code evaluateXxxRule} method is invoked; parameters let ops tune
 * thresholds without code changes.
 *
 * <h2>Adding a new rule type</h2>
 * 1. Add the value to {@link com.remitz.common.enums.MonitoringRuleType}.<br>
 * 2. Add a DB migration widening the {@code monitoring_rules.rule_type} ENUM.<br>
 * 3. Add a case to the {@code switch} in {@link #evaluateRule}.<br>
 * 4. Seed a default row in a Flyway migration so it's live on deploy.
 *
 * <h2>Alert creation</h2>
 * Each triggered rule produces one {@link ComplianceAlertEntity} with the
 * rule's severity, a human-readable description, and the rule's parameters
 * JSON copied into {@code details} for auditability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionMonitoringService {

    private final MonitoringRuleRepository monitoringRuleRepository;
    private final ComplianceAlertRepository complianceAlertRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public List<ComplianceAlertEntity> evaluateTransaction(Long transactionId, Long userId,
                                                            BigDecimal amount, String currency,
                                                            String receiverCountry) {
        log.info("Evaluating transaction {} for user {} - amount: {} {}, country: {}",
                transactionId, userId, amount, currency, receiverCountry);

        List<MonitoringRuleEntity> activeRules = monitoringRuleRepository.findByIsActiveTrue();
        List<ComplianceAlertEntity> alerts = new ArrayList<>();

        for (MonitoringRuleEntity rule : activeRules) {
            try {
                Map<String, Object> params = objectMapper.readValue(
                        rule.getParameters(), new TypeReference<>() {});

                boolean triggered = evaluateRule(rule.getRuleType(), params, transactionId,
                        userId, amount, currency, receiverCountry);

                if (triggered) {
                    ComplianceAlertEntity alert = ComplianceAlertEntity.builder()
                            .rule(rule)
                            .userId(userId)
                            .transactionId(transactionId)
                            .severity(rule.getSeverity())
                            .status(AlertStatus.OPEN)
                            .description(buildAlertDescription(rule, amount, currency, receiverCountry))
                            .details(rule.getParameters())
                            .build();

                    alerts.add(complianceAlertRepository.save(alert));
                    log.warn("Alert triggered: rule='{}', transaction={}, user={}",
                            rule.getRuleName(), transactionId, userId);
                }
            } catch (Exception e) {
                log.error("Error evaluating rule '{}' for transaction {}: {}",
                        rule.getRuleName(), transactionId, e.getMessage());
            }
        }

        if (alerts.isEmpty()) {
            log.info("Transaction {} passed all monitoring rules", transactionId);
        }

        return alerts;
    }

    private boolean evaluateRule(MonitoringRuleType ruleType, Map<String, Object> params,
                                  Long transactionId, Long userId, BigDecimal amount,
                                  String currency, String receiverCountry) {
        return switch (ruleType) {
            case VELOCITY -> evaluateVelocityRule(params, userId);
            case AMOUNT_THRESHOLD -> evaluateAmountThresholdRule(params, amount, currency);
            case STRUCTURING -> evaluateStructuringRule(params, userId, amount);
            case CORRIDOR_RISK -> evaluateCorridorRiskRule(params, receiverCountry);
            case PATTERN, BASELINE_ANOMALY, ROUND_NUMBER, RAPID_SUCCESSION -> false;
        };
    }

    private boolean evaluateVelocityRule(Map<String, Object> params, Long userId) {
        int maxTransactions = getIntParam(params, "maxTransactions", 5);
        // Count existing alerts for this user as a proxy for transaction frequency
        long recentCount = complianceAlertRepository.countByUserIdAndTransactionIdIsNotNull(userId);
        boolean triggered = recentCount >= maxTransactions;
        if (triggered) {
            log.debug("Velocity rule triggered for user {}: {} transactions (max: {})",
                    userId, recentCount, maxTransactions);
        }
        return triggered;
    }

    private boolean evaluateAmountThresholdRule(Map<String, Object> params,
                                                 BigDecimal amount, String currency) {
        BigDecimal threshold = getBigDecimalParam(params, "threshold", BigDecimal.valueOf(5000));
        String ruleCurrency = getStringParam(params, "currency", "GBP");

        if (!ruleCurrency.equalsIgnoreCase(currency)) {
            return false;
        }

        boolean triggered = amount.compareTo(threshold) >= 0;
        if (triggered) {
            log.debug("Amount threshold rule triggered: {} {} >= {} {}",
                    amount, currency, threshold, ruleCurrency);
        }
        return triggered;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateStructuringRule(Map<String, Object> params, Long userId,
                                             BigDecimal amount) {
        BigDecimal threshold = getBigDecimalParam(params, "threshold", BigDecimal.valueOf(5000));
        BigDecimal variance = getBigDecimalParam(params, "variance", BigDecimal.valueOf(500));
        int windowHours = getIntParam(params, "windowHours", 24);
        int requiredCount = getIntParam(params, "count", 3);

        BigDecimal lowerBound = threshold.subtract(variance);
        boolean nearThreshold = amount.compareTo(lowerBound) >= 0 && amount.compareTo(threshold) < 0;

        if (nearThreshold) {
            // Count transaction-linked alerts within the rolling time window (not all-time)
            java.time.LocalDateTime windowStart = java.time.LocalDateTime.now().minusHours(windowHours);
            long recentCount = complianceAlertRepository
                    .countByUserIdAndTransactionIdIsNotNullAndCreatedAtAfter(userId, windowStart);
            boolean triggered = recentCount >= requiredCount;
            if (triggered) {
                log.debug("Structuring rule triggered for user {}: {} near-threshold transactions in last {}h",
                        userId, recentCount, windowHours);
            }
            return triggered;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateCorridorRiskRule(Map<String, Object> params, String receiverCountry) {
        Object countriesObj = params.get("highRiskCountries");
        if (countriesObj instanceof List<?> countries) {
            boolean triggered = countries.stream()
                    .map(Object::toString)
                    .anyMatch(c -> c.equalsIgnoreCase(receiverCountry));
            if (triggered) {
                log.debug("Corridor risk rule triggered for country: {}", receiverCountry);
            }
            return triggered;
        }
        return false;
    }

    private String buildAlertDescription(MonitoringRuleEntity rule, BigDecimal amount,
                                          String currency, String receiverCountry) {
        return String.format("Rule '%s' triggered - Amount: %s %s, Destination: %s",
                rule.getRuleName(), amount, currency, receiverCountry);
    }

    private int getIntParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return defaultValue;
    }

    private BigDecimal getBigDecimalParam(Map<String, Object> params, String key, BigDecimal defaultValue) {
        Object value = params.get(key);
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return defaultValue;
    }

    private String getStringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value instanceof String str) {
            return str;
        }
        return defaultValue;
    }
}

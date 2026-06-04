package com.remitz.modules.fx.service;

import com.remitz.common.dto.CorridorFeeResponse;
import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.FeeType;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.fx.dto.CorridorFeeRequest;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.entity.CorridorFeeEntity;
import com.remitz.modules.fx.repository.CorridorFeeRepository;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorridorFeeService {

    private final CorridorFeeRepository corridorFeeRepository;
    private final CorridorRepository corridorRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Calculate the fee for a given corridor, delivery method, and amount.
     * Based on fee_type: FLAT returns flat_fee, PERCENTAGE returns amount * percentage_fee / 100,
     * TIERED parses tier_rules JSON.
     */
    public BigDecimal getFee(Long corridorId, DeliveryMethod method, BigDecimal amount) {
        CorridorFeeEntity fee = corridorFeeRepository
                .findByCorridorIdAndDeliveryMethodAndIsActiveTrue(corridorId, method)
                .orElse(null);

        if (fee == null) {
            log.debug("No fee config found for corridor={} method={}; returning zero", corridorId, method);
            return BigDecimal.ZERO;
        }

        return switch (fee.getFeeType()) {
            case FLAT -> fee.getFlatFee() != null ? fee.getFlatFee() : BigDecimal.ZERO;
            case PERCENTAGE -> {
                BigDecimal pct = fee.getPercentageFee() != null ? fee.getPercentageFee() : BigDecimal.ZERO;
                yield amount.multiply(pct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            }
            case TIERED -> calculateTieredFee(fee.getTierRules(), amount);
        };
    }

    @Transactional
    public CorridorFeeResponse addFee(Long corridorId, CorridorFeeRequest request) {
        CorridorEntity corridor = corridorRepository.findById(corridorId)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", "id", corridorId));

        CorridorFeeEntity entity = CorridorFeeEntity.builder()
                .corridor(corridor)
                .deliveryMethod(request.getDeliveryMethod())
                .feeType(request.getFeeType())
                .flatFee(request.getFlatFee() != null ? request.getFlatFee() : BigDecimal.ZERO)
                .percentageFee(request.getPercentageFee() != null ? request.getPercentageFee() : BigDecimal.ZERO)
                .tierRules(request.getTierRules())
                .currency(request.getCurrency() != null ? request.getCurrency() : "GBP")
                .isActive(true)
                .build();

        entity = corridorFeeRepository.save(entity);
        log.info("Added fee id={} for corridor={}", entity.getId(), corridorId);
        auditFeeChange("corridor_fee." + corridorId + "." + request.getDeliveryMethod(),
                null, feeDescription(entity), request.getUpdatedBy());
        return mapToResponse(entity);
    }

    @Transactional
    public CorridorFeeResponse updateFee(Long feeId, CorridorFeeRequest request) {
        CorridorFeeEntity entity = corridorFeeRepository.findById(feeId)
                .orElseThrow(() -> new ResourceNotFoundException("CorridorFee", "id", feeId));

        String oldDesc = feeDescription(entity);
        if (request.getDeliveryMethod() != null) entity.setDeliveryMethod(request.getDeliveryMethod());
        if (request.getFeeType() != null) entity.setFeeType(request.getFeeType());
        if (request.getFlatFee() != null) entity.setFlatFee(request.getFlatFee());
        if (request.getPercentageFee() != null) entity.setPercentageFee(request.getPercentageFee());
        if (request.getTierRules() != null) entity.setTierRules(request.getTierRules());
        if (request.getCurrency() != null) entity.setCurrency(request.getCurrency());

        entity = corridorFeeRepository.save(entity);
        log.info("Updated fee id={}", entity.getId());
        auditFeeChange("corridor_fee." + entity.getCorridor().getId() + "." + entity.getDeliveryMethod(),
                oldDesc, feeDescription(entity), request.getUpdatedBy());
        return mapToResponse(entity);
    }

    private BigDecimal calculateTieredFee(String tierRulesJson, BigDecimal amount) {
        if (tierRulesJson == null || tierRulesJson.isBlank()) {
            return BigDecimal.ZERO;
        }

        try {
            // Format: [{"minAmount": 0, "maxAmount": 5000, "flatFee": 4}, {"minAmount": 5000, "maxAmount": null, "percentageFee": 1.0}]
            List<Map<String, Object>> tiers = objectMapper.readValue(tierRulesJson,
                    new TypeReference<List<Map<String, Object>>>() {});

            for (Map<String, Object> tier : tiers) {
                BigDecimal min = new BigDecimal(tier.getOrDefault("minAmount", tier.getOrDefault("min", "0")).toString());
                Object maxObj = tier.get("maxAmount") != null ? tier.get("maxAmount") : tier.get("max");
                BigDecimal max = (maxObj == null || "null".equals(maxObj.toString()))
                        ? new BigDecimal("999999999")
                        : new BigDecimal(maxObj.toString());

                if (amount.compareTo(min) >= 0 && amount.compareTo(max) < 0) {
                    // Check for flatFee first, then percentageFee, then legacy "fee"
                    Object flatFee = tier.get("flatFee") != null ? tier.get("flatFee") : tier.get("fee");
                    Object pctFee = tier.get("percentageFee");

                    if (pctFee != null && !pctFee.toString().equals("0") && !pctFee.toString().equals("null")) {
                        BigDecimal pct = new BigDecimal(pctFee.toString());
                        return amount.multiply(pct).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                    } else if (flatFee != null) {
                        return new BigDecimal(flatFee.toString());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse tier rules JSON: {}", e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    public List<CorridorFeeResponse> getAllFees() {
        return corridorFeeRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<CorridorFeeResponse> getFeesByCorridorId(Long corridorId) {
        return corridorFeeRepository.findByCorridorId(corridorId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void deleteFee(Long feeId) {
        deleteFee(feeId, null);
    }

    @Transactional
    public void deleteFee(Long feeId, String actor) {
        CorridorFeeEntity entity = corridorFeeRepository.findById(feeId)
                .orElseThrow(() -> new ResourceNotFoundException("CorridorFee", "id", feeId));
        String oldDesc = feeDescription(entity);
        String configKey = "corridor_fee." + entity.getCorridor().getId() + "." + entity.getDeliveryMethod();
        corridorFeeRepository.delete(entity);
        log.info("Deleted fee id={}", feeId);
        auditFeeChange(configKey, oldDesc, null, actor);
    }

    private String feeDescription(CorridorFeeEntity e) {
        if (e.getFeeType() == null) return "unknown";
        return switch (e.getFeeType()) {
            case FLAT -> e.getCurrency() + " " + e.getFlatFee();
            case PERCENTAGE -> e.getPercentageFee() + "%";
            case TIERED -> "tiered";
        };
    }

    private void auditFeeChange(String configKey, String oldValue, String newValue, String actor) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO system_config_audit (config_key, old_value, new_value, changed_by, change_source) VALUES (?,?,?,?,?)",
                    configKey,
                    oldValue,
                    newValue,
                    actor != null && !actor.isBlank() ? actor : "ADMIN",
                    "API");
        } catch (Exception e) {
            log.warn("Failed to write fee audit record for {}: {}", configKey, e.getMessage());
        }
    }

    private CorridorFeeResponse mapToResponse(CorridorFeeEntity entity) {
        return CorridorFeeResponse.builder()
                .id(entity.getId())
                .corridorId(entity.getCorridor().getId())
                .deliveryMethod(entity.getDeliveryMethod())
                .feeType(entity.getFeeType())
                .flatFee(entity.getFlatFee())
                .percentageFee(entity.getPercentageFee())
                .tierRules(entity.getTierRules())
                .currency(entity.getCurrency())
                .isActive(entity.getIsActive())
                .build();
    }
}

package com.remitz.modules.fx.service;

import com.remitz.common.dto.CorridorResponse;
import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.RiskLevel;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.fx.dto.CorridorCreateRequest;
import com.remitz.modules.fx.dto.CorridorLimitsResponse;
import com.remitz.modules.fx.dto.CorridorUpdateRequest;
import com.remitz.modules.fx.dto.DeliveryMethodResponse;
import com.remitz.modules.fx.entity.CorridorDeliveryMethodEntity;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.repository.CorridorDeliveryMethodRepository;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.remitz.modules.transaction.repository.PaymentMethodRepository;
import com.remitz.modules.transaction.repository.PayoutTypeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorridorService {

    private final CorridorRepository corridorRepository;
    private final CorridorDeliveryMethodRepository deliveryMethodRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PayoutTypeRepository payoutTypeRepository;

    public List<CorridorResponse> getActiveCorridors() {
        Set<String> activeSendCountries = expandCountryCodes(paymentMethodRepository.findActiveCountryCodes());
        Set<String> activeReceiveCountries = expandCountryCodes(payoutTypeRepository.findActiveCountryCodes());
        return corridorRepository.findByIsActiveTrue().stream()
                .filter(c -> activeSendCountries.contains(c.getSendCountry()))
                .filter(c -> activeReceiveCountries.contains(c.getReceiveCountry()))
                .map(this::mapToResponse)
                .toList();
    }

    /** Build a set containing each code in both alpha-2 and alpha-3 form, so we match regardless of how the corridor row stores it. */
    private Set<String> expandCountryCodes(List<String> codes) {
        Set<String> out = new java.util.HashSet<>();
        for (String code : codes) {
            if (code == null) continue;
            String upper = code.toUpperCase();
            out.add(upper);
            String alpha3 = toAlpha3(upper);
            if (alpha3 != null && !alpha3.isEmpty()) out.add(alpha3);
        }
        return out;
    }

    /** Convert an alpha-2 country code (e.g. "GB") to alpha-3 ("GBR"). Returns the input unchanged if already alpha-3. */
    private String toAlpha3(String code) {
        if (code == null) return null;
        if (code.length() == 3) return code.toUpperCase();
        try {
            return new Locale("", code.toUpperCase()).getISO3Country();
        } catch (Exception e) {
            return null;
        }
    }

    public CorridorResponse getCorridorById(Long id) {
        CorridorEntity entity = corridorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", "id", id));
        return mapToResponse(entity);
    }

    public List<CorridorResponse> getAllCorridors() {
        return corridorRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public CorridorResponse createCorridor(CorridorCreateRequest request) {
        CorridorEntity entity = CorridorEntity.builder()
                .sendCountry(request.getSendCountry())
                .receiveCountry(request.getReceiveCountry())
                .sendCurrency(request.getSendCurrency())
                .receiveCurrency(request.getReceiveCurrency())
                .isActive(true)
                .minAmount(request.getMinAmount() != null ? request.getMinAmount() : BigDecimal.TEN)
                .maxAmount(request.getMaxAmount() != null ? request.getMaxAmount() : BigDecimal.valueOf(50000))
                .dailyLimit(request.getDailyLimit())
                .monthlyLimit(request.getMonthlyLimit())
                .requiredKycTier(request.getRequiredKycTier() != null ? request.getRequiredKycTier() : KycTier.TIER_0)
                .riskLevel(request.getRiskLevel() != null ? request.getRiskLevel() : RiskLevel.LOW)
                .build();

        entity = corridorRepository.save(entity);
        log.info("Created corridor id={} for {}/{}", entity.getId(),
                entity.getSendCurrency(), entity.getReceiveCurrency());
        return mapToResponse(entity);
    }

    @Transactional
    public CorridorResponse updateCorridor(Long id, CorridorUpdateRequest request) {
        CorridorEntity entity = corridorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", "id", id));

        if (request.getMinAmount() != null) entity.setMinAmount(request.getMinAmount());
        if (request.getMaxAmount() != null) entity.setMaxAmount(request.getMaxAmount());
        if (request.getDailyLimit() != null) entity.setDailyLimit(request.getDailyLimit());
        if (request.getMonthlyLimit() != null) entity.setMonthlyLimit(request.getMonthlyLimit());
        if (request.getRequiredKycTier() != null) entity.setRequiredKycTier(request.getRequiredKycTier());
        if (request.getRiskLevel() != null) entity.setRiskLevel(request.getRiskLevel());

        entity = corridorRepository.save(entity);
        log.info("Updated corridor id={}", entity.getId());
        return mapToResponse(entity);
    }

    @Transactional
    public CorridorResponse toggleCorridor(Long id) {
        CorridorEntity entity = corridorRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", "id", id));

        entity.setIsActive(!entity.getIsActive());
        entity = corridorRepository.save(entity);
        log.info("Toggled corridor id={} active={}", entity.getId(), entity.getIsActive());
        return mapToResponse(entity);
    }

    public List<DeliveryMethodResponse> getDeliveryMethods(Long corridorId) {
        // Verify corridor exists
        corridorRepository.findById(corridorId)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", "id", corridorId));

        List<CorridorDeliveryMethodEntity> methods = deliveryMethodRepository
                .findByCorridorIdAndIsActiveTrue(corridorId);

        return methods.stream()
                .map(m -> DeliveryMethodResponse.builder()
                        .id(m.getId())
                        .deliveryMethod(m.getDeliveryMethod())
                        .payoutPartnerId(m.getPayoutPartnerId())
                        .isActive(m.getIsActive())
                        .processingTimeMinutes(m.getProcessingTimeMinutes())
                        .build())
                .toList();
    }

    public CorridorLimitsResponse getCorridorLimits(Long corridorId, KycTier tier) {
        CorridorEntity entity = corridorRepository.findById(corridorId)
                .orElseThrow(() -> new ResourceNotFoundException("Corridor", "id", corridorId));

        return CorridorLimitsResponse.builder()
                .corridorId(entity.getId())
                .kycTier(tier != null ? tier : entity.getRequiredKycTier())
                .minAmount(entity.getMinAmount())
                .maxAmount(entity.getMaxAmount())
                .dailyLimit(entity.getDailyLimit())
                .monthlyLimit(entity.getMonthlyLimit())
                .build();
    }

    private CorridorResponse mapToResponse(CorridorEntity entity) {
        List<DeliveryMethod> methods = null;
        try {
            List<CorridorDeliveryMethodEntity> deliveryMethods = deliveryMethodRepository
                    .findByCorridorIdAndIsActiveTrue(entity.getId());
            if (!deliveryMethods.isEmpty()) {
                methods = deliveryMethods.stream()
                        .map(CorridorDeliveryMethodEntity::getDeliveryMethod)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.debug("Could not load delivery methods for corridor {}: {}", entity.getId(), e.getMessage());
        }

        return CorridorResponse.builder()
                .id(entity.getId())
                .sendCountry(entity.getSendCountry())
                .receiveCountry(entity.getReceiveCountry())
                .sendCurrency(entity.getSendCurrency())
                .receiveCurrency(entity.getReceiveCurrency())
                .isActive(entity.getIsActive())
                .minAmount(entity.getMinAmount())
                .maxAmount(entity.getMaxAmount())
                .requiredKycTier(entity.getRequiredKycTier())
                .riskLevel(entity.getRiskLevel())
                .deliveryMethods(methods)
                .build();
    }
}

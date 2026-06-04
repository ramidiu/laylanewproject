package com.remitz.modules.fx.service;

import com.remitz.common.dto.MarginConfigRequest;
import com.remitz.common.dto.MarginConfigResponse;
import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.KycTier;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.fx.entity.FxMarginEntity;
import com.remitz.modules.fx.repository.FxMarginRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FxMarginService {

    private final FxMarginRepository fxMarginRepository;

    /**
     * Get the applicable margin with priority cascade:
     * 1. Exact match: currency pair + delivery_method + customer_tier
     * 2. currency pair + delivery_method (any tier)
     * 3. currency pair only (any method, any tier)
     * 4. Global default (zero margin)
     */
    public FxMarginEntity getApplicableMargin(String sendCurrency, String receiveCurrency,
                                               DeliveryMethod method, KycTier tier) {
        // Priority 1: Exact match
        if (method != null && tier != null) {
            Optional<FxMarginEntity> exact = fxMarginRepository
                    .findBySendCurrencyAndReceiveCurrencyAndDeliveryMethodAndCustomerTierAndIsActiveTrue(
                            sendCurrency, receiveCurrency, method, tier);
            if (exact.isPresent()) {
                log.debug("Margin match: exact (pair+method+tier) for {}/{}", sendCurrency, receiveCurrency);
                return exact.get();
            }
        }

        // Priority 2: currency pair + delivery_method (any tier)
        if (method != null) {
            Optional<FxMarginEntity> withMethod = fxMarginRepository
                    .findBySendCurrencyAndReceiveCurrencyAndDeliveryMethodAndCustomerTierIsNullAndIsActiveTrue(
                            sendCurrency, receiveCurrency, method);
            if (withMethod.isPresent()) {
                log.debug("Margin match: pair+method for {}/{}", sendCurrency, receiveCurrency);
                return withMethod.get();
            }
        }

        // Priority 3: currency pair only
        Optional<FxMarginEntity> pairOnly = fxMarginRepository
                .findFirstBySendCurrencyAndReceiveCurrencyAndDeliveryMethodIsNullAndCustomerTierIsNullAndIsActiveTrue(
                        sendCurrency, receiveCurrency);
        if (pairOnly.isPresent()) {
            log.debug("Margin match: pair-only for {}/{}", sendCurrency, receiveCurrency);
            return pairOnly.get();
        }

        // Priority 4: Global default - return a zero-margin entity
        log.debug("No margin config found for {}/{}; using zero default", sendCurrency, receiveCurrency);
        return FxMarginEntity.builder()
                .sendCurrency(sendCurrency)
                .receiveCurrency(receiveCurrency)
                .marginPercentage(BigDecimal.ZERO)
                .marginFixed(BigDecimal.ZERO)
                .isActive(true)
                .build();
    }

    @Transactional
    public MarginConfigResponse createMargin(MarginConfigRequest request) {
        FxMarginEntity entity = FxMarginEntity.builder()
                .sendCurrency(request.getSendCurrency())
                .receiveCurrency(request.getReceiveCurrency())
                .deliveryMethod(request.getDeliveryMethod())
                .marginPercentage(request.getMarginPercentage() != null ? request.getMarginPercentage() : BigDecimal.ZERO)
                .marginFixed(request.getMarginFixed() != null ? request.getMarginFixed() : BigDecimal.ZERO)
                .customerTier(request.getCustomerTier() != null ? KycTier.valueOf(request.getCustomerTier()) : null)
                .minAmount(request.getMinAmount())
                .maxAmount(request.getMaxAmount())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        entity = fxMarginRepository.save(entity);
        log.info("Created margin config id={} for {}/{}", entity.getId(),
                entity.getSendCurrency(), entity.getReceiveCurrency());
        return mapToResponse(entity);
    }

    @Transactional
    public MarginConfigResponse updateMargin(Long id, MarginConfigRequest request) {
        FxMarginEntity entity = fxMarginRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("FxMargin", "id", id));

        if (request.getSendCurrency() != null) entity.setSendCurrency(request.getSendCurrency());
        if (request.getReceiveCurrency() != null) entity.setReceiveCurrency(request.getReceiveCurrency());
        if (request.getDeliveryMethod() != null) entity.setDeliveryMethod(request.getDeliveryMethod());
        if (request.getMarginPercentage() != null) entity.setMarginPercentage(request.getMarginPercentage());
        if (request.getMarginFixed() != null) entity.setMarginFixed(request.getMarginFixed());
        if (request.getCustomerTier() != null) entity.setCustomerTier(KycTier.valueOf(request.getCustomerTier()));
        if (request.getMinAmount() != null) entity.setMinAmount(request.getMinAmount());
        if (request.getMaxAmount() != null) entity.setMaxAmount(request.getMaxAmount());
        if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());

        entity = fxMarginRepository.save(entity);
        log.info("Updated margin config id={}", entity.getId());
        return mapToResponse(entity);
    }

    public List<MarginConfigResponse> listMargins() {
        return fxMarginRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    private MarginConfigResponse mapToResponse(FxMarginEntity entity) {
        return MarginConfigResponse.builder()
                .id(entity.getId())
                .sendCurrency(entity.getSendCurrency())
                .receiveCurrency(entity.getReceiveCurrency())
                .deliveryMethod(entity.getDeliveryMethod())
                .marginPercentage(entity.getMarginPercentage())
                .marginFixed(entity.getMarginFixed())
                .customerTier(entity.getCustomerTier() != null ? entity.getCustomerTier().name() : null)
                .minAmount(entity.getMinAmount())
                .maxAmount(entity.getMaxAmount())
                .isActive(entity.getIsActive())
                .build();
    }
}

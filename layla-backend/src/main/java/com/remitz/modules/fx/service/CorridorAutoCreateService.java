package com.remitz.modules.fx.service;

import com.remitz.common.dto.CorridorResponse;
import com.remitz.common.enums.*;
import com.remitz.modules.fx.dto.CorridorAutoCreateRequest;
import com.remitz.modules.fx.entity.*;
import com.remitz.modules.fx.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CorridorAutoCreateService {

    private final CorridorRepository corridorRepository;
    private final CorridorDeliveryMethodRepository corridorDeliveryMethodRepository;
    private final FxRateHistoryRepository fxRateHistoryRepository;
    private final FxMarginRepository fxMarginRepository;
    private final CorridorFeeRepository corridorFeeRepository;

    @Transactional
    public CorridorResponse autoCreateCorridor(CorridorAutoCreateRequest request) {
        String sendCurrency = request.getSendCurrency();
        String receiveCurrency = request.getReceiveCurrency();

        // Skip if send and receive currency are the same
        if (sendCurrency.equals(receiveCurrency)) {
            log.debug("Skipping auto-create for same currency pair: {}/{}", sendCurrency, receiveCurrency);
            return null;
        }

        // Check if corridor already exists
        Optional<CorridorEntity> existing = corridorRepository.findBySendCurrencyAndReceiveCurrency(
                sendCurrency, receiveCurrency);
        if (existing.isPresent()) {
            log.debug("Corridor already exists for {}/{}: id={}", sendCurrency, receiveCurrency,
                    existing.get().getId());
            return mapToResponse(existing.get());
        }

        // Create the corridor with defaults
        CorridorEntity corridor = CorridorEntity.builder()
                .sendCountry(request.getSendCountry())
                .receiveCountry(request.getReceiveCountry())
                .sendCurrency(sendCurrency)
                .receiveCurrency(receiveCurrency)
                .isActive(true)
                .minAmount(BigDecimal.TEN)
                .maxAmount(BigDecimal.valueOf(50000))
                .dailyLimit(BigDecimal.valueOf(100000))
                .monthlyLimit(BigDecimal.valueOf(500000))
                .requiredKycTier(KycTier.TIER_0)
                .riskLevel(RiskLevel.LOW)
                .build();

        corridor = corridorRepository.save(corridor);
        log.info("Auto-created corridor id={} for {}/{}", corridor.getId(), sendCurrency, receiveCurrency);

        // Seed FX rate if none exists
        seedFxRate(sendCurrency, receiveCurrency);

        // Create default margin config if none exists
        seedDefaultMargin(sendCurrency, receiveCurrency);

        // Create default fee if none exists
        seedDefaultFee(corridor, sendCurrency);

        // Seed default delivery methods
        seedDeliveryMethods(corridor);

        return mapToResponse(corridor);
    }

    private void seedFxRate(String sendCurrency, String receiveCurrency) {
        Optional<FxRateHistoryEntity> existingRate = fxRateHistoryRepository
                .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(sendCurrency, receiveCurrency);
        if (existingRate.isPresent()) {
            log.debug("FX rate already exists for {}/{}", sendCurrency, receiveCurrency);
            return;
        }

        // Try to derive rate from existing rates via a common pivot currency
        BigDecimal derivedRate = tryDeriveRate(sendCurrency, receiveCurrency);

        FxRateHistoryEntity rateEntity = FxRateHistoryEntity.builder()
                .baseCurrency(sendCurrency)
                .targetCurrency(receiveCurrency)
                .rate(derivedRate != null ? derivedRate : BigDecimal.ONE)
                .source(derivedRate != null ? FxRateSource.MANUAL : FxRateSource.FALLBACK)
                .fetchedAt(LocalDateTime.now())
                .build();

        fxRateHistoryRepository.save(rateEntity);
        log.info("Seeded FX rate for {}/{} = {} (source={})", sendCurrency, receiveCurrency,
                rateEntity.getRate(), rateEntity.getSource());
    }

    /**
     * Try to derive a rate for sendCurrency/receiveCurrency from existing rates.
     * For example, if we have GBP/INR and GBP/AUD, then AUD/INR = (GBP/INR) / (GBP/AUD).
     * We try common pivot currencies: GBP, USD, EUR.
     */
    private BigDecimal tryDeriveRate(String sendCurrency, String receiveCurrency) {
        List<String> pivotCurrencies = List.of("GBP", "USD", "EUR");

        for (String pivot : pivotCurrencies) {
            if (pivot.equals(sendCurrency) || pivot.equals(receiveCurrency)) {
                continue;
            }

            // Check if we have pivot/receiveCurrency and pivot/sendCurrency
            Optional<FxRateHistoryEntity> pivotToReceive = fxRateHistoryRepository
                    .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(pivot, receiveCurrency);
            Optional<FxRateHistoryEntity> pivotToSend = fxRateHistoryRepository
                    .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(pivot, sendCurrency);

            if (pivotToReceive.isPresent() && pivotToSend.isPresent()) {
                BigDecimal rate = pivotToReceive.get().getRate()
                        .divide(pivotToSend.get().getRate(), 8, RoundingMode.HALF_UP);
                log.info("Derived rate {}/{} = {} via pivot {}", sendCurrency, receiveCurrency, rate, pivot);
                return rate;
            }

            // Also check if pivot is the sendCurrency itself (direct rate)
            if (pivot.equals(sendCurrency)) {
                Optional<FxRateHistoryEntity> directRate = fxRateHistoryRepository
                        .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(sendCurrency, receiveCurrency);
                if (directRate.isPresent()) {
                    return directRate.get().getRate();
                }
            }
        }

        // Also try: if sendCurrency is a pivot for something
        // Check direct reverse rate: receiveCurrency/sendCurrency exists -> invert
        Optional<FxRateHistoryEntity> reverseRate = fxRateHistoryRepository
                .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(receiveCurrency, sendCurrency);
        if (reverseRate.isPresent() && reverseRate.get().getRate().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal inverted = BigDecimal.ONE.divide(reverseRate.get().getRate(), 8, RoundingMode.HALF_UP);
            log.info("Derived rate {}/{} = {} via inverse", sendCurrency, receiveCurrency, inverted);
            return inverted;
        }

        log.warn("Could not derive rate for {}/{}; will use fallback rate 1.0", sendCurrency, receiveCurrency);
        return null;
    }

    private void seedDefaultMargin(String sendCurrency, String receiveCurrency) {
        Optional<FxMarginEntity> existing = fxMarginRepository
                .findFirstBySendCurrencyAndReceiveCurrencyAndDeliveryMethodIsNullAndCustomerTierIsNullAndIsActiveTrue(
                        sendCurrency, receiveCurrency);
        if (existing.isPresent()) {
            log.debug("Default margin already exists for {}/{}", sendCurrency, receiveCurrency);
            return;
        }

        FxMarginEntity margin = FxMarginEntity.builder()
                .sendCurrency(sendCurrency)
                .receiveCurrency(receiveCurrency)
                .marginPercentage(BigDecimal.ZERO)
                .marginFixed(BigDecimal.ZERO)
                .isActive(true)
                .build();

        fxMarginRepository.save(margin);
        log.info("Seeded default margin config for {}/{}", sendCurrency, receiveCurrency);
    }

    private void seedDefaultFee(CorridorEntity corridor, String sendCurrency) {
        Optional<CorridorFeeEntity> existing = corridorFeeRepository
                .findByCorridorIdAndDeliveryMethodAndIsActiveTrue(corridor.getId(), DeliveryMethod.BANK_DEPOSIT);
        if (existing.isPresent()) {
            log.debug("Default fee already exists for corridor id={}", corridor.getId());
            return;
        }

        CorridorFeeEntity fee = CorridorFeeEntity.builder()
                .corridor(corridor)
                .deliveryMethod(DeliveryMethod.BANK_DEPOSIT)
                .feeType(FeeType.FLAT)
                .flatFee(BigDecimal.valueOf(1.99))
                .percentageFee(BigDecimal.ZERO)
                .currency(sendCurrency)
                .isActive(true)
                .build();

        corridorFeeRepository.save(fee);
        log.info("Seeded default FLAT fee of 1.99 {} for corridor id={} BANK_DEPOSIT",
                sendCurrency, corridor.getId());
    }

    private void seedDeliveryMethods(CorridorEntity corridor) {
        // Add default delivery methods: BANK_DEPOSIT, MOBILE_WALLET, CASH_PICKUP
        DeliveryMethod[] methods = { DeliveryMethod.BANK_DEPOSIT, DeliveryMethod.MOBILE_WALLET, DeliveryMethod.CASH_PICKUP };
        int[] times = { 60, 30, 120 };
        for (int i = 0; i < methods.length; i++) {
            corridorDeliveryMethodRepository.save(CorridorDeliveryMethodEntity.builder()
                    .corridor(corridor)
                    .deliveryMethod(methods[i])
                    .isActive(true)
                    .processingTimeMinutes(times[i])
                    .build());
        }
        log.info("Seeded {} delivery methods for corridor {}", methods.length, corridor.getId());
    }

    private CorridorResponse mapToResponse(CorridorEntity entity) {
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
                .build();
    }
}

package com.remitz.modules.fx.service;

import com.remitz.common.dto.QuoteRequest;
import com.remitz.common.dto.QuoteResponse;
import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.RateLockExpiredException;
import com.remitz.modules.fx.config.FxProperties;
import com.remitz.modules.fx.dto.QuoteDetails;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.entity.FxMarginEntity;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.remitz.modules.payout.usi.service.UsiRateService;
import com.remitz.modules.user.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteService {

    private static final String QUOTE_CACHE_PREFIX = "fx:quote:";

    private final FxRateService fxRateService;
    private final FxMarginService fxMarginService;
    private final CorridorFeeService corridorFeeService;
    private final CorridorRateSlabService rateSlabService;
    private final CorridorRepository corridorRepository;
    private final UsiRateService usiRateService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FxProperties fxProperties;
    private final ObjectMapper objectMapper;
    // Code added by Naresh: System Controls Phase 5 — runtime override for quote TTL.
    private final SystemConfigService systemConfigService;

    public QuoteResponse generateQuote(QuoteRequest request) {
        if (request.getSendAmount() == null && request.getReceiveAmount() == null) {
            throw new RemitzException("Either sendAmount or receiveAmount must be provided", HttpStatus.BAD_REQUEST);
        }

        // Resolve corridor
        Long corridorId = request.getCorridorId();
        if (corridorId == null) {
            CorridorEntity corridor = corridorRepository
                    .findBySendCurrencyAndReceiveCurrencyAndIsActiveTrue(
                            request.getSendCurrency(), request.getReceiveCurrency())
                    .orElseThrow(() -> new RemitzException(
                            "No active corridor for " + request.getSendCurrency() + "/" + request.getReceiveCurrency(),
                            HttpStatus.BAD_REQUEST));
            corridorId = corridor.getId();
        }

        // Get mid-market rate (API or manual override)
        BigDecimal midRate = fxRateService.getRate(request.getSendCurrency(), request.getReceiveCurrency());

        // USI-rate-driven quoting (feature-flagged, default OFF). When ON and a cached USI rate exists
        // for this corridor + payout method, use it as the base the margin is applied to. Otherwise the
        // normal mid-market/manual rate is kept — a quote is NEVER blocked by a missing/stale USI rate.
        if (usiRateService.isRateDrivenQuotes()) {
            BigDecimal usiRate = usiRateService.getCachedUsiRate(
                    request.getSendCurrency(), request.getReceiveCurrency(), request.getDeliveryMethod());
            if (usiRate != null && usiRate.compareTo(BigDecimal.ZERO) > 0) {
                log.info("Quote base rate from USI: {} {}/{} method={} (mid was {})",
                        usiRate, request.getSendCurrency(), request.getReceiveCurrency(),
                        request.getDeliveryMethod(), midRate);
                midRate = usiRate;
            } else {
                log.debug("USI rate-driven quoting ON but no cached USI rate for {}/{} method={}; using mid {}",
                        request.getSendCurrency(), request.getReceiveCurrency(), request.getDeliveryMethod(), midRate);
            }
        }

        // Get applicable margin
        FxMarginEntity margin = fxMarginService.getApplicableMargin(
                request.getSendCurrency(), request.getReceiveCurrency(),
                request.getDeliveryMethod(), null);

        BigDecimal marginPercentage = margin.getMarginPercentage() != null ? margin.getMarginPercentage() : BigDecimal.ZERO;
        BigDecimal marginFixed = margin.getMarginFixed() != null ? margin.getMarginFixed() : BigDecimal.ZERO;

        // Calculate applied rate: midRate * (1 - marginPercentage/100) - marginFixed
        BigDecimal marginFactor = BigDecimal.ONE.subtract(
                marginPercentage.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP));
        BigDecimal appliedRate = midRate.multiply(marginFactor).subtract(marginFixed)
                .setScale(8, RoundingMode.HALF_UP);

        // Ensure applied rate is positive
        if (appliedRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RemitzException("Calculated rate is non-positive. Check margin configuration.",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Get fee
        DeliveryMethod deliveryMethod = request.getDeliveryMethod() != null
                ? request.getDeliveryMethod() : DeliveryMethod.BANK_DEPOSIT;

        // USI commission passed on to the customer: the per-transaction commission USI charges Layla
        // is added to the fee so it is deducted from the customer on every quote. Both the customer
        // FX calculator and the pay-in flow quote through here, so both pick it up identically.
        // Zero/absent for non-USI corridors (nothing cached).
        BigDecimal usiCommission = usiRateService.getCachedUsiCommission(
                request.getSendCurrency(), request.getReceiveCurrency(), request.getDeliveryMethod());
        BigDecimal usiCommissionFee = (usiCommission != null && usiCommission.signum() > 0)
                ? usiCommission : BigDecimal.ZERO;

        // Rate-slab lookup is scoped to the REQUESTED payment type (cash/bank/mobile).
        // Null when the caller didn't specify one, which falls back to corridor-wide slabs.
        String slabMethod = request.getDeliveryMethod() != null ? request.getDeliveryMethod().name() : null;

        BigDecimal sendAmount;
        BigDecimal receiveAmount;
        // When an amount-based rate slab matches the send amount, its rate REPLACES the
        // margin-based applied rate (receive = send * slabRate) and no FX margin is shown.
        boolean slabApplied = false;

        if (request.getSendAmount() != null) {
            sendAmount = request.getSendAmount();
            java.util.Optional<BigDecimal> slab = rateSlabService.getSlabRate(corridorId, sendAmount, slabMethod);
            if (slab.isPresent()) { appliedRate = slab.get(); slabApplied = true; }
            BigDecimal fee = corridorFeeService.getFee(corridorId, deliveryMethod, sendAmount).add(usiCommissionFee);
            // Fee-on-top: the FULL send amount is converted for the beneficiary and the fee is added
            // on top. So receive = sendAmount * rate and total-to-pay = sendAmount + fee.
            receiveAmount = sendAmount.multiply(appliedRate).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalCost = sendAmount.add(fee);

            return buildQuoteResponse(request, midRate, appliedRate,
                    slabApplied ? BigDecimal.ZERO : marginPercentage,
                    fee, usiCommissionFee, sendAmount, receiveAmount, totalCost, corridorId, deliveryMethod);
        } else {
            receiveAmount = request.getReceiveAmount();
            // Fee-on-top: the converted principal that produces the requested receive amount IS the
            // send amount, and the fee sits on top — so the customer pays (sendAmount + fee) in total.
            BigDecimal amountToConvert = receiveAmount.divide(appliedRate, 2, RoundingMode.HALF_UP);
            // The slab depends on the converted amount, so resolve it from that and re-derive.
            java.util.Optional<BigDecimal> slab = rateSlabService.getSlabRate(corridorId, amountToConvert, slabMethod);
            if (slab.isPresent()) {
                appliedRate = slab.get();
                slabApplied = true;
                amountToConvert = receiveAmount.divide(appliedRate, 2, RoundingMode.HALF_UP);
            }
            BigDecimal fee = corridorFeeService.getFee(corridorId, deliveryMethod, amountToConvert).add(usiCommissionFee);
            sendAmount = amountToConvert;
            BigDecimal totalCost = sendAmount.add(fee);

            return buildQuoteResponse(request, midRate, appliedRate,
                    slabApplied ? BigDecimal.ZERO : marginPercentage,
                    fee, usiCommissionFee, sendAmount, receiveAmount, totalCost, corridorId, deliveryMethod);
        }
    }

    public QuoteDetails validateQuote(String quoteId) {
        String cacheKey = QUOTE_CACHE_PREFIX + quoteId;
        Object cached = redisTemplate.opsForValue().get(cacheKey);

        if (cached == null) {
            throw new RateLockExpiredException("Quote " + quoteId + " has expired or does not exist");
        }

        try {
            if (cached instanceof QuoteDetails) {
                return (QuoteDetails) cached;
            }
            return objectMapper.convertValue(cached, QuoteDetails.class);
        } catch (Exception e) {
            log.error("Failed to deserialize quote details for {}: {}", quoteId, e.getMessage());
            throw new RemitzException("Failed to read quote details", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private QuoteResponse buildQuoteResponse(QuoteRequest request, BigDecimal midRate,
                                              BigDecimal appliedRate, BigDecimal marginPercentage,
                                              BigDecimal fee, BigDecimal usiCommission, BigDecimal sendAmount,
                                              BigDecimal receiveAmount, BigDecimal totalCost,
                                              Long corridorId, DeliveryMethod deliveryMethod) {
        String quoteId = UUID.randomUUID().toString();
        // Code added by Naresh: Read runtime control from system_config with safe fallback
        // to the existing app.fx.quote-lock-ttl-seconds property when the row is missing.
        int fallback = (int) fxProperties.getQuoteLockTtlSeconds();
        long ttl = systemConfigService.getInt("fx.quote_ttl_seconds", fallback);
        LocalDateTime rateLockedUntil = LocalDateTime.now().plusSeconds(ttl);

        // Store quote details in Redis
        QuoteDetails quoteDetails = QuoteDetails.builder()
                .quoteId(quoteId)
                .sendCurrency(request.getSendCurrency())
                .receiveCurrency(request.getReceiveCurrency())
                .sendAmount(sendAmount)
                .receiveAmount(receiveAmount)
                .exchangeRate(midRate)
                .appliedRate(appliedRate)
                .marginApplied(marginPercentage)
                .fee(fee)
                .usiCommission(usiCommission)
                .totalCost(totalCost)
                .corridorId(corridorId)
                .deliveryMethod(deliveryMethod.name())
                .rateLockedUntil(rateLockedUntil)
                .build();

        String cacheKey = QUOTE_CACHE_PREFIX + quoteId;
        try {
            redisTemplate.opsForValue().set(cacheKey, quoteDetails, ttl, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Failed to store quote in Redis: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            // Store as simple string fallback
            try {
                org.springframework.data.redis.core.StringRedisTemplate stringRedis =
                        new org.springframework.data.redis.core.StringRedisTemplate(redisTemplate.getConnectionFactory());
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                stringRedis.opsForValue().set(cacheKey, mapper.writeValueAsString(quoteDetails), ttl, TimeUnit.SECONDS);
                log.info("Stored quote in Redis as JSON string fallback");
            } catch (Exception e2) {
                log.error("Failed to store quote even as string: {}", e2.getMessage());
            }
        }

        log.info("Generated quote {} for {}/{} sendAmount={} receiveAmount={} rate={}",
                quoteId, request.getSendCurrency(), request.getReceiveCurrency(),
                sendAmount, receiveAmount, appliedRate);

        return QuoteResponse.builder()
                .quoteId(quoteId)
                .sendAmount(sendAmount)
                .receiveAmount(receiveAmount)
                .exchangeRate(midRate)
                .appliedRate(appliedRate)
                .marginApplied(marginPercentage)
                .fee(fee)
                .usiCommission(usiCommission)
                .totalCost(totalCost)
                .rateLockedUntil(rateLockedUntil)
                .expiresInSeconds(ttl)
                .build();
    }
}

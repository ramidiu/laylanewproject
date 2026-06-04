package com.remitz.modules.fx.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.dto.FxRateResponse;
import com.remitz.common.enums.FxRateSource;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.fx.config.FxProperties;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.entity.FxRateHistoryEntity;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.remitz.modules.fx.repository.FxRateHistoryRepository;
import com.remitz.modules.transaction.repository.PayoutTypeRepository;
import com.remitz.modules.user.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class FxRateService {

    private static final String RATE_CACHE_PREFIX = "fx:rate:";

    private static final String MANUAL_MODE_KEY  = "fx.manual_rate_mode";
    private static final String MANUAL_RATES_KEY  = "fx.manual_rates";

    private final RestTemplate restTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final FxRateHistoryRepository fxRateHistoryRepository;
    private final CorridorRepository corridorRepository;
    private final PayoutTypeRepository payoutTypeRepository;
    private final FxProperties fxProperties;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    public FxRateService(RedisTemplate<String, Object> redisTemplate,
                         FxRateHistoryRepository fxRateHistoryRepository,
                         CorridorRepository corridorRepository,
                         PayoutTypeRepository payoutTypeRepository,
                         FxProperties fxProperties,
                         SystemConfigService systemConfigService,
                         ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.redisTemplate = redisTemplate;
        this.fxRateHistoryRepository = fxRateHistoryRepository;
        this.corridorRepository = corridorRepository;
        this.payoutTypeRepository = payoutTypeRepository;
        this.fxProperties = fxProperties;
        this.systemConfigService = systemConfigService;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns all currencies needed for rate fetching based on active corridors.
     * Includes all receive currencies plus any non-GBP send currencies (so we can
     * cross-calculate rates for corridors like AUD→INR).
     */
    private List<String> getActiveCurrencies() {
        List<CorridorEntity> activeCorridors = corridorRepository.findByIsActiveTrue();
        Set<String> currencies = new LinkedHashSet<>();
        for (CorridorEntity corridor : activeCorridors) {
            if (corridor.getReceiveCurrency() != null) {
                currencies.add(corridor.getReceiveCurrency());
            }
            // Include non-GBP send currencies so GBP/<SEND> can be fetched for cross-calculation
            if (corridor.getSendCurrency() != null && !"GBP".equals(corridor.getSendCurrency())) {
                currencies.add(corridor.getSendCurrency());
            }
        }
        if (currencies.isEmpty()) {
            // Fallback to defaults so the scheduler doesn't fail on fresh setup
            return Arrays.asList("INR", "PKR", "NGN", "GHS", "PHP");
        }
        return new ArrayList<>(currencies);
    }

    @Scheduled(fixedRateString = "${app.fx.rate-fetch-interval-ms}")
    public void fetchRates() {
        String apiKey = fxProperties.getExchangeRatesApi().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Exchange Rates API key not configured; skipping rate fetch");
            return;
        }

        try {
            List<String> activeCurrencies = getActiveCurrencies();
            String baseUrl = fxProperties.getExchangeRatesApi().getBaseUrl();
            String symbols = "GBP," + String.join(",", activeCurrencies);

            // Free tier only supports EUR base, so fetch EUR rates and cross-calculate GBP
            String url = baseUrl + "/latest?access_key=" + apiKey + "&symbols=" + symbols;

            log.info("Fetching FX rates from exchangeratesapi.io (EUR base, symbols={})", symbols);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !Boolean.TRUE.equals(response.get("success"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> error = response != null ? (Map<String, Object>) response.get("error") : null;
                log.warn("Exchange Rates API error: {}", error);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> rates = (Map<String, Object>) response.get("rates");
            if (rates == null) {
                log.warn("No rates in API response");
                return;
            }

            BigDecimal eurToGbp = parseBigDecimal(rates.get("GBP"));
            if (eurToGbp == null || eurToGbp.compareTo(BigDecimal.ZERO) == 0) {
                log.warn("Could not get EUR/GBP rate");
                return;
            }

            // Step 1: Cross-calculate GBP/<currency> for all active currencies
            Map<String, BigDecimal> gbpRates = new LinkedHashMap<>();
            for (String target : activeCurrencies) {
                BigDecimal eurToTarget = parseBigDecimal(rates.get(target));
                if (eurToTarget == null) {
                    log.warn("Could not get EUR/{} rate", target);
                    continue;
                }
                BigDecimal gbpToTarget = eurToTarget.divide(eurToGbp, 8, RoundingMode.HALF_UP);
                gbpRates.put(target, gbpToTarget);
                cacheAndSaveRate("GBP", target, gbpToTarget, FxRateSource.OPEN_EXCHANGE_RATES);
                log.info("Rate GBP/{} = {}", target, gbpToTarget);
            }

            // Step 2: Proactively compute cross-rates for non-GBP send corridors (e.g. AUD→BDT)
            List<CorridorEntity> activeCorridors = corridorRepository.findByIsActiveTrue();
            for (CorridorEntity corridor : activeCorridors) {
                String sendCcy = corridor.getSendCurrency();
                String receiveCcy = corridor.getReceiveCurrency();
                if (sendCcy == null || receiveCcy == null || "GBP".equals(sendCcy)) continue;

                BigDecimal gbpToReceive = gbpRates.get(receiveCcy);
                BigDecimal gbpToSend = gbpRates.get(sendCcy);

                if (gbpToReceive == null || gbpToSend == null) {
                    log.warn("Cannot compute cross-rate {}/{}: missing GBP leg", sendCcy, receiveCcy);
                    continue;
                }
                if (gbpToSend.compareTo(BigDecimal.ZERO) == 0) {
                    log.warn("Cannot compute cross-rate {}/{}: GBP/{} is zero", sendCcy, receiveCcy, sendCcy);
                    continue;
                }

                BigDecimal crossRate = gbpToReceive.divide(gbpToSend, 8, RoundingMode.HALF_UP);
                cacheAndSaveRate(sendCcy, receiveCcy, crossRate, FxRateSource.OPEN_EXCHANGE_RATES);
                log.info("Cross-rate {}/{} = {} (GBP/{} / GBP/{})", sendCcy, receiveCcy, crossRate, receiveCcy, sendCcy);
            }

            log.info("FX rates fetched and cached successfully");

        } catch (Exception e) {
            log.warn("Failed to fetch FX rates: {}. Using cached rates.", e.getMessage());
        }
    }

    private void cacheAndSaveRate(String base, String target, BigDecimal rate, FxRateSource source) {
        String cacheKey = RATE_CACHE_PREFIX + base + ":" + target;
        redisTemplate.opsForValue().set(cacheKey, rate.toPlainString(),
                fxProperties.getRateCacheTtlSeconds(), TimeUnit.SECONDS);

        FxRateHistoryEntity history = FxRateHistoryEntity.builder()
                .baseCurrency(base)
                .targetCurrency(target)
                .rate(rate)
                .source(source)
                .fetchedAt(LocalDateTime.now())
                .build();
        fxRateHistoryRepository.save(history);
    }

    public BigDecimal getRate(String base, String target) {
        if (base.equals(target)) return BigDecimal.ONE;

        // Manual rate override takes priority when manual mode is enabled
        if (systemConfigService.getBoolean(MANUAL_MODE_KEY, false)) {
            Map<String, BigDecimal> manualRates = getManualRates();
            String pairKey = base + "_" + target;
            if (manualRates.containsKey(pairKey)) {
                BigDecimal manualRate = manualRates.get(pairKey);
                log.info("Using manual rate for {}/{}: {}", base, target, manualRate);
                return manualRate;
            }
        }

        String cacheKey = RATE_CACHE_PREFIX + base + ":" + target;

        Object cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                return new BigDecimal(cached.toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid cached rate for {}/{}: {}", base, target, cached);
            }
        }

        // Fallback: latest from database
        Optional<FxRateHistoryEntity> latest = fxRateHistoryRepository
                .findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(base, target);

        if (latest.isPresent()) {
            long ageSeconds = java.time.Duration.between(latest.get().getFetchedAt(), LocalDateTime.now()).getSeconds();
            if (ageSeconds > fxProperties.getStaleRateThresholdSeconds()) {
                log.warn("Rate for {}/{} is stale ({}s old)", base, target, ageSeconds);
            }
            redisTemplate.opsForValue().set(cacheKey, latest.get().getRate().toPlainString(),
                    fxProperties.getRateCacheTtlSeconds(), TimeUnit.SECONDS);
            return latest.get().getRate();
        }

        // Cross-rate via GBP: base/target = GBP/target / GBP/base
        if (!"GBP".equals(base)) {
            try {
                BigDecimal gbpToTarget = getRate("GBP", target);
                BigDecimal gbpToBase = getRate("GBP", base);
                if (gbpToBase.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal crossRate = gbpToTarget.divide(gbpToBase, 8, RoundingMode.HALF_UP);
                    log.info("Cross-rate {}/{} = {} (via GBP)", base, target, crossRate);
                    cacheAndSaveRate(base, target, crossRate, FxRateSource.OPEN_EXCHANGE_RATES);
                    return crossRate;
                }
            } catch (Exception e) {
                log.warn("Cross-rate calculation failed for {}/{}: {}", base, target, e.getMessage());
            }
        }

        throw new ResourceNotFoundException("FX Rate", "pair", base + "/" + target);
    }

    public List<FxRateResponse> getAllRates() {
        // Toggle: home.live_rates_enabled — superadmin can switch off the public Live Rates section.
        if (!systemConfigService.getBoolean("home.live_rates_enabled", true)) {
            return new ArrayList<>();
        }
        // Only include currencies that have at least one ACTIVE payout_type (i.e. truly receivable today).
        Set<String> activeReceiveCurrencies = new HashSet<>(payoutTypeRepository.findActiveCurrencies());
        List<FxRateResponse> responses = new ArrayList<>();
        for (String target : getActiveCurrencies()) {
            if (!activeReceiveCurrencies.isEmpty() && !activeReceiveCurrencies.contains(target)) {
                continue; // skip rates whose receive side isn't currently enabled
            }
            try {
                BigDecimal rate = getRate("GBP", target);
                responses.add(FxRateResponse.builder()
                        .baseCurrency("GBP")
                        .targetCurrency(target)
                        .rate(rate)
                        .source(FxRateSource.OPEN_EXCHANGE_RATES)
                        .fetchedAt(LocalDateTime.now())
                        .build());
            } catch (Exception e) {
                log.warn("Could not get rate for GBP/{}: {}", target, e.getMessage());
            }
        }
        return responses;
    }

    public List<FxRateResponse> getRateHistory(String base, String target,
                                                LocalDateTime from, LocalDateTime to) {
        List<FxRateHistoryEntity> history = fxRateHistoryRepository
                .findByBaseCurrencyAndTargetCurrencyAndFetchedAtBetween(base, target, from, to);

        return history.stream()
                .map(entity -> FxRateResponse.builder()
                        .baseCurrency(entity.getBaseCurrency())
                        .targetCurrency(entity.getTargetCurrency())
                        .rate(entity.getRate())
                        .source(entity.getSource())
                        .fetchedAt(entity.getFetchedAt())
                        .build())
                .toList();
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof Number) {
                return BigDecimal.valueOf(((Number) value).doubleValue());
            }
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ── Manual Rate Override ──────────────────────────────────────────────────

    public boolean isManualModeEnabled() {
        return systemConfigService.getBoolean(MANUAL_MODE_KEY, false);
    }

    public void setManualModeEnabled(boolean enabled) {
        systemConfigService.updateValue(MANUAL_MODE_KEY, String.valueOf(enabled), null, "SYSTEM");
        log.info("FX manual rate mode set to: {}", enabled);
        if (!enabled) {
            // Clear Redis so API rates are fetched fresh on next request
            Set<String> keys = redisTemplate.keys(RATE_CACHE_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) redisTemplate.delete(keys);
        }
    }

    public Map<String, BigDecimal> getManualRates() {
        String json = systemConfigService.getString(MANUAL_RATES_KEY, "{}");
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<Map<String, String>>() {});
            Map<String, BigDecimal> result = new LinkedHashMap<>();
            raw.forEach((k, v) -> {
                try { result.put(k, new BigDecimal(v)); } catch (Exception ignored) {}
            });
            return result;
        } catch (Exception e) {
            log.warn("Could not parse manual rates JSON: {}", json);
            return new LinkedHashMap<>();
        }
    }

    public void setManualRate(String base, String target, BigDecimal rate) {
        setManualRate(base, target, rate, "ADMIN");
    }

    public void setManualRate(String base, String target, BigDecimal rate, String actor) {
        Map<String, BigDecimal> rates = getManualRates();
        rates.put(base + "_" + target, rate);
        persistManualRates(rates, actor);
        // Invalidate Redis cache for this pair so the manual rate is used immediately
        redisTemplate.delete(RATE_CACHE_PREFIX + base + ":" + target);
        log.info("Manual rate set for {}/{}: {} by {}", base, target, rate, actor);
    }

    public void clearManualRate(String base, String target) {
        clearManualRate(base, target, "ADMIN");
    }

    public void clearManualRate(String base, String target, String actor) {
        Map<String, BigDecimal> rates = getManualRates();
        rates.remove(base + "_" + target);
        persistManualRates(rates, actor);
        redisTemplate.delete(RATE_CACHE_PREFIX + base + ":" + target);
        log.info("Manual rate cleared for {}/{} by {}", base, target, actor);
    }

    private void persistManualRates(Map<String, BigDecimal> rates, String actor) {
        try {
            Map<String, String> serialisable = new LinkedHashMap<>();
            rates.forEach((k, v) -> serialisable.put(k, v.toPlainString()));
            String json = objectMapper.writeValueAsString(serialisable);
            systemConfigService.updateValue(MANUAL_RATES_KEY, json, null, actor != null ? actor : "SYSTEM");
        } catch (Exception e) {
            throw new RuntimeException("Failed to persist manual rates: " + e.getMessage(), e);
        }
    }
}

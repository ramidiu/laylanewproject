package com.remitz.modules.payout.usi.service;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.modules.fx.entity.CorridorDeliveryMethodEntity;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.remitz.modules.payout.usi.dto.UsiChargesResult;
import com.remitz.modules.payout.usi.dto.UsiRateRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1 (READ-ONLY): surfaces the live USI rate/charges for active corridors via getCharges.
 * This does NOT change customer quotes and does NOT create anything on USI — it only reads.
 * Later phases will cache these on a schedule and (behind a flag) feed them into quoting.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsiRateService {

    private final CorridorRepository corridorRepository;
    private final UsiPayoutService usiPayoutService;

    /** In-memory cache of the latest USI rate per corridor+method, warmed by the FX page and the scheduler. */
    private final Map<String, BigDecimal> rateCache = new ConcurrentHashMap<>();

    /** In-memory cache of the latest USI per-transaction commission per corridor+method (same warmers as rateCache). */
    private final Map<String, BigDecimal> commissionCache = new ConcurrentHashMap<>();

    /** Master switch: when true, USI rates drive customer quotes (Phase 3). Default OFF — no pricing change. */
    @Value("${usi.rate-driven-quotes:false}")
    private boolean rateDrivenQuotes;

    private static String cacheKey(String send, String receive, DeliveryMethod method) {
        return send + "_" + receive + "_" + (method != null ? method.name() : "ANY");
    }

    /**
     * Cached USI rate for quoting: exact corridor+method first, then a method-agnostic fallback.
     * Returns null when nothing is cached (caller must fall back to the normal rate).
     */
    public BigDecimal getCachedUsiRate(String send, String receive, DeliveryMethod method) {
        BigDecimal r = rateCache.get(cacheKey(send, receive, method));
        if (r == null) r = rateCache.get(cacheKey(send, receive, null));
        return r;
    }

    /**
     * Cached USI per-transaction commission for quoting: exact corridor+method first, then a
     * method-agnostic fallback. Returns null when nothing is cached (caller treats as zero).
     */
    public BigDecimal getCachedUsiCommission(String send, String receive, DeliveryMethod method) {
        BigDecimal c = commissionCache.get(cacheKey(send, receive, method));
        if (c == null) c = commissionCache.get(cacheKey(send, receive, null));
        return c;
    }

    /** True when USI-rate-driven quoting is enabled (feature flag). */
    public boolean isRateDrivenQuotes() {
        return rateDrivenQuotes;
    }

    /**
     * Periodic cache refresh — only runs when the feature flag is ON, so USI isn't polled when unused.
     * Interval configurable via usi.rate-refresh-ms (default 10 min).
     */
    @Scheduled(fixedDelayString = "${usi.rate-refresh-ms:600000}", initialDelayString = "30000")
    public void refreshCache() {
        if (!rateDrivenQuotes) return;
        try {
            activeCorridorRates(BigDecimal.valueOf(100));   // side-effect: repopulates rateCache
        } catch (Exception e) {
            log.warn("USI rate cache refresh failed: {}", e.getMessage());
        }
    }

    /** One-off getCharges lookup for an explicit corridor + method (used by the single-lookup endpoint). */
    public UsiChargesResult single(String destinationCountry, String usiTransType,
                                   String sourceCurrency, String destCurrency, BigDecimal amount) {
        return usiPayoutService.getCharges(destinationCountry, usiTransType, sourceCurrency, destCurrency, amount);
    }

    /**
     * USI rate for every active corridor x active delivery method, priced at {@code sampleAmount}
     * on the SOURCE side. Failures are captured per-row (ok=false, error set) so one bad corridor
     * never aborts the sweep.
     */
    public List<UsiRateRow> activeCorridorRates(BigDecimal sampleAmount) {
        BigDecimal amount = sampleAmount != null ? sampleAmount : BigDecimal.valueOf(100);
        List<UsiRateRow> rows = new ArrayList<>();

        for (CorridorEntity c : corridorRepository.findActiveWithDeliveryMethods()) {
            // USI (Layla London) funds payouts in GBP, so only GBP-source corridors can be priced by USI.
            if (c.getSendCurrency() == null || !"GBP".equalsIgnoreCase(c.getSendCurrency())) continue;

            List<CorridorDeliveryMethodEntity> methods = c.getDeliveryMethods();
            if (methods == null || methods.isEmpty()) continue;

            for (CorridorDeliveryMethodEntity m : methods) {
                if (m.getIsActive() != null && !m.getIsActive()) continue;

                String transType = UsiPayoutServiceImpl.usiTransType(m.getDeliveryMethod());
                UsiChargesResult r = usiPayoutService.getCharges(
                        c.getReceiveCountry(), transType, c.getSendCurrency(), c.getReceiveCurrency(), amount);

                boolean ok = r != null && "SUCCESS".equalsIgnoreCase(r.getStatus());
                if (ok && r.getRate() != null && r.getRate().signum() > 0) {
                    rateCache.put(cacheKey(c.getSendCurrency(), c.getReceiveCurrency(), m.getDeliveryMethod()), r.getRate());
                    rateCache.put(cacheKey(c.getSendCurrency(), c.getReceiveCurrency(), null), r.getRate());
                    BigDecimal comm = r.getCommission() != null ? r.getCommission() : BigDecimal.ZERO;
                    commissionCache.put(cacheKey(c.getSendCurrency(), c.getReceiveCurrency(), m.getDeliveryMethod()), comm);
                    commissionCache.put(cacheKey(c.getSendCurrency(), c.getReceiveCurrency(), null), comm);
                }
                // Only surface corridors USI can actually price. Corridors USI doesn't handle
                // (e.g. Sudan/SDG, which is a live Layla corridor paid via a manual partner rather
                // than USI) return an error from getCharges — omit them so they don't clutter the
                // USI Live Rate -> Customer Rate table as ERROR rows.
                if (!(ok && r.getRate() != null && r.getRate().signum() > 0)) continue;
                rows.add(UsiRateRow.builder()
                        .corridor(c.getSendCurrency() + "->" + c.getReceiveCurrency())
                        .sendCurrency(c.getSendCurrency())
                        .receiveCurrency(c.getReceiveCurrency())
                        .receiveCountry(c.getReceiveCountry())
                        .deliveryMethod(m.getDeliveryMethod() != null ? m.getDeliveryMethod().name() : null)
                        .usiTransType(transType)
                        .payoutPartnerId(m.getPayoutPartnerId())
                        .ok(ok)
                        .usiRate(r != null ? r.getRate() : null)
                        .commission(r != null ? r.getCommission() : null)
                        .destAmount(r != null ? r.getDestAmount() : null)
                        .totalCharges(r != null ? r.getTotalCharges() : null)
                        .error(ok ? null : (r != null ? r.getError() : "no result"))
                        .build());
            }
        }
        return rows;
    }
}

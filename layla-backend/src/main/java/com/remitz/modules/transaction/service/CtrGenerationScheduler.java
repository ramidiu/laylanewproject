package com.remitz.modules.transaction.service;

import com.remitz.modules.compliance.entity.CtrReportEntity;
import com.remitz.modules.compliance.repository.CtrReportRepository;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Nightly CTR scan: finds any customer whose total send volume on the previous day
 * reached the CTR threshold (default: £10k GBP) and posts a CTR draft to
 * compliance-service for regulatory review/filing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CtrGenerationScheduler {

    private final TransactionRepository transactionRepository;
    private final CtrReportRepository ctrReportRepository;

    @Value("${app.tm.ctr.threshold:10000}")
    private BigDecimal threshold;

    @Value("${app.tm.ctr.currency:GBP}")
    private String currency;

    @Scheduled(cron = "${app.tm.ctr.cron:0 30 4 * * *}", zone = "UTC")
    public void nightlyCtrScan() {
        LocalDate reportDate = LocalDate.now().minusDays(1);
        LocalDateTime start = reportDate.atStartOfDay();
        LocalDateTime end = reportDate.plusDays(1).atStartOfDay();

        log.info("CTR scan starting for {} (threshold={} {})", reportDate, threshold, currency);

        List<TransactionEntity> candidates = transactionRepository.findCtrCandidates(
                threshold, currency, start, end);

        // Also aggregate per user for same-day totals that cross threshold cumulatively
        Map<Long, List<TransactionEntity>> perUserAll = new HashMap<>();
        List<TransactionEntity> dayTxns = transactionRepository.findCtrCandidates(
                BigDecimal.ZERO, currency, start, end);
        for (TransactionEntity t : dayTxns) {
            perUserAll.computeIfAbsent(t.getSenderId(), k -> new ArrayList<>()).add(t);
        }

        int submitted = 0;
        for (Map.Entry<Long, List<TransactionEntity>> e : perUserAll.entrySet()) {
            List<TransactionEntity> userTxns = e.getValue();
            BigDecimal total = userTxns.stream()
                    .map(TransactionEntity::getSendAmount)
                    .filter(java.util.Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (total.compareTo(threshold) < 0) continue;

            List<String> refs = new ArrayList<>();
            for (TransactionEntity t : userTxns) refs.add(t.getReferenceNumber());

            String userEmail = userTxns.get(0).getSenderEmail();
            String userName = userTxns.get(0).getSenderName();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("reportDate", reportDate.toString());
            body.put("userId", e.getKey());
            body.put("userEmail", userEmail);
            body.put("userName", userName);
            body.put("transactionCount", userTxns.size());
            body.put("totalAmount", total.toString());
            body.put("currency", currency);
            body.put("threshold", threshold.toString());
            body.put("transactionRefs", refs);

            try {
                java.time.LocalDate rDate = java.time.LocalDate.parse(body.get("reportDate").toString());
                Long uid = Long.valueOf(body.get("userId").toString());
                int txnCount = ((Number) body.get("transactionCount")).intValue();
                java.math.BigDecimal totalAmt = new java.math.BigDecimal(body.get("totalAmount").toString());
                String cur = body.get("currency").toString();
                java.math.BigDecimal thr = new java.math.BigDecimal(body.get("threshold").toString());
                String uEmail = body.get("userEmail") != null ? body.get("userEmail").toString() : null;
                String uName = body.get("userName") != null ? body.get("userName").toString() : null;
                String refsJson = body.get("transactionRefs") != null
                        ? new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(body.get("transactionRefs")) : null;

                CtrReportEntity existing = ctrReportRepository.findByReportDateAndUserId(rDate, uid).orElse(null);
                if (existing != null) {
                    existing.setTransactionCount(txnCount);
                    existing.setTotalAmount(totalAmt);
                    existing.setCurrency(cur);
                    existing.setThreshold(thr);
                    existing.setTransactionRefs(refsJson);
                    if (uEmail != null) existing.setUserEmail(uEmail);
                    if (uName != null) existing.setUserName(uName);
                    ctrReportRepository.save(existing);
                } else {
                    ctrReportRepository.save(CtrReportEntity.builder()
                            .reportDate(rDate).userId(uid).userEmail(uEmail).userName(uName)
                            .transactionCount(txnCount).totalAmount(totalAmt).currency(cur)
                            .threshold(thr).transactionRefs(refsJson)
                            .filingStatus(CtrReportEntity.FilingStatus.DRAFT).build());
                }
                submitted++;
            } catch (Exception ex) {
                log.warn("CTR submission failed for user {} on {}: {}",
                        e.getKey(), reportDate, ex.getMessage());
            }
        }
        log.info("CTR scan complete for {} — candidates with at-threshold txns={} userAggregatesSubmitted={}",
                reportDate, candidates.size(), submitted);
    }
}

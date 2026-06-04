package com.remitz.modules.fx.service;

import com.remitz.modules.fx.dto.CompetitorComparisonResponse;
import com.remitz.modules.fx.dto.CompetitorRateRequest;
import com.remitz.modules.fx.entity.CompetitorRateEntity;
import com.remitz.modules.fx.repository.CompetitorRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompetitorRateService {

    private final CompetitorRateRepository competitorRateRepository;
    private final FxRateService fxRateService;

    public void logRate(CompetitorRateRequest request) {
        CompetitorRateEntity entity = CompetitorRateEntity.builder()
                .competitorName(request.getCompetitorName())
                .sendCurrency(request.getSendCurrency())
                .receiveCurrency(request.getReceiveCurrency())
                .customerRate(request.getCustomerRate())
                .fee(request.getFee())
                .totalCostPerUnit(request.getTotalCostPerUnit())
                .build();

        competitorRateRepository.save(entity);
        log.info("Logged competitor rate: {} {}/{} = {}",
                request.getCompetitorName(), request.getSendCurrency(),
                request.getReceiveCurrency(), request.getCustomerRate());
    }

    public CompetitorComparisonResponse getComparison(String sendCurrency, String receiveCurrency) {
        List<CompetitorRateEntity> competitors = competitorRateRepository
                .findBySendCurrencyAndReceiveCurrency(sendCurrency, receiveCurrency);

        BigDecimal ourRate = null;
        try {
            ourRate = fxRateService.getRate(sendCurrency, receiveCurrency);
        } catch (Exception e) {
            log.warn("Could not get our rate for comparison: {}", e.getMessage());
        }

        List<CompetitorComparisonResponse.CompetitorEntry> entries = competitors.stream()
                .map(c -> CompetitorComparisonResponse.CompetitorEntry.builder()
                        .competitorName(c.getCompetitorName())
                        .customerRate(c.getCustomerRate())
                        .fee(c.getFee())
                        .totalCostPerUnit(c.getTotalCostPerUnit())
                        .capturedAt(c.getCapturedAt())
                        .build())
                .toList();

        return CompetitorComparisonResponse.builder()
                .sendCurrency(sendCurrency)
                .receiveCurrency(receiveCurrency)
                .ourRate(ourRate)
                .competitors(entries)
                .build();
    }
}

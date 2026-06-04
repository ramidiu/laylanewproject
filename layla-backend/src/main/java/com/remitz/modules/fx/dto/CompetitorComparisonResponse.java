package com.remitz.modules.fx.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompetitorComparisonResponse {

    private String sendCurrency;
    private String receiveCurrency;
    private BigDecimal ourRate;
    private List<CompetitorEntry> competitors;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompetitorEntry {
        private String competitorName;
        private BigDecimal customerRate;
        private BigDecimal fee;
        private BigDecimal totalCostPerUnit;
        private LocalDateTime capturedAt;
    }
}

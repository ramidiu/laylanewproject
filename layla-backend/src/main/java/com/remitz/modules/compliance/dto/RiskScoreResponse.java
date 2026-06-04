package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.EntityType;
import com.remitz.common.enums.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskScoreResponse {

    private Long id;
    private EntityType entityType;
    private Long entityId;
    private Integer score;
    private RiskLevel riskLevel;
    private String factors;
    private LocalDateTime calculatedAt;
    private LocalDateTime validUntil;
}

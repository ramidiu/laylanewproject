package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.MonitoringRuleType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringRuleResponse {

    private Long id;
    private String ruleName;
    private MonitoringRuleType ruleType;
    private String parameters;
    private AlertSeverity severity;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

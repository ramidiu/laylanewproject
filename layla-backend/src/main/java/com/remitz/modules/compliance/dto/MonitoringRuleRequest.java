package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.MonitoringRuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonitoringRuleRequest {

    @NotBlank(message = "Rule name is required")
    private String ruleName;

    @NotNull(message = "Rule type is required")
    private MonitoringRuleType ruleType;

    @NotBlank(message = "Parameters are required")
    private String parameters;

    @NotNull(message = "Severity is required")
    private AlertSeverity severity;

    private Boolean isActive;
}

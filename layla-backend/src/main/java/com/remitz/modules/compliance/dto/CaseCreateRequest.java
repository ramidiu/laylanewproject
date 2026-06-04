package com.remitz.modules.compliance.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CaseCreateRequest {

    @NotEmpty(message = "At least one alert ID is required")
    private List<Long> alertIds;

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Priority is required")
    private String priority;

    private Long assigneeId;

    private String summary;
}

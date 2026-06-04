package com.remitz.modules.compliance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDispositionRequest {

    @NotNull
    private Action action;

    private String reason;

    public enum Action {
        FALSE_POSITIVE,
        CONFIRMED_MATCH,
        ESCALATE
    }
}

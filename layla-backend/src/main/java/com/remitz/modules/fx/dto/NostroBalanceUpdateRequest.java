package com.remitz.modules.fx.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NostroBalanceUpdateRequest {

    @NotNull(message = "New balance is required")
    private BigDecimal newBalance;
}

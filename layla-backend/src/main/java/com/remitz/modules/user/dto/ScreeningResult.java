package com.remitz.modules.user.dto;

import com.remitz.common.enums.ScreeningStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningResult {

    private ScreeningStatus status;
    private BigDecimal matchScore;
    private String matchDetails;
}

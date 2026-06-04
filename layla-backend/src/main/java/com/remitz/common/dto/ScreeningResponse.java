package com.remitz.common.dto;

import com.remitz.common.enums.EntityType;
import com.remitz.common.enums.ScreeningListType;
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
public class ScreeningResponse {

    private Long id;
    private EntityType entityType;
    private Long entityId;
    private ScreeningListType listChecked;
    private BigDecimal matchScore;
    private ScreeningStatus status;
    private String matchDetails;
}

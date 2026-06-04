package com.remitz.common.dto;

import com.remitz.common.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionListRequest {

    private Integer page;
    private Integer size;
    private TransactionStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long corridorId;
    private String search;
    private String sortBy;
    private String sortDir;
}

package com.remitz.common.dto;

import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusHistoryResponse {

    private TransactionStatus fromStatus;
    private TransactionStatus toStatus;
    private ActorType actorType;
    private String reason;
    private LocalDateTime createdAt;
}

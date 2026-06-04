package com.remitz.modules.notification.dto;

import com.remitz.common.enums.NotificationChannel;
import com.remitz.common.enums.NotificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationLogResponse {

    private Long id;
    private Long userId;
    private Long transactionId;
    private String templateCode;
    private NotificationChannel channel;
    private String recipient;
    private NotificationStatus status;
    private Integer retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime deliveredAt;
    private String errorMessage;
    private LocalDateTime createdAt;
}

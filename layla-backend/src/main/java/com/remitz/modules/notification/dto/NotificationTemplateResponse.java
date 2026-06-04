package com.remitz.modules.notification.dto;

import com.remitz.common.enums.NotificationChannel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateResponse {

    private Long id;
    private String templateCode;
    private NotificationChannel channel;
    private String language;
    private String subject;
    private String bodyTemplate;
    private Boolean isActive;
    private LocalDateTime createdAt;
}

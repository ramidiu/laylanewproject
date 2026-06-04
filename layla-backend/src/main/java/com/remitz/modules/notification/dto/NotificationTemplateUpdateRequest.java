package com.remitz.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateUpdateRequest {

    private String subject;
    private String bodyTemplate;
    private Boolean isActive;
}

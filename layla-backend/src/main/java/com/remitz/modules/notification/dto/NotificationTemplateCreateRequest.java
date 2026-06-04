package com.remitz.modules.notification.dto;

import com.remitz.common.enums.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateCreateRequest {

    @NotBlank(message = "Template code is required")
    private String templateCode;

    @NotNull(message = "Channel is required")
    private NotificationChannel channel;

    private String language;

    private String subject;

    @NotBlank(message = "Body template is required")
    private String bodyTemplate;

    private Boolean isActive;
}

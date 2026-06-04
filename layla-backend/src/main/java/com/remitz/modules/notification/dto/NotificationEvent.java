package com.remitz.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private String templateCode;
    private Long userId;
    private String email;
    private String phone;
    private String language;
    private Long transactionId;
    private Map<String, String> variables;
}

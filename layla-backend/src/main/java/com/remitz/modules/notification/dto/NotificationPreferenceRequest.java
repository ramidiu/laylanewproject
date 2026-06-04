package com.remitz.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceRequest {
    private Boolean rateAlerts;
    private Boolean promotional;
    private Boolean transactionUpdates;
    private Boolean securityAlerts;
    private Boolean kycUpdates;
    private Boolean complianceAlerts;
    private Boolean systemNotifications;
    private Boolean emailEnabled;
}

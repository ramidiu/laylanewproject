package com.remitz.modules.notification.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationPreferenceEntity {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "rate_alerts", nullable = false)
    private Boolean rateAlerts;

    @Column(name = "promotional", nullable = false)
    private Boolean promotional;

    @Column(name = "transaction_updates", nullable = false)
    private Boolean transactionUpdates;

    @Column(name = "security_alerts", nullable = false)
    private Boolean securityAlerts;

    @Column(name = "kyc_updates", nullable = false)
    private Boolean kycUpdates;

    @Column(name = "compliance_alerts", nullable = false)
    private Boolean complianceAlerts;

    @Column(name = "system_notifications", nullable = false)
    private Boolean systemNotifications;

    @Column(name = "email_enabled", nullable = false)
    private Boolean emailEnabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.rateAlerts == null) this.rateAlerts = true;
        if (this.promotional == null) this.promotional = true;
        if (this.transactionUpdates == null) this.transactionUpdates = true;
        if (this.securityAlerts == null) this.securityAlerts = true;
        if (this.kycUpdates == null) this.kycUpdates = true;
        if (this.complianceAlerts == null) this.complianceAlerts = true;
        if (this.systemNotifications == null) this.systemNotifications = true;
        if (this.emailEnabled == null) this.emailEnabled = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

package com.remitz.modules.notification.service;

import com.remitz.modules.notification.dto.NotificationPreferenceRequest;
import com.remitz.modules.notification.dto.NotificationPreferenceResponse;
import com.remitz.modules.notification.entity.NotificationPreferenceEntity;
import com.remitz.modules.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    @Transactional
    public NotificationPreferenceEntity getOrCreate(Long userId) {
        return repository.findByUserId(userId).orElseGet(() -> {
            NotificationPreferenceEntity fresh = NotificationPreferenceEntity.builder()
                    .userId(userId)
                    .rateAlerts(true)
                    .promotional(true)
                    .transactionUpdates(true)
                    .securityAlerts(true)
                    .kycUpdates(true)
                    .complianceAlerts(true)
                    .systemNotifications(true)
                    .emailEnabled(true)
                    .build();
            return repository.save(fresh);
        });
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponse getPreferences(Long userId) {
        NotificationPreferenceEntity entity = repository.findByUserId(userId).orElse(null);
        if (entity == null) {
            return defaultResponse(userId);
        }
        return toResponse(entity);
    }

    @Transactional
    public NotificationPreferenceResponse updatePreferences(Long userId, NotificationPreferenceRequest request) {
        NotificationPreferenceEntity entity = getOrCreate(userId);
        if (request.getRateAlerts() != null) entity.setRateAlerts(request.getRateAlerts());
        if (request.getPromotional() != null) entity.setPromotional(request.getPromotional());
        if (request.getTransactionUpdates() != null) entity.setTransactionUpdates(request.getTransactionUpdates());
        if (request.getSecurityAlerts() != null) entity.setSecurityAlerts(request.getSecurityAlerts());
        if (request.getKycUpdates() != null) entity.setKycUpdates(request.getKycUpdates());
        if (request.getComplianceAlerts() != null) entity.setComplianceAlerts(request.getComplianceAlerts());
        if (request.getSystemNotifications() != null) entity.setSystemNotifications(request.getSystemNotifications());
        if (request.getEmailEnabled() != null) entity.setEmailEnabled(request.getEmailEnabled());
        NotificationPreferenceEntity saved = repository.save(entity);
        log.info("Notification preferences updated for userId={}", userId);
        return toResponse(saved);
    }

    /**
     * Used by the send path. Given a set of user ids and a notification type,
     * returns only those user ids who have that type enabled.
     * Users with no row in the preferences table are treated as all-enabled
     * (the default on first signup).
     */
    @Transactional(readOnly = true)
    public List<Long> filterUsersByType(Collection<Long> userIds, String type) {
        Map<Long, NotificationPreferenceEntity> byUser = new HashMap<>();
        for (NotificationPreferenceEntity p : repository.findByUserIdIn(userIds)) {
            byUser.put(p.getUserId(), p);
        }
        return userIds.stream()
                .filter(id -> {
                    NotificationPreferenceEntity p = byUser.get(id);
                    if (p == null) return true; // default = opted-in
                    return isTypeEnabled(p, type);
                })
                .toList();
    }

    public boolean isTypeEnabled(NotificationPreferenceEntity p, String type) {
        if (type == null) return true;
        switch (type.toUpperCase()) {
            case "RATE_ALERT":
            case "RATE_ALERTS":
                return Boolean.TRUE.equals(p.getRateAlerts());
            case "PROMOTIONAL":
                return Boolean.TRUE.equals(p.getPromotional());
            case "TRANSACTION_UPDATE":
            case "TRANSACTION_UPDATES":
                return Boolean.TRUE.equals(p.getTransactionUpdates());
            case "SECURITY_ALERT":
            case "SECURITY_ALERTS":
                return Boolean.TRUE.equals(p.getSecurityAlerts());
            case "KYC_UPDATE":
            case "KYC_UPDATES":
                return Boolean.TRUE.equals(p.getKycUpdates());
            case "COMPLIANCE_ALERT":
            case "COMPLIANCE_ALERTS":
                return Boolean.TRUE.equals(p.getComplianceAlerts());
            case "SYSTEM":
                return Boolean.TRUE.equals(p.getSystemNotifications());
            default:
                return true;
        }
    }

    private NotificationPreferenceResponse defaultResponse(Long userId) {
        return NotificationPreferenceResponse.builder()
                .userId(userId)
                .rateAlerts(true).promotional(true).transactionUpdates(true)
                .securityAlerts(true).kycUpdates(true).complianceAlerts(true)
                .systemNotifications(true).emailEnabled(true)
                .build();
    }

    private NotificationPreferenceResponse toResponse(NotificationPreferenceEntity e) {
        return NotificationPreferenceResponse.builder()
                .userId(e.getUserId())
                .rateAlerts(e.getRateAlerts())
                .promotional(e.getPromotional())
                .transactionUpdates(e.getTransactionUpdates())
                .securityAlerts(e.getSecurityAlerts())
                .kycUpdates(e.getKycUpdates())
                .complianceAlerts(e.getComplianceAlerts())
                .systemNotifications(e.getSystemNotifications())
                .emailEnabled(e.getEmailEnabled())
                .build();
    }
}

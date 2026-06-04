package com.remitz.modules.notification.service;

import com.remitz.common.enums.NotificationChannel;
import com.remitz.common.enums.NotificationStatus;
import com.remitz.modules.notification.config.NotificationProperties;
import com.remitz.modules.notification.entity.NotificationLogEntity;
import com.remitz.modules.notification.entity.UserDeviceEntity;
import com.remitz.modules.notification.repository.NotificationLogRepository;
import com.remitz.modules.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final UserDeviceRepository userDeviceRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationProperties notificationProperties;

    public boolean sendPush(Long userId, String title, String body) {
        return sendPush(userId, title, body, null, null);
    }

    public boolean sendPush(Long userId, String title, String body, String templateCode, Long transactionId) {
        List<UserDeviceEntity> devices = userDeviceRepository.findByUserIdAndIsActiveTrue(userId);

        if (devices.isEmpty()) {
            log.debug("No active devices found for user: {}", userId);
            return false;
        }

        boolean anySent = false;

        for (UserDeviceEntity device : devices) {
            NotificationLogEntity logEntry = NotificationLogEntity.builder()
                    .userId(userId)
                    .transactionId(transactionId)
                    .templateCode(templateCode != null ? templateCode : "DIRECT")
                    .channel(NotificationChannel.PUSH)
                    .recipient(device.getDeviceToken())
                    .status(NotificationStatus.QUEUED)
                    .retryCount(0)
                    .build();
            logEntry = notificationLogRepository.save(logEntry);

            try {
                if (StringUtils.hasText(notificationProperties.getFcm().getCredentialsPath())) {
                    // FCM integration placeholder
                    // When FCM credentials are configured, send via Firebase Cloud Messaging
                    sendViaFcm(device, title, body);
                } else {
                    log.info("Push notification (FCM not configured) - userId: {}, device: {}, platform: {}, title: '{}', body: '{}'",
                            userId, device.getDeviceToken(), device.getPlatform(), title, body);
                }

                logEntry.setStatus(NotificationStatus.SENT);
                logEntry.setSentAt(LocalDateTime.now());
                notificationLogRepository.save(logEntry);
                anySent = true;

            } catch (Exception e) {
                log.error("Failed to send push notification to device: {} for user: {}: {}",
                        device.getDeviceToken(), userId, e.getMessage());
                logEntry.setStatus(NotificationStatus.FAILED);
                logEntry.setErrorMessage(e.getMessage());
                notificationLogRepository.save(logEntry);
            }
        }

        return anySent;
    }

    private void sendViaFcm(UserDeviceEntity device, String title, String body) {
        // FCM integration placeholder
        // Implementation would use Google Firebase Admin SDK to send push notifications
        log.info("FCM push notification sent to device: {} (platform: {}), title: '{}'",
                device.getDeviceToken(), device.getPlatform(), title);
    }
}

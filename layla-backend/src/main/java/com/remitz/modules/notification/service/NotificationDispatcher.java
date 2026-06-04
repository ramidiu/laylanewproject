package com.remitz.modules.notification.service;

import com.remitz.common.enums.NotificationChannel;
import com.remitz.modules.notification.entity.NotificationTemplateEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcher {

    private final TemplateService templateService;
    private final EmailService emailService;
    private final SmsService smsService;
    private final PushNotificationService pushNotificationService;
    private final InAppNotificationService inAppNotificationService;

    public void dispatch(String templateCode, Long userId, String email, String phone,
                         String language, Map<String, String> variables, Long transactionId) {
        log.info("Dispatching notification: templateCode={}, userId={}, transactionId={}",
                templateCode, userId, transactionId);

        // Send EMAIL notification
        try {
            NotificationTemplateEntity emailTemplate = templateService.resolveTemplate(
                    templateCode, NotificationChannel.EMAIL, language);
            if (emailTemplate != null && email != null) {
                String subject = templateService.renderSubject(emailTemplate, variables);
                String body = templateService.renderTemplate(emailTemplate, variables);
                emailService.sendEmail(email, subject, body, userId, templateCode, transactionId);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch EMAIL notification for template {}: {}", templateCode, e.getMessage());
        }

        // Create IN_APP notification
        try {
            NotificationTemplateEntity inAppTemplate = templateService.resolveTemplate(
                    templateCode, NotificationChannel.IN_APP, language);
            if (inAppTemplate != null && userId != null) {
                String title = templateService.renderSubject(inAppTemplate, variables);
                String body = templateService.renderTemplate(inAppTemplate, variables);
                String referenceType = transactionId != null ? "TRANSACTION" : null;
                inAppNotificationService.create(userId, title, body, templateCode, referenceType, transactionId);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch IN_APP notification for template {}: {}", templateCode, e.getMessage());
        }

        // Send SMS notification (if template exists)
        try {
            NotificationTemplateEntity smsTemplate = templateService.resolveTemplate(
                    templateCode, NotificationChannel.SMS, language);
            if (smsTemplate != null && phone != null) {
                String body = templateService.renderTemplate(smsTemplate, variables);
                smsService.sendSms(phone, body, userId, templateCode, transactionId);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch SMS notification for template {}: {}", templateCode, e.getMessage());
        }

        // Send PUSH notification (if template exists)
        try {
            NotificationTemplateEntity pushTemplate = templateService.resolveTemplate(
                    templateCode, NotificationChannel.PUSH, language);
            if (pushTemplate != null && userId != null) {
                String title = templateService.renderSubject(pushTemplate, variables);
                String body = templateService.renderTemplate(pushTemplate, variables);
                pushNotificationService.sendPush(userId, title, body, templateCode, transactionId);
            }
        } catch (Exception e) {
            log.error("Failed to dispatch PUSH notification for template {}: {}", templateCode, e.getMessage());
        }

        log.info("Notification dispatch completed for templateCode={}, userId={}", templateCode, userId);
    }
}

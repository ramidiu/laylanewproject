package com.remitz.modules.notification.service;

import com.remitz.common.enums.NotificationChannel;
import com.remitz.common.enums.NotificationStatus;
import com.remitz.modules.notification.config.NotificationProperties;
import com.remitz.modules.notification.entity.NotificationLogEntity;
import com.remitz.modules.notification.repository.NotificationLogRepository;
import com.remitz.modules.user.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final NotificationProperties notificationProperties;
    private final NotificationLogRepository notificationLogRepository;
    // Code added by Naresh: System Controls Phase 5 — runtime master switch for outbound SMS.
    private final SystemConfigService systemConfigService;

    public boolean sendSms(String to, String body) {
        return sendSms(to, body, null, null, null);
    }

    public boolean sendSms(String to, String body, Long userId, String templateCode, Long transactionId) {
        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        // Default TRUE preserves existing behavior when the row is missing.
        if (!systemConfigService.getBoolean("notifications.sms.enabled", true)) {
            log.info("SMS skipped: notifications.sms.enabled=false (to={}, template={})",
                    to, templateCode);
            return false;
        }

        NotificationLogEntity logEntry = NotificationLogEntity.builder()
                .userId(userId != null ? userId : 0L)
                .transactionId(transactionId)
                .templateCode(templateCode != null ? templateCode : "DIRECT")
                .channel(NotificationChannel.SMS)
                .recipient(to)
                .status(NotificationStatus.QUEUED)
                .retryCount(0)
                .build();
        logEntry = notificationLogRepository.save(logEntry);

        NotificationProperties.TwilioConfig twilio = notificationProperties.getTwilio();

        if (!StringUtils.hasText(twilio.getAccountSid()) || !StringUtils.hasText(twilio.getAuthToken())) {
            log.warn("Twilio credentials not configured. SMS to {} will not be sent. Body: {}", to, body);
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setErrorMessage("Twilio credentials not configured");
            notificationLogRepository.save(logEntry);
            return false;
        }

        int maxAttempts = notificationProperties.getRetry().getMaxAttempts();
        long delayMs = notificationProperties.getRetry().getDelayMs();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // Twilio REST API integration
                // Using basic HTTP call to avoid hard dependency on Twilio SDK
                sendViaTwilio(to, body, twilio);

                logEntry.setStatus(NotificationStatus.SENT);
                logEntry.setSentAt(LocalDateTime.now());
                logEntry.setRetryCount(attempt - 1);
                notificationLogRepository.save(logEntry);

                log.info("SMS sent successfully to: {} (attempt {})", to, attempt);
                return true;

            } catch (Exception e) {
                log.warn("Failed to send SMS to: {} (attempt {}/{}): {}", to, attempt, maxAttempts, e.getMessage());
                logEntry.setRetryCount(attempt);
                logEntry.setErrorMessage(e.getMessage());

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        logEntry.setStatus(NotificationStatus.FAILED);
        notificationLogRepository.save(logEntry);
        log.error("Failed to send SMS to: {} after {} attempts", to, maxAttempts);
        return false;
    }

    private void sendViaTwilio(String to, String body, NotificationProperties.TwilioConfig twilio) throws Exception {
        String url = String.format("https://api.twilio.com/2010-04-01/Accounts/%s/Messages.json",
                twilio.getAccountSid());

        // Build form data
        String formData = String.format("To=%s&From=%s&Body=%s",
                java.net.URLEncoder.encode(to, "UTF-8"),
                java.net.URLEncoder.encode(twilio.getFromNumber(), "UTF-8"),
                java.net.URLEncoder.encode(body, "UTF-8"));

        String credentials = twilio.getAccountSid() + ":" + twilio.getAuthToken();
        String encodedCredentials = java.util.Base64.getEncoder().encodeToString(credentials.getBytes());

        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create(url))
                .header("Authorization", "Basic " + encodedCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(formData))
                .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            throw new RuntimeException("Twilio API returned status " + response.statusCode() + ": " + response.body());
        }

        log.debug("Twilio SMS response: {}", response.body());
    }
}

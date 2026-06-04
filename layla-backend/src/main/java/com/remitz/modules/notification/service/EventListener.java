package com.remitz.modules.notification.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.modules.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventListener {

    private final NotificationDispatcher notificationDispatcher;
    private final ObjectMapper objectMapper;

    public void handleTransactionEvent(String message) {
        log.info("Received transaction event: {}", message);
        processEvent(message, "transaction");
    }

    public void handleKycEvent(String message) {
        log.info("Received KYC event: {}", message);
        processEvent(message, "kyc");
    }

    public void handleComplianceEvent(String message) {
        log.info("Received compliance event: {}", message);
        processEvent(message, "compliance");
    }

    private void processEvent(String message, String eventSource) {
        try {
            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);

            if (event.getTemplateCode() == null) {
                log.warn("Received {} event without templateCode, skipping", eventSource);
                return;
            }

            notificationDispatcher.dispatch(
                    event.getTemplateCode(),
                    event.getUserId(),
                    event.getEmail(),
                    event.getPhone(),
                    event.getLanguage(),
                    event.getVariables(),
                    event.getTransactionId()
            );

        } catch (Exception e) {
            log.error("Failed to process {} event: {}. Message: {}", eventSource, e.getMessage(), message);
        }
    }
}

package com.remitz.modules.transaction.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.remitz.modules.user.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("transactionRedisPublisher")
@Slf4j
public class RedisPublisher {

    private static final String TRANSACTION_EVENTS_CHANNEL = "transaction-events";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    // Code added by Naresh: System Controls Phase 6 — runtime gate for transaction-status events.
    private final SystemConfigService systemConfigService;

    public RedisPublisher(StringRedisTemplate stringRedisTemplate,
                          SystemConfigService systemConfigService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.systemConfigService = systemConfigService;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publishTransactionEvent(String eventType, Map<String, Object> eventData) {
        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        // Default TRUE preserves existing behavior when the row is missing.
        if (!systemConfigService.getBoolean("notifications.transaction_status.enabled", true)) {
            log.info("Transaction-status event skipped: notifications.transaction_status.enabled=false (eventType={})",
                    eventType);
            return;
        }
        try {
            Map<String, Object> event = Map.of(
                    "eventType", eventType,
                    "data", eventData,
                    "timestamp", System.currentTimeMillis()
            );
            String message = objectMapper.writeValueAsString(event);
            stringRedisTemplate.convertAndSend(TRANSACTION_EVENTS_CHANNEL, message);
            log.debug("Published transaction event: {} for data: {}", eventType, eventData);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize transaction event: {}", e.getMessage(), e);
        }
    }
}

package com.remitz.modules.user.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component("kycRedisPublisher")
@Slf4j
public class RedisPublisher {

    private static final String KYC_EVENTS_CHANNEL = "kyc-events";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisPublisher(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public void publishKycEvent(String templateCode, Long userId, String email,
                                 String firstName, Map<String, String> variables) {
        try {
            Map<String, Object> event = Map.of(
                    "templateCode", templateCode,
                    "userId", userId,
                    "email", email != null ? email : "",
                    "language", "en",
                    "variables", variables != null ? variables : Map.of()
            );
            String message = objectMapper.writeValueAsString(event);
            stringRedisTemplate.convertAndSend(KYC_EVENTS_CHANNEL, message);
            log.info("Published KYC event: {} for userId: {}", templateCode, userId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize KYC event: {}", e.getMessage(), e);
        }
    }
}

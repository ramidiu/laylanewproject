package com.remitz.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BrevoEmailSender {

    private static final String API_URL = "https://api.brevo.com/v3/smtp/email";

    @Value("${brevo.api-key:}")
    private String apiKey;

    @Value("${brevo.from-email:noreply@laylamoneytransfer.com}")
    private String fromEmail;

    private final RestTemplate restTemplate = new RestTemplate();

    public boolean send(String toEmail, String subject, String htmlContent) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Brevo API key not configured — skipping email to {}", toEmail);
            return false;
        }
        try {
            log.info("Brevo sending from: {}", fromEmail);
            log.info("Brevo sending to: {}", toEmail);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", apiKey);

            Map<String, String> sender = new HashMap<>();
            sender.put("email", fromEmail);

            Map<String, String> recipient = new HashMap<>();
            recipient.put("email", toEmail);
            List<Map<String, String>> toList = Collections.singletonList(recipient);

            Map<String, Object> body = new HashMap<>();
            body.put("sender", sender);
            body.put("to", toList);
            body.put("subject", subject);
            body.put("htmlContent", htmlContent);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(API_URL, request, String.class);

            log.info("Brevo response: {}", response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Brevo email sent to {}", toEmail);
                return true;
            }
            log.warn("Brevo email to {} failed with status {}", toEmail, response.getStatusCode());
        } catch (HttpStatusCodeException httpEx) {
            log.error("Brevo email to {} HTTP {} body: {}", toEmail,
                    httpEx.getStatusCode(), httpEx.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Brevo email to {} error: {}", toEmail, e.getMessage());
        }
        return false;
    }
}

package com.remitz.modules.notification.service;

import com.remitz.common.enums.NotificationChannel;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.notification.dto.NotificationTemplateCreateRequest;
import com.remitz.modules.notification.dto.NotificationTemplateResponse;
import com.remitz.modules.notification.dto.NotificationTemplateUpdateRequest;
import com.remitz.modules.notification.dto.TemplatePreviewRequest;
import com.remitz.modules.notification.dto.TemplatePreviewResponse;
import com.remitz.modules.notification.entity.NotificationTemplateEntity;
import com.remitz.modules.notification.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateService {

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateEntity resolveTemplate(String templateCode, NotificationChannel channel, String language) {
        // Try exact language match first
        if (language != null && !language.equals("en")) {
            var template = templateRepository
                    .findByTemplateCodeAndChannelAndLanguageAndIsActiveTrue(templateCode, channel, language);
            if (template.isPresent()) {
                return template.get();
            }
        }

        // Fallback to English
        return templateRepository
                .findByTemplateCodeAndChannelAndLanguageAndIsActiveTrue(templateCode, channel, "en")
                .orElse(null);
    }

    public String renderTemplate(NotificationTemplateEntity template, Map<String, String> variables) {
        if (template == null || template.getBodyTemplate() == null) {
            return null;
        }
        return replaceVariables(template.getBodyTemplate(), variables);
    }

    public String renderSubject(NotificationTemplateEntity template, Map<String, String> variables) {
        if (template == null || template.getSubject() == null) {
            return null;
        }
        return replaceVariables(template.getSubject(), variables);
    }

    private String replaceVariables(String text, Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return text;
        }
        String result = text;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            result = result.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<NotificationTemplateResponse> listTemplates() {
        return templateRepository.findAllByIsActiveTrue().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public NotificationTemplateResponse updateTemplate(Long id, NotificationTemplateUpdateRequest request) {
        NotificationTemplateEntity template = templateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + id));

        if (request.getSubject() != null) {
            template.setSubject(request.getSubject());
        }
        if (request.getBodyTemplate() != null) {
            template.setBodyTemplate(request.getBodyTemplate());
        }
        if (request.getIsActive() != null) {
            template.setIsActive(request.getIsActive());
        }

        NotificationTemplateEntity saved = templateRepository.save(template);
        log.info("Updated notification template: id={}, code={}", saved.getId(), saved.getTemplateCode());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public TemplatePreviewResponse previewTemplate(TemplatePreviewRequest request) {
        NotificationTemplateEntity template;

        if (request.getTemplateId() != null) {
            template = templateRepository.findById(request.getTemplateId())
                    .orElseThrow(() -> new ResourceNotFoundException("Template not found with id: " + request.getTemplateId()));
        } else if (request.getTemplateKey() != null) {
            template = templateRepository.findFirstByTemplateCodeOrderByIdAsc(request.getTemplateKey())
                    .orElseThrow(() -> new ResourceNotFoundException("Template not found with key: " + request.getTemplateKey()));
        } else {
            throw new IllegalArgumentException("Either templateId or templateKey must be provided");
        }

        Map<String, String> sampleData = buildSampleData();

        String renderedSubject = renderSubject(template, sampleData);
        String renderedBody = renderTemplate(template, sampleData);

        return TemplatePreviewResponse.builder()
                .templateId(template.getId())
                .templateCode(template.getTemplateCode())
                .renderedSubject(renderedSubject)
                .renderedBody(renderedBody)
                .build();
    }

    private Map<String, String> buildSampleData() {
        Map<String, String> sampleData = new LinkedHashMap<>();
        sampleData.put("firstName", "John");
        sampleData.put("lastName", "Doe");
        sampleData.put("name", "John Doe");
        sampleData.put("email", "john@example.com");
        sampleData.put("otp", "123456");
        sampleData.put("amount", "500.00");
        sampleData.put("sendAmount", "500.00");
        sampleData.put("receiveAmount", "41,250.00");
        sampleData.put("sendCurrency", "GBP");
        sampleData.put("receiveCurrency", "INR");
        sampleData.put("exchangeRate", "82.50");
        sampleData.put("feeAmount", "9.99");
        sampleData.put("referenceNumber", "FB-2024-ABC12345");
        sampleData.put("receiverName", "Jane Doe");
        sampleData.put("reason", "Sample reason");
        sampleData.put("status", "COMPLETED");
        return sampleData;
    }

    @Transactional
    public NotificationTemplateResponse createTemplate(NotificationTemplateCreateRequest request) {
        NotificationTemplateEntity template = NotificationTemplateEntity.builder()
                .templateCode(request.getTemplateCode())
                .channel(request.getChannel())
                .language(request.getLanguage())
                .subject(request.getSubject())
                .bodyTemplate(request.getBodyTemplate())
                .isActive(request.getIsActive())
                .build();

        NotificationTemplateEntity saved = templateRepository.save(template);
        log.info("Created notification template: id={}, code={}, channel={}",
                saved.getId(), saved.getTemplateCode(), saved.getChannel());
        return toResponse(saved);
    }

    private NotificationTemplateResponse toResponse(NotificationTemplateEntity entity) {
        return NotificationTemplateResponse.builder()
                .id(entity.getId())
                .templateCode(entity.getTemplateCode())
                .channel(entity.getChannel())
                .language(entity.getLanguage())
                .subject(entity.getSubject())
                .bodyTemplate(entity.getBodyTemplate())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

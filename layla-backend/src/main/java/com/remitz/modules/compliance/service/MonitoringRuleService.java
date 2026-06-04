package com.remitz.modules.compliance.service;

import com.remitz.modules.compliance.dto.MonitoringRuleRequest;
import com.remitz.modules.compliance.dto.MonitoringRuleResponse;
import com.remitz.modules.compliance.entity.MonitoringRuleEntity;
import com.remitz.modules.compliance.repository.MonitoringRuleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringRuleService {

    private final MonitoringRuleRepository monitoringRuleRepository;

    @Transactional
    public MonitoringRuleResponse createRule(MonitoringRuleRequest request) {
        MonitoringRuleEntity rule = MonitoringRuleEntity.builder()
                .ruleName(request.getRuleName())
                .ruleType(request.getRuleType())
                .parameters(request.getParameters())
                .severity(request.getSeverity())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .build();

        rule = monitoringRuleRepository.save(rule);
        log.info("Monitoring rule created: {} ({})", rule.getRuleName(), rule.getRuleType());
        return toResponse(rule);
    }

    @Transactional(readOnly = true)
    public List<MonitoringRuleResponse> getAllRules() {
        return monitoringRuleRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public MonitoringRuleResponse updateRule(Long id, MonitoringRuleRequest request) {
        MonitoringRuleEntity rule = monitoringRuleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Monitoring rule not found: " + id));

        if (request.getRuleName() != null) {
            rule.setRuleName(request.getRuleName());
        }
        if (request.getRuleType() != null) {
            rule.setRuleType(request.getRuleType());
        }
        if (request.getParameters() != null) {
            rule.setParameters(request.getParameters());
        }
        if (request.getSeverity() != null) {
            rule.setSeverity(request.getSeverity());
        }
        if (request.getIsActive() != null) {
            rule.setIsActive(request.getIsActive());
        }

        rule = monitoringRuleRepository.save(rule);
        log.info("Monitoring rule updated: {} ({})", rule.getRuleName(), rule.getId());
        return toResponse(rule);
    }

    private MonitoringRuleResponse toResponse(MonitoringRuleEntity entity) {
        return MonitoringRuleResponse.builder()
                .id(entity.getId())
                .ruleName(entity.getRuleName())
                .ruleType(entity.getRuleType())
                .parameters(entity.getParameters())
                .severity(entity.getSeverity())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

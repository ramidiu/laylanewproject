package com.remitz.modules.compliance.service;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import com.remitz.common.enums.CaseStatus;
import com.remitz.common.enums.SarFilingStatus;
import com.remitz.modules.compliance.dto.*;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import com.remitz.modules.compliance.entity.ComplianceCaseEntity;
import com.remitz.modules.compliance.entity.SarReportEntity;
import com.remitz.modules.compliance.repository.ComplianceAlertRepository;
import com.remitz.modules.compliance.repository.ComplianceCaseRepository;
import com.remitz.modules.compliance.repository.SarReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComplianceCaseService {

    private final ComplianceCaseRepository complianceCaseRepository;

    public Page<ComplianceCaseResponse> listCases(Pageable pageable) {
        return complianceCaseRepository.findAll(pageable).map(this::toResponse);
    }
    private final ComplianceAlertRepository complianceAlertRepository;
    private final SarReportRepository sarReportRepository;

    @Transactional
    public ComplianceCaseResponse createCase(CaseCreateRequest request) {
        String caseReference = generateCaseReference();

        AlertSeverity priority;
        try {
            priority = AlertSeverity.valueOf(request.getPriority().toUpperCase());
        } catch (IllegalArgumentException e) {
            priority = AlertSeverity.MEDIUM;
        }

        ComplianceCaseEntity caseEntity = ComplianceCaseEntity.builder()
                .caseReference(caseReference)
                .userId(request.getUserId())
                .status(CaseStatus.OPEN)
                .priority(priority)
                .assignedTo(request.getAssigneeId())
                .summary(request.getSummary())
                .build();

        caseEntity = complianceCaseRepository.save(caseEntity);

        // Link alerts to case by escalating them
        if (request.getAlertIds() != null) {
            for (Long alertId : request.getAlertIds()) {
                complianceAlertRepository.findById(alertId).ifPresent(alert -> {
                    alert.setStatus(AlertStatus.ESCALATED);
                    complianceAlertRepository.save(alert);
                });
            }
        }

        log.info("Compliance case created: {} for user {}", caseReference, request.getUserId());
        return toResponse(caseEntity);
    }

    @Transactional(readOnly = true)
    public ComplianceCaseResponse getCase(Long id) {
        ComplianceCaseEntity caseEntity = complianceCaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compliance case not found: " + id));

        ComplianceCaseResponse response = toResponse(caseEntity);

        List<SarReportEntity> sarReports = sarReportRepository.findByComplianceCaseId(id);
        response.setSarReports(sarReports.stream()
                .map(this::toSarResponse)
                .collect(Collectors.toList()));

        return response;
    }

    @Transactional
    public ComplianceCaseResponse updateCase(Long id, CaseUpdateRequest request) {
        ComplianceCaseEntity caseEntity = complianceCaseRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Compliance case not found: " + id));

        if (request.getStatus() != null) {
            caseEntity.setStatus(request.getStatus());
            if (request.getStatus() == CaseStatus.CLOSED) {
                caseEntity.setClosedAt(LocalDateTime.now());
            }
        }

        if (request.getAssignedTo() != null) {
            caseEntity.setAssignedTo(request.getAssignedTo());
        }

        if (request.getSummary() != null) {
            caseEntity.setSummary(request.getSummary());
        }

        if (request.getFindings() != null) {
            caseEntity.setFindings(request.getFindings());
        }

        if (request.getOutcome() != null) {
            caseEntity.setOutcome(request.getOutcome());
        }

        log.info("Compliance case {} updated: status={}", caseEntity.getCaseReference(), caseEntity.getStatus());
        return toResponse(complianceCaseRepository.save(caseEntity));
    }

    @Transactional
    public SarReportResponse createSarReport(Long caseId, SarCreateRequest request) {
        ComplianceCaseEntity caseEntity = complianceCaseRepository.findById(caseId)
                .orElseThrow(() -> new RuntimeException("Compliance case not found: " + caseId));

        SarReportEntity sarReport = SarReportEntity.builder()
                .complianceCase(caseEntity)
                .reportType(request.getReportType())
                .filingStatus(SarFilingStatus.DRAFT)
                .reportContent(request.getReportContent())
                .filedBy(request.getFiledBy())
                .build();

        sarReport = sarReportRepository.save(sarReport);
        log.info("SAR report created for case {}: type={}", caseEntity.getCaseReference(), request.getReportType());

        return toSarResponse(sarReport);
    }

    private String generateCaseReference() {
        int year = Year.now().getValue();
        long count = complianceCaseRepository.count() + 1;
        return String.format("CC-%d-%05d", year, count);
    }

    private ComplianceCaseResponse toResponse(ComplianceCaseEntity entity) {
        return ComplianceCaseResponse.builder()
                .id(entity.getId())
                .caseReference(entity.getCaseReference())
                .userId(entity.getUserId())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .assignedTo(entity.getAssignedTo())
                .summary(entity.getSummary())
                .findings(entity.getFindings())
                .outcome(entity.getOutcome())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .closedAt(entity.getClosedAt())
                .build();
    }

    private SarReportResponse toSarResponse(SarReportEntity entity) {
        return SarReportResponse.builder()
                .id(entity.getId())
                .caseId(entity.getComplianceCase().getId())
                .reportType(entity.getReportType())
                .filingStatus(entity.getFilingStatus())
                .reportContent(entity.getReportContent())
                .filedBy(entity.getFiledBy())
                .filedAt(entity.getFiledAt())
                .acknowledgedAt(entity.getAcknowledgedAt())
                .externalReference(entity.getExternalReference())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

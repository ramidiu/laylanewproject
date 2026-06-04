package com.remitz.modules.compliance.entity;

import com.remitz.common.enums.SarFilingStatus;
import com.remitz.common.enums.SarReportType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "sar_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SarReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "case_id", nullable = false)
    private ComplianceCaseEntity complianceCase;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false)
    private SarReportType reportType;

    @Enumerated(EnumType.STRING)
    @Column(name = "filing_status", nullable = false)
    private SarFilingStatus filingStatus;

    @Column(name = "report_content", columnDefinition = "TEXT")
    private String reportContent;

    @Column(name = "filed_by")
    private Long filedBy;

    @Column(name = "filed_at")
    private LocalDateTime filedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "external_reference", length = 255)
    private String externalReference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.filingStatus == null) {
            this.filingStatus = SarFilingStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

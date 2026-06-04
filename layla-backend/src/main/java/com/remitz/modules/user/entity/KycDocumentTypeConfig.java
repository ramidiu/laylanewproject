package com.remitz.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_document_types")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycDocumentTypeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "country_name", length = 100)
    private String countryName;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(name = "document_name", nullable = false, length = 100)
    private String documentName;

    @Column
    private Integer sides;

    @Column(name = "has_id_number")
    private Boolean hasIdNumber;

    @Column(name = "id_number_label", length = 50)
    private String idNumberLabel;

    @Column(name = "id_number_format", length = 50)
    private String idNumberFormat;

    @Column(name = "has_expiry")
    private Boolean hasExpiry;

    @Column(name = "has_issue_date")
    private Boolean hasIssueDate;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.sides == null) {
            this.sides = 1;
        }
        if (this.hasIdNumber == null) {
            this.hasIdNumber = false;
        }
        if (this.hasExpiry == null) {
            this.hasExpiry = false;
        }
        if (this.hasIssueDate == null) {
            this.hasIssueDate = false;
        }
        if (this.isActive == null) {
            this.isActive = true;
        }
        if (this.displayOrder == null) {
            this.displayOrder = 0;
        }
        if (this.countryCode == null) {
            this.countryCode = "ALL";
        }
    }
}

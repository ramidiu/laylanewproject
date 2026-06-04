package com.remitz.modules.payin.customer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "payin_customer_documents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayinCustomerDocumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    /** IDENTITY_FRONT, IDENTITY_BACK, ADDRESS_PROOF */
    @Column(name = "doc_side", nullable = false, length = 20)
    private String docSide;

    /** PASSPORT, DRIVING_LICENSE, NATIONAL_ID, UTILITY_BILL, BANK_STATEMENT, etc. */
    @Column(name = "doc_category", nullable = false, length = 50)
    private String docCategory;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

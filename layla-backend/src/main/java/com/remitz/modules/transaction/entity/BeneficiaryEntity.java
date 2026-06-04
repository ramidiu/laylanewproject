package com.remitz.modules.transaction.entity;

import com.remitz.common.enums.DeliveryMethod;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BeneficiaryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "country", nullable = false, length = 100)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod;

    @Column(name = "bank_name")
    private String bankName;

    @Column(name = "account_number", length = 100)
    private String accountNumber;

    @Column(name = "iban", length = 50)
    private String iban;

    @Column(name = "swift_bic", length = 20)
    private String swiftBic;

    @Column(name = "sort_code", length = 150)
    private String sortCode;

    @Column(name = "branch_state", length = 100)
    private String branchState;

    @Column(name = "branch_city", length = 100)
    private String branchCity;

    @Column(name = "mobile_number", length = 30)
    private String mobileNumber;

    @Column(name = "mobile_provider", length = 100)
    private String mobileProvider;

    @Column(name = "id_number", length = 100)
    private String idNumber;

    @Column(name = "id_type", length = 50)
    private String idType;

    @Column(name = "relation_id")
    private Long relationId;

    @Column(name = "email")
    private String email;

    @Column(name = "date_of_birth")
    private java.time.LocalDate dateOfBirth;

    @Column(name = "address")
    private String address;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "is_favourite", nullable = false)
    private Boolean isFavourite;

    @Column(name = "is_blocked", nullable = false)
    private Boolean isBlocked;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isFavourite == null) isFavourite = false;
        if (isBlocked == null) isBlocked = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

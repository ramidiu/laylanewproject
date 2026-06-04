package com.remitz.modules.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bank_database")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankDatabase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "bank_name", nullable = false)
    private String bankName;

    @Column(name = "bank_identifier")
    private String bankIdentifier;

    @Column(name = "bank_address")
    private String bankAddress;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "city")
    private String city;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }
}

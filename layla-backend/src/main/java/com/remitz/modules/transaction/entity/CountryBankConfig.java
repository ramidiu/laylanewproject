package com.remitz.modules.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "country_bank_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CountryBankConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, unique = true, length = 3)
    private String countryCode;

    @Column(name = "country_name")
    private String countryName;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "identifier_name")
    private String identifierName;

    @Column(name = "identifier_label")
    private String identifierLabel;

    @Column(name = "identifier_format")
    private String identifierFormat;

    @Column(name = "identifier_length")
    private Integer identifierLength;

    @Column(name = "auto_lookup")
    private Boolean autoLookup;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (autoLookup == null) autoLookup = false;
    }
}

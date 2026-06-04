package com.remitz.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "country_risk_tiers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CountryRiskTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 3)
    private String countryCode;

    @Column(name = "country_name", nullable = false, length = 100)
    private String countryName;

    @Column(name = "risk_tier", nullable = false, length = 20)
    private String riskTier;

    @Column(name = "risk_points", nullable = false)
    private Integer riskPoints;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}

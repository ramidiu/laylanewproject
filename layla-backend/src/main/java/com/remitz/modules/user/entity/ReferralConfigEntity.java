package com.remitz.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "referral_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReferralConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "corridor_id")
    private Long corridorId;

    @Column(name = "rate_boost_percentage", precision = 10, scale = 4)
    private BigDecimal rateBoostPercentage;

    @Column(name = "referrer_credit_amount", precision = 19, scale = 4)
    private BigDecimal referrerCreditAmount;

    @Column(name = "credit_currency", length = 3)
    private String creditCurrency;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

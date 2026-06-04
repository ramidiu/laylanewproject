package com.remitz.modules.user.entity;

import com.remitz.common.enums.KycTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "kyc_tier_limits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KycTierLimitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycTier tier;

    @Column(name = "max_per_transaction", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxPerTransaction;

    @Column(name = "max_daily", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxDaily;

    @Column(name = "max_weekly", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxWeekly;

    @Column(name = "max_monthly", nullable = false, precision = 18, scale = 2)
    private BigDecimal maxMonthly;

    @Column(length = 3)
    private String currency;

    @Column(name = "corridor_id")
    private Long corridorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.currency == null) {
            this.currency = "GBP";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

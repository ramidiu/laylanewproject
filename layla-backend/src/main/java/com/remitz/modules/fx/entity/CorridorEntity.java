package com.remitz.modules.fx.entity;

import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.RiskLevel;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "corridors")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorridorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "send_country", nullable = false, length = 3)
    private String sendCountry;

    @Column(name = "receive_country", nullable = false, length = 3)
    private String receiveCountry;

    @Column(name = "send_currency", nullable = false, length = 3)
    private String sendCurrency;

    @Column(name = "receive_currency", nullable = false, length = 3)
    private String receiveCurrency;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "min_amount", precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "daily_limit", precision = 18, scale = 2)
    private BigDecimal dailyLimit;

    @Column(name = "monthly_limit", precision = 18, scale = 2)
    private BigDecimal monthlyLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_kyc_tier")
    private KycTier requiredKycTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "corridor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CorridorFeeEntity> fees = new ArrayList<>();

    @OneToMany(mappedBy = "corridor", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CorridorDeliveryMethodEntity> deliveryMethods = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

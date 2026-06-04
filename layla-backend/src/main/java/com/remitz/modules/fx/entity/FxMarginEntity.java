package com.remitz.modules.fx.entity;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.KycTier;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fx_margins")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FxMarginEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "send_country", length = 3)
    private String sendCountry;

    @Column(name = "receive_country", length = 3)
    private String receiveCountry;

    @Column(name = "send_currency", nullable = false, length = 3)
    private String sendCurrency;

    @Column(name = "receive_currency", nullable = false, length = 3)
    private String receiveCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method")
    private DeliveryMethod deliveryMethod;

    @Column(name = "margin_percentage", precision = 6, scale = 4)
    private BigDecimal marginPercentage;

    @Column(name = "margin_fixed", precision = 18, scale = 4)
    private BigDecimal marginFixed;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_tier")
    private KycTier customerTier;

    @Column(name = "min_amount", precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

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

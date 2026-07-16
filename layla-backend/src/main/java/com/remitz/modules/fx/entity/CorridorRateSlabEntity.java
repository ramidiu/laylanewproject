package com.remitz.modules.fx.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * An amount-based exchange-rate band for a corridor. When a quote's send amount
 * falls in [minAmount, maxAmount], {@code rate} is used as the applied rate
 * (receive = send * rate). maxAmount null = open-ended (this slab and above).
 */
@Entity
@Table(name = "corridor_rate_slabs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorridorRateSlabEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "corridor_id", nullable = false)
    private Long corridorId;

    /**
     * The payout delivery method this rate band applies to (DeliveryMethod enum name,
     * e.g. BANK_DEPOSIT / CASH_PICKUP / MOBILE_WALLET). NULL = applies to every payment
     * type on this corridor (legacy corridor-wide slab).
     */
    @Column(name = "delivery_method", length = 30)
    private String deliveryMethod;

    @Column(name = "min_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 18, scale = 2)
    private BigDecimal maxAmount;

    @Column(name = "rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}

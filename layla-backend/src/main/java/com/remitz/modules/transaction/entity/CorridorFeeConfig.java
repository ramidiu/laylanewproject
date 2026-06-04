package com.remitz.modules.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "corridor_fee_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CorridorFeeConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_currency", nullable = false, length = 3)
    private String fromCurrency;

    @Column(name = "to_currency", nullable = false, length = 3)
    private String toCurrency;

    @Column(name = "payin_partner_id")
    private Long payinPartnerId;

    @Column(name = "payin_share_type")
    private String payinShareType;

    @Column(name = "payin_share_value", precision = 18, scale = 4)
    private BigDecimal payinShareValue;

    @Column(name = "payout_partner_id")
    private Long payoutPartnerId;

    @Column(name = "payout_share_type")
    private String payoutShareType;

    @Column(name = "payout_share_value", precision = 18, scale = 4)
    private BigDecimal payoutShareValue;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

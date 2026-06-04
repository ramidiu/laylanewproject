package com.remitz.modules.payin.volume.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "volume_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VolumePaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", length = 100)
    private String paymentId;

    @Column(name = "merchant_payment_id", nullable = false, unique = true, length = 100)
    private String merchantPaymentId;

    /** Internal transaction ID (regular send-money transaction) */
    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "transaction_reference", length = 100)
    private String transactionReference;

    @Column(name = "currency_iso", nullable = false, length = 10)
    private String currencyIso;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_status", nullable = false, length = 30)
    private String paymentStatus;

    @Column(name = "settle_status", length = 30)
    private String settleStatus;

    @Column(name = "is_external")
    private Boolean isExternal;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
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

package com.remitz.modules.payin.trust.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "trust_payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrustPaymentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", length = 100)
    private String transactionRef;

    @Column(name = "order_reference", length = 100)
    private String orderReference;

    @Column(name = "request_reference", length = 100)
    private String requestReference;

    @Column(name = "transaction_reference", length = 200)
    private String transactionReference;

    @Column(precision = 15, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency_iso", length = 10)
    private String currencyIso;

    @Column(name = "payment_status", length = 50)
    private String paymentStatus;

    @Column(name = "settle_status", length = 50)
    private String settleStatus;

    @Column(name = "error_code", length = 20)
    private String errorCode;

    @Column(name = "raw_params", columnDefinition = "TEXT")
    private String rawParams;

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

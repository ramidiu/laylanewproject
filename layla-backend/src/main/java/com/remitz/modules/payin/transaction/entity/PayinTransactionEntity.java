package com.remitz.modules.payin.transaction.entity;

import com.remitz.common.enums.CreatedSource;
import com.remitz.common.enums.PaymentMode;
import com.remitz.common.enums.PayinTransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payin_transactions",
        uniqueConstraints = @UniqueConstraint(name = "uk_payin_txn_ext_ref", columnNames = "external_reference_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PayinTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, unique = true, length = 36)
    private String transactionId;

    @Column(name = "customer_id", nullable = false, length = 36)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_source", nullable = false, length = 20)
    private CreatedSource customerSource;

    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(name = "receive_currency", length = 10)
    private String receiveCurrency;

    @Column(name = "receive_amount", precision = 18, scale = 4)
    private BigDecimal receiveAmount;

    @Column(name = "delivery_method", length = 30)
    private String deliveryMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false, length = 30)
    private PaymentMode paymentMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayinTransactionStatus status;

    @Column(name = "external_reference_id", length = 100)
    private String externalReferenceId;

    @Column(name = "linked_transaction_id")
    private Long linkedTransactionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        // Respect an explicitly-set createdAt (admin-chosen transaction date); else default to now.
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

package com.remitz.modules.transaction.entity;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "reference_number", nullable = false, unique = true, length = 50)
    private String referenceNumber;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_email")
    private String senderEmail;

    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Column(name = "corridor_id", nullable = false)
    private Long corridorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod;

    @Column(name = "send_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal sendAmount;

    @Column(name = "send_currency", nullable = false, length = 3)
    private String sendCurrency;

    @Column(name = "receive_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal receiveAmount;

    @Column(name = "receive_currency", nullable = false, length = 3)
    private String receiveCurrency;

    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal exchangeRate;

    @Column(name = "applied_rate", nullable = false, precision = 18, scale = 8)
    private BigDecimal appliedRate;

    @Column(name = "locked_rate", precision = 18, scale = 8)
    private BigDecimal lockedRate;

    @Column(name = "rate_locked_at")
    private LocalDateTime rateLockedAt;

    @Column(name = "rate_lock_expires_at")
    private LocalDateTime rateLockExpiresAt;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "fee_currency", length = 3)
    private String feeCurrency;

    @Column(name = "fx_margin_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal fxMarginAmount;

    @Column(name = "total_debit_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalDebitAmount;

    @Column(name = "compliance_hold_reason", columnDefinition = "TEXT")
    private String complianceHoldReason;

    @Column(name = "payout_partner_id")
    private Long payoutPartnerId;

    @Column(name = "payin_partner_id")
    private Long payinPartnerId;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "payout_reference")
    private String payoutReference;

    @Column(name = "payout_confirmed_at")
    private LocalDateTime payoutConfirmedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type", nullable = false)
    private PaymentMethodType paymentMethodType;

    @Column(name = "payment_reference")
    private String paymentReference;

    @Column(name = "is_recurring", nullable = false)
    private Boolean isRecurring;

    @Column(name = "recurring_schedule_id")
    private Long recurringScheduleId;

    @Column(name = "risk_score")
    private Integer riskScore;

    @Column(name = "visitor_id", length = 128)
    private String visitorId;

    @Column(name = "risk_factors", columnDefinition = "JSON")
    private String riskFactors;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "wallet_amount_used", precision = 18, scale = 4)
    private BigDecimal walletAmountUsed;

    @Column(name = "referral_code_used", length = 12)
    private String referralCodeUsed;

    @Column(name = "rate_boost_applied", precision = 5, scale = 2)
    private BigDecimal rateBoostApplied;

    @Column(name = "rate_boost_amount", precision = 18, scale = 8)
    private BigDecimal rateBoostAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TransactionStatusHistoryEntity> statusHistory = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        // Respect an explicitly-set createdAt (e.g. admin-chosen payin date); else default to now.
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

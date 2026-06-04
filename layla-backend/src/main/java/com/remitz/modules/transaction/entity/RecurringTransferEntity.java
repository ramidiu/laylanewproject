package com.remitz.modules.transaction.entity;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.RecurringFrequency;
import com.remitz.common.enums.RecurringStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "recurring_transfers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecurringTransferEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "beneficiary_id", nullable = false)
    private Long beneficiaryId;

    @Column(name = "corridor_id", nullable = false)
    private Long corridorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false)
    private DeliveryMethod deliveryMethod;

    @Column(name = "send_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal sendAmount;

    @Column(name = "send_currency", nullable = false, length = 3)
    private String sendCurrency;

    @Column(name = "receive_currency", nullable = false, length = 3)
    private String receiveCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private RecurringFrequency frequency;

    @Column(name = "custom_interval_days")
    private Integer customIntervalDays;

    @Column(name = "next_execution_date", nullable = false)
    private LocalDate nextExecutionDate;

    @Column(name = "last_execution_date")
    private LocalDate lastExecutionDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private RecurringStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method_type", nullable = false)
    private PaymentMethodType paymentMethodType;

    @Column(name = "total_executions", nullable = false)
    private Integer totalExecutions;

    @Column(name = "max_executions")
    private Integer maxExecutions;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (totalExecutions == null) totalExecutions = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

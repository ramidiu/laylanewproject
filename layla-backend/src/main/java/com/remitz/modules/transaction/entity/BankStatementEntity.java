package com.remitz.modules.transaction.entity;

import com.remitz.common.enums.BankStatementStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "bank_statements")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 100)
    private String accountId;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "reference")
    private String reference;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "counterparty")
    private String counterparty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private BankStatementStatus status;

    @Column(name = "matched_payment_id")
    private Long matchedPaymentId;

    @Column(name = "imported_at", nullable = false, updatable = false)
    private LocalDateTime importedAt;

    @PrePersist
    protected void onCreate() {
        importedAt = LocalDateTime.now();
    }
}

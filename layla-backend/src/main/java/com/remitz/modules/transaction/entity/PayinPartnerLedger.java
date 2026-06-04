package com.remitz.modules.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payin_partner_ledger")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayinPartnerLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "partner_id", nullable = false)
    private Long partnerId;

    @Column(name = "transaction_id")
    private Long transactionId;

    @Column(name = "txn_display_id")
    private String txnDisplayId;

    @Column(name = "entry_type", nullable = false)
    private String entryType;

    @Column(name = "local_amount", precision = 18, scale = 4)
    private BigDecimal localAmount;

    @Column(name = "local_currency", length = 3)
    private String localCurrency;

    @Column(name = "usd_amount", precision = 18, scale = 4)
    private BigDecimal usdAmount;

    @Column(name = "fx_rate_used", precision = 18, scale = 8)
    private BigDecimal fxRateUsed;

    @Column(name = "balance_usd", precision = 18, scale = 4)
    private BigDecimal balanceUsd;

    @Column(name = "description")
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

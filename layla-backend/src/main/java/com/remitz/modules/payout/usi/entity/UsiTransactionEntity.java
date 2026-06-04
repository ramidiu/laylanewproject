package com.remitz.modules.payout.usi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "usi_transactions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsiTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** transactions.reference_number (TXN<n>) — the Layla-side id. */
    @Column(name = "transaction_id", length = 64, nullable = false, unique = true)
    private String transactionId;

    @Column(name = "trans_session_id", length = 64, unique = true)
    private String transSessionId;

    @Column(name = "reference_number", length = 64)
    private String referenceNumber;

    @Column(name = "remitter_id", length = 64)
    private String remitterId;

    @Column(name = "remitter_name")
    private String remitterName;

    @Column(name = "beneficiary_id", length = 64)
    private String beneficiaryId;

    @Column(name = "beneficiary_name")
    private String beneficiaryName;

    @Column(name = "trans_type", length = 50)
    private String transType;

    @Column(name = "destination_country", length = 100)
    private String destinationCountry;

    @Column(name = "source_currency", length = 8)
    private String sourceCurrency;

    @Column(name = "destination_currency", length = 8)
    private String destinationCurrency;

    @Column(name = "source_transfer_amount", precision = 18, scale = 4)
    private BigDecimal sourceTransferAmount;

    @Column(name = "destination_amount", precision = 18, scale = 4)
    private BigDecimal destinationAmount;

    @Column(name = "rate", precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "commission", precision = 18, scale = 4)
    private BigDecimal commission;

    @Column(name = "agent_fee", precision = 18, scale = 4)
    private BigDecimal agentFee;

    @Column(name = "hq_fee", precision = 18, scale = 4)
    private BigDecimal hqFee;

    @Column(name = "tax", precision = 18, scale = 4)
    private BigDecimal tax;

    @Column(name = "remitter_pay_amount", precision = 18, scale = 4)
    private BigDecimal remitterPayAmount;

    @Column(name = "agent_deduction", precision = 18, scale = 4)
    private BigDecimal agentDeduction;

    @Column(name = "agent_to_pay_hq", precision = 18, scale = 4)
    private BigDecimal agentToPayHq;

    @Column(name = "delivery_date")
    private LocalDateTime deliveryDate;

    @Column(name = "payment_token")
    private String paymentToken;

    @Column(name = "payment_status", length = 60)
    private String paymentStatus;

    @Column(name = "usi_status", length = 60)
    private String usiStatus;

    @Column(name = "status", length = 60)
    private String status;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}

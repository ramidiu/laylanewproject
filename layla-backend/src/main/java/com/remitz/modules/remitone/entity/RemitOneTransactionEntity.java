package com.remitz.modules.remitone.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "remit_one_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RemitOneTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 100)
    private String transactionId;

    @Column(name = "payment_status", length = 50)
    private String paymentStatus;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "trans_session_id", length = 100)
    private String transSessionId;

    @Column(name = "trans_type", length = 50)
    private String transType;

    @Column(name = "remitter_id", length = 100)
    private String remitterId;

    @Column(name = "remitter_name", length = 255)
    private String remitterName;

    @Column(name = "beneficiary_id", length = 100)
    private String beneficiaryId;

    @Column(name = "beneficiary_name", length = 255)
    private String beneficiaryName;

    @Column(name = "destination_country", length = 10)
    private String destinationCountry;

    @Column(name = "source_currency", length = 10)
    private String sourceCurrency;

    @Column(name = "destination_currency", length = 10)
    private String destinationCurrency;

    @Column(name = "source_amount", precision = 18, scale = 4)
    private BigDecimal sourceAmount;

    @Column(name = "rate", precision = 18, scale = 8)
    private BigDecimal rate;

    @Column(name = "destination_amount", precision = 18, scale = 4)
    private BigDecimal destinationAmount;

    @Column(name = "commission", precision = 18, scale = 4)
    private BigDecimal commission;

    @Column(name = "tax", precision = 18, scale = 4)
    private BigDecimal tax;

    @Column(name = "remitter_pay_amount", precision = 18, scale = 4)
    private BigDecimal remitterPayAmount;

    @Column(name = "comments_to_beneficiary", columnDefinition = "TEXT")
    private String commentsToBeneficiary;

    @Column(name = "raw_response", columnDefinition = "LONGTEXT")
    private String rawResponse;

    @Column(name = "created_on")
    private LocalDateTime createdOn;
}

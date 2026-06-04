package com.remitz.modules.payin.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayinTransactionDto {

    private String transactionId;
    // Human-facing TXN reference from the linked regular transaction (shown in all UIs).
    private String referenceNumber;
    private String customerId;
    private String customerSource;
    private Long beneficiaryId;
    private BigDecimal amount;
    private String currency;
    private String receiveCurrency;
    private BigDecimal receiveAmount;
    private String deliveryMethod;
    private String paymentMode;
    private String status;
    private String externalReferenceId;
    private LocalDateTime createdAt;
    private String beneficiaryName;
    private String beneficiaryBank;
    private String beneficiaryAccount;
    // Full beneficiary details so the payout partner can verify before paying.
    private String beneficiaryPhone;
    private String beneficiaryCountry;
    private String beneficiaryCity;
    private String beneficiaryBranch;
    private String beneficiarySwift;
    private String beneficiaryIban;
    private String beneficiaryAddress;
    private String beneficiaryProvider;
}

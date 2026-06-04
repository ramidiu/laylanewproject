package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.TransactionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    private Long id;
    private String referenceNumber;
    /** Payout-side reference assigned by the partner (e.g. USI Money). */
    private String payoutReference;
    private String senderName;
    private String beneficiaryName;
    private TransactionStatus status;
    private DeliveryMethod deliveryMethod;
    private BigDecimal sendAmount;
    private String sendCurrency;
    private BigDecimal receiveAmount;
    private String receiveCurrency;
    private BigDecimal exchangeRate;
    private BigDecimal appliedRate;
    private BigDecimal feeAmount;
    private BigDecimal totalDebitAmount;
    private PaymentMethodType paymentMethodType;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private BigDecimal walletAmountUsed;
    private String referralCodeUsed;
    private BigDecimal rateBoostApplied;

    private Integer riskScore;
    private String riskFactors;
    private String visitorId;
}

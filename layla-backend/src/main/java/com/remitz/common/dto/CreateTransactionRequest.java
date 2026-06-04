package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.PaymentMethodType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

    @NotBlank(message = "Quote ID is required")
    private String quoteId;

    @NotNull(message = "Beneficiary ID is required")
    private Long beneficiaryId;

    @NotNull(message = "Corridor ID is required")
    private Long corridorId;

    @NotNull(message = "Delivery method is required")
    private DeliveryMethod deliveryMethod;

    @NotNull(message = "Send amount is required")
    private BigDecimal sendAmount;

    @NotBlank(message = "Send currency is required")
    private String sendCurrency;

    @NotNull(message = "Payment method type is required")
    private PaymentMethodType paymentMethodType;

    private String notes;

    /** Client-generated idempotency key (UUID). Prevents duplicate transaction creation on retries. */
    private String idempotencyKey;

    /** Optional referral code — applies a rate boost if valid */
    private String referralCode;

    /** If true, use available wallet balance to offset the send amount */
    private Boolean useWallet;

    /** Wallet amount the customer wants to apply (capped by wallet balance and sendAmount) */
    private java.math.BigDecimal walletAmountToUse;
}

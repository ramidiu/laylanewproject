package com.remitz.modules.payin.transaction.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePayinTransactionRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    private String beneficiaryId;

    @Valid
    private BeneficiaryDetailsDto beneficiaryDetails;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    private String currency;

    @NotBlank(message = "Payment mode is required")
    private String paymentMode;

    private String receiveCurrency;
    private BigDecimal receiveAmount;
    private String deliveryMethod;

    private String externalReferenceId;

    /**
     * Optional transaction date chosen by the admin/partner (PAYIN only). When set,
     * the transaction's createdAt uses this date (with the current time-of-day), so
     * money can be recorded as sent yesterday/today/a chosen date. If absent, the
     * normal "now" timestamp is used.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate transactionDate;
}

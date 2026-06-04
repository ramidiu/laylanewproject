package com.remitz.modules.payin.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreatePayinTransactionResponse {

    private boolean success;
    private String transactionId;
    private String status;
    private String customerSource;
    private String message;

    public static CreatePayinTransactionResponse success(String transactionId, String status, String customerSource) {
        return CreatePayinTransactionResponse.builder()
                .success(true)
                .transactionId(transactionId)
                .status(status)
                .customerSource(customerSource)
                .message("Transaction created successfully")
                .build();
    }

    public static CreatePayinTransactionResponse failure(String message) {
        return CreatePayinTransactionResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}

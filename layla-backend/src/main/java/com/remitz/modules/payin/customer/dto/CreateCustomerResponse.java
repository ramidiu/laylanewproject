package com.remitz.modules.payin.customer.dto;

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
public class CreateCustomerResponse {

    private boolean success;
    private String customerId;
    private Boolean isVerified;
    private String createdSource;
    private String message;

    public static CreateCustomerResponse success(String customerId) {
        return CreateCustomerResponse.builder()
                .success(true)
                .customerId(customerId)
                .isVerified(true)
                .createdSource("BACKEND")
                .message("Customer created successfully")
                .build();
    }

    public static CreateCustomerResponse failure(String message) {
        return CreateCustomerResponse.builder()
                .success(false)
                .message(message)
                .build();
    }
}

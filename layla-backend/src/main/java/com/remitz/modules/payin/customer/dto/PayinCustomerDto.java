package com.remitz.modules.payin.customer.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayinCustomerDto {

    private String customerId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dob;
    private String nationality;
    private String addressLine1;
    private String city;
    private String country;
    private String postalCode;
    private Boolean isVerified;
    private Boolean hasExpiredDocuments;
    private String createdSource;
    private LocalDateTime createdAt;

    /** Populated only for FRONTEND_USER rows — the users.id needed for the toggle call. */
    private Long userId;
    /** Populated only for FRONTEND_USER rows — mirrors users.payin_enabled. */
    private Boolean payinEnabled;
    /** Total KYC documents this customer has on file (payin_customer_documents OR kyc_documents). */
    private Integer kycDocsCount;
}

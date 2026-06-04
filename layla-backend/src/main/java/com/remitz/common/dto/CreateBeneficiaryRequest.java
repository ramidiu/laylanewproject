package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateBeneficiaryRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Country is required")
    private String country;

    @NotNull(message = "Delivery method is required")
    private DeliveryMethod deliveryMethod;

    private String bankName;
    private String accountNumber;
    private String iban;
    private String swiftBic;
    private String sortCode;
    private String branchState;
    private String branchCity;
    private String mobileNumber;
    private String mobileProvider;
    private String idNumber;
    private String idType;
    private String address;
    private String relationship;
}

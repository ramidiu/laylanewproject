package com.remitz.common.dto;

import com.remitz.common.enums.DeliveryMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateBeneficiaryRequest {

    private String fullName;
    private String country;
    private DeliveryMethod deliveryMethod;
    private String bankName;
    private String accountNumber;
    private String iban;
    private String swiftBic;
    private String sortCode;
    private String mobileNumber;
    private String mobileProvider;
    private String idNumber;
    private String idType;
    private String address;
    private String relationship;
}

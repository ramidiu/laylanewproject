package com.remitz.modules.payin.transaction.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Beneficiary details inline-created from the PayIn Partner create-transaction flow.
 * Mirrors the customer Add Recipient form fields so USI Money has every value it needs
 * when the resulting transaction is later pushed via the USI Money admin page.
 *
 * Validation is delivery-method-dependent so we don't @NotBlank anything here — the
 * service layer enforces conditional requirements (bank vs mobile vs cash).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BeneficiaryDetailsDto {
    private String name;
    private String address;
    private String mobileNumber;

    // Bank deposit
    private String bankName;
    private String sortCode;          // → BeneficiaryEntity.sort_code (Branch Name)
    private String branchState;
    private String branchCity;
    private String accountNumber;
    private String iban;
    private String swiftBic;
    private String ifscCode;

    // Mobile wallet
    private String mobileProvider;

    // Cash collection — packed into bankName/accountNumber/address/city if provided
    private String collectionPointName;
    private String collectionPointCode;
    private String collectionPointAddress;
    private String collectionPointCity;
}

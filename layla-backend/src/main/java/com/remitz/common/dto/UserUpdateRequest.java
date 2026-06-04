package com.remitz.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    private String firstName;
    private String lastName;
    private String phone;
    private LocalDate dateOfBirth;
    private String country;
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String postcode;
    private String preferredLanguage;

    // Admin fields
    private String status;
    private String kycTier;
}

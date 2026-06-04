package com.remitz.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoAccessRequest {

    @NotBlank
    private String fullName;

    @NotBlank
    private String country;

    @NotBlank
    @Email
    private String email;

    @NotBlank
    private String phone;

    private String role;
}

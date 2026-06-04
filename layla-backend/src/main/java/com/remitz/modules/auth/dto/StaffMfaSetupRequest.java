package com.remitz.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to complete forced MFA setup for staff accounts. "
        + "The secret and code come from the login response (mfaSetupSecret) and your authenticator app respectively.")
public class StaffMfaSetupRequest {

    @NotBlank
    @Email
    @Schema(description = "Staff email address", example = "admin@laylamoneytransfer.com")
    private String email;

    @NotBlank
    @Schema(description = "Staff password", example = "AdminUser@2026")
    private String password;

    @NotBlank
    @Schema(description = "TOTP secret returned by POST /api/auth/login in the mfaSetupSecret field",
            example = "O4YIIV7VMFY3BE5QP7COBUOYTTY6KYDV")
    private String secret;

    @NotBlank
    @Schema(description = "6-digit TOTP code from your authenticator app (Google Authenticator, Authy, etc.)",
            example = "123456")
    private String code;
}

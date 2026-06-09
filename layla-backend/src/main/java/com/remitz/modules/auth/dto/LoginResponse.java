package com.remitz.modules.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Login response containing JWT tokens or MFA challenge")
public class LoginResponse {
    @Schema(description = "JWT access token (null if MFA required)", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String accessToken;
    @Schema(description = "JWT refresh token", example = "eyJhbGciOiJIUzI1NiJ9...")
    private String refreshToken;
    @Schema(description = "Temporary MFA token (set when MFA is required)")
    private String mfaToken;
    @Schema(description = "Whether MFA verification is required to complete login", example = "false")
    private boolean mfaRequired;
    @Schema(description = "Whether MFA setup is required (first-time staff login)", example = "false")
    private boolean mfaSetupRequired;
    @Schema(description = "TOTP secret for MFA setup (only during setup)")
    private String mfaSetupSecret;
    @Schema(description = "QR code data URI for authenticator app (only during setup)")
    private String mfaSetupQrCodeUri;
    @Schema(description = "User UUID")
    private String userUuid;
    @Schema(description = "Token type", example = "Bearer")
    private String tokenType;
    @Schema(description = "Access token expiry in seconds", example = "3600")
    private long expiresIn;
    @Schema(description = "Whether the user must change their password before continuing (default-password accounts)", example = "false")
    private boolean passwordChangeRequired;
}

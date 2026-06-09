package com.remitz.modules.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for {@code POST /api/account/delete-request}.
 *
 * <p>{@code userId} is accepted for client convenience but is NOT trusted — the
 * account acted upon is always the authenticated user resolved from the JWT, so
 * a user can never request deletion of another account.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Account deletion request")
public class AccountDeletionRequest {

    @Schema(description = "Client-supplied user id/uuid (ignored server-side; the authenticated user is used)")
    private String userId;

    @Size(max = 1000, message = "Reason must be 1000 characters or fewer")
    @Schema(description = "Optional reason for deletion")
    private String reason;
}

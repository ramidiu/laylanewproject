package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.EntityType;
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
public class ScreeningRequest {

    @NotBlank(message = "Full name is required")
    private String fullName;

    private String country;

    private String dateOfBirth;

    @NotNull(message = "Entity type is required")
    private EntityType entityType;

    @NotNull(message = "Entity ID is required")
    private Long entityId;
}

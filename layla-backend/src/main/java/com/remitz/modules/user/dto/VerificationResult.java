package com.remitz.modules.user.dto;

import com.remitz.common.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationResult {

    private VerificationStatus status;
    private String providerReference;
    private String resultData;
}

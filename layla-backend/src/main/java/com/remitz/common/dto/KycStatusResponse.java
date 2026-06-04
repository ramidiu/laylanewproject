package com.remitz.common.dto;

import com.remitz.common.enums.KycTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycStatusResponse {

    private String userId;
    private KycTier currentTier;
    private String overallStatus;
    private List<KycDocumentResponse> documents;
    private List<String> verifications;
    private List<String> nextTierRequirements;
}

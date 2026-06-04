package com.remitz.modules.user.service;

import com.remitz.modules.user.dto.ScreeningResult;
import com.remitz.modules.user.dto.VerificationResult;
import com.remitz.modules.user.entity.KycDocumentEntity;

public interface KycVerificationProvider {

    VerificationResult verifyIdentity(String userId, KycDocumentEntity document);

    VerificationResult checkLiveness(String userId, byte[] selfieImage);

    ScreeningResult screenPEP(String fullName, String dateOfBirth, String country);

    ScreeningResult screenSanctions(String fullName, String country);
}

package com.remitz.modules.user.service;

import com.remitz.common.enums.ScreeningStatus;
import com.remitz.common.enums.VerificationStatus;
import com.remitz.modules.user.dto.ScreeningResult;
import com.remitz.modules.user.dto.VerificationResult;
import com.remitz.modules.user.entity.KycDocumentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Primary
@Slf4j
public class OpenSanctionsKycProvider implements KycVerificationProvider {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.compliance-service.url:http://localhost:8085}")
    private String complianceBaseUrl;

    @Override
    public VerificationResult verifyIdentity(String userId, KycDocumentEntity document) {
        log.info("Identity verification (manual review) for user: {}, docType: {}",
                userId, document.getDocumentType());
        return VerificationResult.builder()
                .status(VerificationStatus.PENDING)
                .providerReference("MANUAL-" + UUID.randomUUID().toString().substring(0, 8))
                .resultData("{\"message\": \"Awaiting manual review\"}")
                .build();
    }

    @Override
    public VerificationResult checkLiveness(String userId, byte[] selfieImage) {
        log.info("Liveness check (manual review) for user: {}", userId);
        return VerificationResult.builder()
                .status(VerificationStatus.PENDING)
                .providerReference("MANUAL-" + UUID.randomUUID().toString().substring(0, 8))
                .resultData("{\"message\": \"Awaiting manual liveness review\"}")
                .build();
    }

    @Override
    public ScreeningResult screenPEP(String fullName, String dateOfBirth, String country) {
        return callComplianceScreen(fullName, dateOfBirth, country, "PEP");
    }

    @Override
    public ScreeningResult screenSanctions(String fullName, String country) {
        return callComplianceScreen(fullName, null, country, "SANCTIONS");
    }

    private ScreeningResult callComplianceScreen(String fullName, String dob, String country, String kind) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("fullName", fullName);
            if (country != null) body.put("country", country);
            if (dob != null) body.put("dateOfBirth", dob);
            body.put("entityType", "CUSTOMER");
            body.put("entityId", 0L);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<List> response = restTemplate.exchange(
                    complianceBaseUrl + "/api/compliance/screen",
                    org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    List.class);

            List<?> results = response.getBody();
            if (results == null || results.isEmpty()) {
                return clearResult(kind, "Compliance service returned no results");
            }

            BigDecimal bestScore = BigDecimal.ZERO;
            boolean hit = false;
            String details = null;
            for (Object row : results) {
                if (!(row instanceof Map)) continue;
                Map<?, ?> m = (Map<?, ?>) row;
                Object status = m.get("status");
                Object score = m.get("matchScore");
                if ("POTENTIAL_MATCH".equals(String.valueOf(status)) || "CONFIRMED_MATCH".equals(String.valueOf(status))) {
                    hit = true;
                    if (score instanceof Number) {
                        BigDecimal s = new BigDecimal(score.toString());
                        if (s.compareTo(bestScore) > 0) {
                            bestScore = s;
                            details = objectMapper.writeValueAsString(m);
                        }
                    }
                }
            }

            if (hit) {
                return ScreeningResult.builder()
                        .status(ScreeningStatus.POTENTIAL_MATCH)
                        .matchScore(bestScore.setScale(2, RoundingMode.HALF_UP))
                        .matchDetails(details)
                        .build();
            }
            return clearResult(kind, "No matches above threshold");
        } catch (Exception e) {
            log.error("{} screening call failed for '{}': {}", kind, fullName, e.getMessage());
            return ScreeningResult.builder()
                    .status(ScreeningStatus.CLEAR)
                    .matchScore(BigDecimal.ZERO)
                    .matchDetails("{\"error\": \"" + e.getClass().getSimpleName() + "\"}")
                    .build();
        }
    }

    private ScreeningResult clearResult(String kind, String message) {
        return ScreeningResult.builder()
                .status(ScreeningStatus.CLEAR)
                .matchScore(BigDecimal.ZERO)
                .matchDetails("{\"kind\": \"" + kind + "\", \"message\": \"" + message + "\"}")
                .build();
    }
}

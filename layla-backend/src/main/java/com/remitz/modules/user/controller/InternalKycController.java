package com.remitz.modules.user.controller;

import com.remitz.modules.user.entity.KycDocumentTypeConfig;
import com.remitz.modules.user.repository.KycDocumentTypeConfigRepository;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.user.service.KycService;
import com.remitz.common.dto.KycDocumentResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal service-to-service endpoints for KYC lookups.
 * Not authenticated — protected by network-level restrictions only.
 * Used by transaction-service to enforce KYC verification before creating transactions.
 */
@RestController
@RequestMapping("/internal/kyc")
@RequiredArgsConstructor
@Slf4j
@Hidden
public class InternalKycController {

    private final KycService kycService;
    private final UserRepository userRepository;
    private final KycDocumentTypeConfigRepository docTypeRepo;

    private static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
            Map.entry("GB", "United Kingdom"), Map.entry("US", "United States"),
            Map.entry("AU", "Australia"), Map.entry("DE", "Germany"),
            Map.entry("IN", "India"), Map.entry("PK", "Pakistan"),
            Map.entry("NG", "Nigeria"), Map.entry("GH", "Ghana"),
            Map.entry("PH", "Philippines"), Map.entry("KE", "Kenya"),
            Map.entry("BD", "Bangladesh"), Map.entry("ZA", "South Africa"),
            Map.entry("NP", "Nepal"), Map.entry("AE", "UAE"),
            Map.entry("LK", "Sri Lanka"), Map.entry("UG", "Uganda"),
            Map.entry("TZ", "Tanzania"), Map.entry("EG", "Egypt"),
            Map.entry("FR", "France"), Map.entry("IT", "Italy"),
            Map.entry("ES", "Spain"), Map.entry("MA", "Morocco"));

    /**
     * Auto-seed KYC document types for a country if none exist. Called by transaction-service's
     * CorridorAutoCreateService when a payout type or payment method is activated — ensures the
     * KYC upload page always has document-type options for every enabled country.
     */
    @PostMapping("/seed-document-types/{countryCode}")
    public ResponseEntity<Map<String, Object>> seedDocumentTypes(
            @PathVariable String countryCode,
            @RequestBody(required = false) Map<String, String> body) {

        String cc = countryCode.toUpperCase().trim();
        String countryName = body != null && body.get("countryName") != null
                ? body.get("countryName")
                : COUNTRY_NAMES.getOrDefault(cc, cc);

        // Check if IDENTITY docs already exist for this country
        List<KycDocumentTypeConfig> existingIdentity = docTypeRepo
                .findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder(cc, "IDENTITY", true);
        List<KycDocumentTypeConfig> existingAddress = docTypeRepo
                .findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder(cc, "ADDRESS", true);

        int created = 0;

        if (existingIdentity.isEmpty()) {
            String[][] idDocs = {
                    {"Passport", "1", "true", "Passport Number", null, "true", "true"},
                    {"National ID", "2", "true", "ID Number", null, "false", "false"},
                    {"Driver's Licence", "2", "true", "Licence Number", null, "true", "false"}
            };
            int order = 1;
            for (String[] d : idDocs) {
                docTypeRepo.save(KycDocumentTypeConfig.builder()
                        .countryCode(cc)
                        .countryName(countryName)
                        .category("IDENTITY")
                        .documentName(d[0])
                        .sides(Integer.parseInt(d[1]))
                        .hasIdNumber(Boolean.parseBoolean(d[2]))
                        .idNumberLabel(d[3])
                        .idNumberFormat(d[4])
                        .hasExpiry(Boolean.parseBoolean(d[5]))
                        .hasIssueDate(Boolean.parseBoolean(d[6]))
                        .isActive(true)
                        .displayOrder(order++)
                        .build());
                created++;
            }
            log.info("Seeded {} IDENTITY document types for country {}", idDocs.length, cc);
        }

        if (existingAddress.isEmpty()) {
            String[][] addrDocs = {
                    {"Utility Bill"},
                    {"Bank Statement"},
                    {"Council Tax Bill"}
            };
            int order = 1;
            for (String[] d : addrDocs) {
                docTypeRepo.save(KycDocumentTypeConfig.builder()
                        .countryCode(cc)
                        .countryName(countryName)
                        .category("ADDRESS")
                        .documentName(d[0])
                        .sides(1)
                        .hasIdNumber(false)
                        .hasExpiry(false)
                        .hasIssueDate(false)
                        .isActive(true)
                        .displayOrder(order++)
                        .build());
                created++;
            }
            log.info("Seeded {} ADDRESS document types for country {}", addrDocs.length, cc);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("countryCode", cc);
        result.put("created", created);
        result.put("identityDocs", existingIdentity.isEmpty() ? 3 : existingIdentity.size());
        result.put("addressDocs", existingAddress.isEmpty() ? 3 : existingAddress.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/documents/{uuid}")
    public ResponseEntity<Map<String, Object>> getDocumentsByUuid(@PathVariable String uuid) {
        return userRepository.findByUuid(uuid)
                .map(user -> {
                    List<KycDocumentResponse> docs = kycService.getDocuments(user.getId());
                    int approved = 0, pending = 0, rejected = 0, expired = 0;
                    java.time.LocalDate today = java.time.LocalDate.now();
                    for (KycDocumentResponse d : docs) {
                        if (d.getStatus() == null) continue;
                        String s = d.getStatus().name();
                        if ("APPROVED".equals(s)) {
                            // An APPROVED document with an expiry date in the past is expired.
                            if (d.getExpiryDate() != null && d.getExpiryDate().isBefore(today)) {
                                expired++;
                            } else {
                                approved++;
                            }
                        }
                        else if ("PENDING".equals(s)) pending++;
                        else if ("REJECTED".equals(s)) rejected++;
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("userId", user.getId());
                    result.put("uuid", uuid);
                    result.put("total", docs.size());
                    result.put("approved", approved);
                    result.put("pending", pending);
                    result.put("rejected", rejected);
                    result.put("expired", expired);
                    boolean allDocsVerified = docs.size() > 0 && pending == 0 && rejected == 0
                            && expired == 0 && approved > 0;
                    result.put("allVerified", allDocsVerified);

                    return ResponseEntity.ok(result);
                })
                .orElseGet(() -> {
                    Map<String, Object> empty = new LinkedHashMap<>();
                    empty.put("uuid", uuid);
                    empty.put("total", 0);
                    empty.put("approved", 0);
                    empty.put("pending", 0);
                    empty.put("rejected", 0);
                    empty.put("allVerified", false);
                    return ResponseEntity.ok(empty);
                });
    }
}

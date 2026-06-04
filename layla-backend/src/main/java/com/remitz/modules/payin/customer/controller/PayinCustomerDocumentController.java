package com.remitz.modules.payin.customer.controller;

import com.remitz.common.enums.KycDocumentStatus;
import com.remitz.common.enums.KycDocumentType;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.payin.customer.entity.PayinCustomerDocumentEntity;
import com.remitz.modules.payin.customer.repository.PayinCustomerDocumentRepository;
import com.remitz.modules.payin.customer.repository.PayinCustomerRepository;
import com.remitz.modules.user.entity.KycDocumentEntity;
import com.remitz.modules.user.repository.KycDocumentRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payin/customer")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PayIn Customer Documents", description = "Document uploads for PayIn customers")
public class PayinCustomerDocumentController {

    private final PayinCustomerDocumentRepository documentRepository;
    private final PayinCustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final KycDocumentRepository kycDocumentRepository;

    @Value("${app.kyc.upload-dir:./kyc-uploads}")
    private String uploadDir;

    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final java.util.Set<String> ALLOWED_EXT =
            java.util.Set.of(".jpg", ".jpeg", ".png", ".pdf");

    @PostMapping("/{customerId}/document")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Upload a customer document")
    public ResponseEntity<Map<String, Object>> uploadDocument(
            @PathVariable String customerId,
            @RequestParam("docSide") String docSide,
            @RequestParam("docCategory") String docCategory,
            @RequestParam(value = "documentNumber", required = false) String documentNumber,
            @RequestParam(value = "issueDate", required = false) String issueDate,
            @RequestParam(value = "expiryDate", required = false) String expiryDate,
            @RequestParam("file") MultipartFile file) {

        boolean isPayinCustomer = customerRepository.existsByCustomerId(customerId);
        UserEntity frontendUser = null;
        if (!isPayinCustomer) {
            frontendUser = userRepository.findByUuid(customerId).orElse(null);
            if (frontendUser == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Customer not found"));
            }
        }

        if (file.isEmpty() || file.getSize() > MAX_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "File must be non-empty and under 10 MB"));
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        int dotIdx = originalFilename.lastIndexOf('.');
        String ext = dotIdx >= 0 ? originalFilename.substring(dotIdx).toLowerCase() : "";
        if (!ALLOWED_EXT.contains(ext)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Allowed file types: JPG, PNG, PDF"));
        }

        String savedPath = saveFile(customerId, docSide, file, ext);

        // Frontend / imported users → save to kyc_documents (users-side)
        if (frontendUser != null) {
            KycDocumentType type = mapToKycDocumentType(docCategory);
            KycDocumentEntity ku = KycDocumentEntity.builder()
                    .userId(frontendUser.getId())
                    .documentType(type)
                    .documentNumber(documentNumber)
                    .filePath(savedPath)
                    .status(KycDocumentStatus.APPROVED)
                    .issueDate(issueDate != null && !issueDate.isBlank() ? LocalDate.parse(issueDate) : null)
                    .expiryDate(expiryDate != null && !expiryDate.isBlank() ? LocalDate.parse(expiryDate) : null)
                    .build();
            KycDocumentEntity savedDoc = kycDocumentRepository.save(ku);
            log.info("Uploaded kyc_documents row {} for frontend user {} (category={})",
                    savedDoc.getId(), customerId, docCategory);
            return ResponseEntity.ok(Map.of("success", true, "id", savedDoc.getId(), "path", savedPath));
        }

        PayinCustomerDocumentEntity doc = PayinCustomerDocumentEntity.builder()
                .customerId(customerId)
                .docSide(docSide.toUpperCase())
                .docCategory(docCategory.toUpperCase())
                .documentNumber(documentNumber)
                .issueDate(issueDate != null && !issueDate.isBlank() ? LocalDate.parse(issueDate) : null)
                .expiryDate(expiryDate != null && !expiryDate.isBlank() ? LocalDate.parse(expiryDate) : null)
                .filePath(savedPath)
                .fileName(originalFilename)
                .status("PENDING")
                .build();

        PayinCustomerDocumentEntity saved = documentRepository.save(doc);
        log.info("PayIn customer document uploaded — customerId: {}, side: {}, docId: {}", customerId, docSide, saved.getId());

        return ResponseEntity.ok(Map.of("success", true, "documentId", saved.getId(), "message", "Document uploaded successfully"));
    }

    @GetMapping("/{customerId}/kyc-existing")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Existing KYC data for a customer",
            description = "Used by the create-transaction KYC step to pre-populate the form when the customer already uploaded documents through the customer app.")
    public ResponseEntity<Map<String, Object>> getExistingKyc(@PathVariable String customerId) {
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("hasIdentity", false);
        out.put("hasAddress", false);

        UserEntity user = userRepository.findByUuid(customerId).orElse(null);
        if (user == null) {
            // Fall back to payin_customers row if any
            return ResponseEntity.ok(out);
        }

        out.put("dob", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : "");

        List<KycDocumentEntity> docs = kycDocumentRepository.findByUserId(user.getId());
        for (KycDocumentEntity d : docs) {
            String type = d.getDocumentType() != null ? d.getDocumentType().name() : "";
            boolean isAddress = type.equals("PROOF_OF_ADDRESS");
            boolean isIdentity = type.equals("PASSPORT") || type.equals("DRIVING_LICENCE") || type.equals("NATIONAL_ID");
            String number = d.getDocumentNumber() != null ? d.getDocumentNumber() : "";
            if (isIdentity && !number.endsWith("_BACK")) {
                out.put("hasIdentity", true);
                out.put("idType", type);
                out.put("idDocumentNumber", number);
                out.put("idIssueDate", d.getIssueDate() != null ? d.getIssueDate().toString() : "");
                out.put("idExpiryDate", d.getExpiryDate() != null ? d.getExpiryDate().toString() : "");
                out.put("idFilePath", d.getFilePath());
                out.put("idStatus", d.getStatus() != null ? d.getStatus().name() : "");
                out.put("idDocId", d.getId());
            } else if (isAddress) {
                out.put("hasAddress", true);
                // The customer-app flow does not capture a sub-type — surface a sensible default
                out.put("addressType", "UTILITY_BILL");
                out.put("addressFilePath", d.getFilePath());
                out.put("addressStatus", d.getStatus() != null ? d.getStatus().name() : "");
                out.put("addressDocId", d.getId());
            }
        }
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{customerId}/verify")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Verify a customer who already has KYC documents on file",
            description = "Approves outstanding documents and bumps the user's KYC tier to TIER_2 — used by the partner KYC step when re-upload is unnecessary.")
    public ResponseEntity<Map<String, Object>> verifyExisting(@PathVariable String customerId) {
        UserEntity user = userRepository.findByUuid(customerId).orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Customer not found"));
        }

        List<KycDocumentEntity> docs = kycDocumentRepository.findByUserId(user.getId());
        boolean hasIdentity = docs.stream().anyMatch(d -> {
            String t = d.getDocumentType() != null ? d.getDocumentType().name() : "";
            return t.equals("PASSPORT") || t.equals("DRIVING_LICENCE") || t.equals("NATIONAL_ID");
        });
        boolean hasAddress = docs.stream().anyMatch(d ->
                d.getDocumentType() != null && d.getDocumentType().name().equals("PROOF_OF_ADDRESS"));

        if (!hasIdentity || !hasAddress) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Customer is missing required documents — cannot verify without re-upload",
                    "hasIdentity", hasIdentity,
                    "hasAddress", hasAddress));
        }

        // Approve any PENDING docs
        for (KycDocumentEntity d : docs) {
            if (d.getStatus() == null || d.getStatus() == KycDocumentStatus.PENDING) {
                d.setStatus(KycDocumentStatus.APPROVED);
                kycDocumentRepository.save(d);
            }
        }

        // Bump tier so the rest of the system treats the user as verified
        if (user.getKycTier() == null || user.getKycTier().name().equals("TIER_0")) {
            user.setKycTier(com.remitz.common.enums.KycTier.TIER_2);
            userRepository.save(user);
        }

        // Mirror to payin_customers if a row exists
        customerRepository.findByCustomerId(customerId).ifPresent(pc -> {
            pc.setIsVerified(true);
            customerRepository.save(pc);
        });

        log.info("Customer {} marked verified using existing KYC documents", customerId);
        return ResponseEntity.ok(Map.of("success", true, "kycTier", user.getKycTier().name()));
    }

    @GetMapping("/{customerId}/documents")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List documents for a customer")
    public ResponseEntity<List<Map<String, Object>>> getDocuments(@PathVariable String customerId) {
        List<Map<String, Object>> docs = documentRepository.findByCustomerId(customerId).stream()
                .map(d -> Map.<String, Object>of(
                        "id", d.getId(),
                        "docSide", d.getDocSide(),
                        "docCategory", d.getDocCategory(),
                        "documentNumber", d.getDocumentNumber() != null ? d.getDocumentNumber() : "",
                        "issueDate", d.getIssueDate() != null ? d.getIssueDate().toString() : "",
                        "expiryDate", d.getExpiryDate() != null ? d.getExpiryDate().toString() : "",
                        "fileName", d.getFileName(),
                        "status", d.getStatus()
                )).collect(Collectors.toList());
        return ResponseEntity.ok(docs);
    }

    @GetMapping("/document/{docId}/file")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Serve a document file")
    public ResponseEntity<byte[]> getFile(@PathVariable Long docId) {
        PayinCustomerDocumentEntity doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) return ResponseEntity.notFound().build();

        try {
            Path path = Paths.get(doc.getFilePath());
            byte[] bytes = Files.readAllBytes(path);
            String ct = probeContentType(doc.getFileName());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getFileName() + "\"")
                    .contentType(MediaType.parseMediaType(ct))
                    .body(bytes);
        } catch (IOException e) {
            log.error("Failed to read document file: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/kyc-document/{docId}/file")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Serve a kyc_documents file (frontend / imported users)")
    public ResponseEntity<byte[]> getKycDocFile(@PathVariable Long docId) {
        KycDocumentEntity doc = kycDocumentRepository.findById(docId).orElse(null);
        if (doc == null || doc.getFilePath() == null) return ResponseEntity.notFound().build();
        try {
            Path path = Paths.get(doc.getFilePath());
            byte[] bytes = Files.readAllBytes(path);
            String fileName = path.getFileName().toString();
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .contentType(MediaType.parseMediaType(probeContentType(fileName)))
                    .body(bytes);
        } catch (IOException e) {
            log.error("Failed to read kyc_documents file: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private String saveFile(String customerId, String docSide, MultipartFile file, String ext) {
        try {
            Path dir = Paths.get(uploadDir, "payin", customerId);
            Files.createDirectories(dir);
            String filename = docSide.toLowerCase() + "_" + System.currentTimeMillis() + ext;
            Path dest = dir.resolve(filename);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest.toString();
        } catch (IOException e) {
            throw new RuntimeException("Failed to save document file", e);
        }
    }

    private String probeContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    /** Map the partner's docCategory string (e.g. "PASSPORT", "UTILITY_BILL") to a KycDocumentType. */
    private KycDocumentType mapToKycDocumentType(String docCategory) {
        if (docCategory == null) return KycDocumentType.PROOF_OF_ADDRESS;
        String c = docCategory.trim().toUpperCase();
        switch (c) {
            case "PASSPORT":          return KycDocumentType.PASSPORT;
            case "DRIVING_LICENCE":
            case "DRIVING_LICENSE":   return KycDocumentType.DRIVING_LICENCE;
            case "NATIONAL_ID":
            case "RESIDENCE_PERMIT":  return KycDocumentType.NATIONAL_ID;
            case "IDENTITY":          return KycDocumentType.PASSPORT;     // generic identity fallback
            default:                  return KycDocumentType.PROOF_OF_ADDRESS;  // utility bill, bank statement, council tax, etc.
        }
    }
}

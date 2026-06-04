package com.remitz.modules.user.controller;

import com.remitz.common.dto.*;
import com.remitz.common.enums.KycDocumentStatus;
import com.remitz.common.enums.KycDocumentType;
import com.remitz.common.enums.VerificationType;
import com.remitz.modules.user.entity.KycDocumentEntity;
import com.remitz.modules.user.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC Management", description = "APIs for KYC document management and verification")
public class KycController {

    private final KycService kycService;
    private final com.remitz.modules.auth.repository.UserRepository userRepository;

    private Long resolveUserId(String userIdOrUuid) {
        try {
            return Long.parseLong(userIdOrUuid);
        } catch (NumberFormatException e) {
            return userRepository.findByUuid(userIdOrUuid)
                    .map(u -> u.getId())
                    .orElseThrow(() -> new com.remitz.common.exception.ResourceNotFoundException("User", "uuid", userIdOrUuid));
        }
    }

    /** True when the current principal can approve KYC (i.e. is an admin/compliance user). */
    private boolean hasApproveKycAuthority() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "user:approve_kyc".equals(a.getAuthority()));
    }

    @Operation(summary = "Upload KYC document", description = "Upload a KYC document for verification")
    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<KycDocumentResponse>> uploadDocument(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Document type") @RequestParam KycDocumentType type,
            @Parameter(description = "Document number") @RequestParam(required = false) String documentNumber,
            @Parameter(description = "Document file") @RequestParam MultipartFile file,
            @RequestParam(required = false) String issueDate,
            @RequestParam(required = false) String expiryDate,
            HttpServletRequest request) {
        String ipAddress = getClientIpAddress(request);
        LocalDate parsedIssueDate = issueDate != null ? LocalDate.parse(issueDate) : null;
        LocalDate parsedExpiryDate = expiryDate != null ? LocalDate.parse(expiryDate) : null;
        // If an admin (has KYC-approval authority) uploads on the user's behalf, auto-approve
        // so the user is verified + activated immediately. Self-uploads stay PENDING for review.
        boolean autoApprove = hasApproveKycAuthority();
        KycDocumentResponse response = kycService.uploadDocument(resolveUserId(userId), type, documentNumber, file, ipAddress, parsedIssueDate, parsedExpiryDate, autoApprove);
        return ResponseEntity.ok(ApiResponse.<KycDocumentResponse>builder()
                .success(true)
                .data(response)
                .message("Document uploaded successfully")
                .build());
    }

    @Operation(summary = "Get KYC documents", description = "Get all KYC documents for a user")
    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<KycDocumentResponse>>> getDocuments(
            @Parameter(description = "User ID") @PathVariable String userId) {
        List<KycDocumentResponse> documents = kycService.getDocuments(resolveUserId(userId));
        return ResponseEntity.ok(ApiResponse.<List<KycDocumentResponse>>builder()
                .success(true)
                .data(documents)
                .message("Documents retrieved successfully")
                .build());
    }

    @Operation(summary = "Review KYC document", description = "Approve or reject a KYC document (admin only)")
    @PutMapping("/documents/{docId}")
    @PreAuthorize("hasPermission(null, 'user:approve_kyc')")
    public ResponseEntity<ApiResponse<KycDocumentResponse>> reviewDocument(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Document ID") @PathVariable Long docId,
            @Valid @RequestBody KycDocumentReviewRequest reviewRequest,
            HttpServletRequest request) {
        String ipAddress = getClientIpAddress(request);
        // Extract reviewer ID from the security context (JWT subject is the UUID, we use userId from path as fallback)
        KycDocumentResponse response = kycService.reviewDocument(
                docId,
                reviewRequest.getStatus(),
                reviewRequest.getRejectionReason(),
                resolveUserId(userId),
                ipAddress);
        return ResponseEntity.ok(ApiResponse.<KycDocumentResponse>builder()
                .success(true)
                .data(response)
                .message("Document reviewed successfully")
                .build());
    }

    @Operation(summary = "Trigger verification", description = "Trigger a KYC verification check for a user")
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<Void>> triggerVerification(
            @Parameter(description = "User ID") @PathVariable String userId,
            @Parameter(description = "Verification type") @RequestParam VerificationType type) {
        kycService.triggerVerification(resolveUserId(userId), type);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Verification triggered successfully")
                .build());
    }

    @Operation(summary = "Get KYC status", description = "Get the current KYC status and tier information for a user")
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<KycStatusResponse>> getKycStatus(
            @Parameter(description = "User ID") @PathVariable String userId) {
        KycStatusResponse status = kycService.getKycStatus(resolveUserId(userId));
        return ResponseEntity.ok(ApiResponse.<KycStatusResponse>builder()
                .success(true)
                .data(status)
                .message("KYC status retrieved successfully")
                .build());
    }

    @Operation(summary = "Screen PEP/Sanctions", description = "Run PEP and sanctions screening for a user")
    @PostMapping("/screening")
    public ResponseEntity<ApiResponse<List<ScreeningResponse>>> screenPepSanctions(
            @Parameter(description = "User ID") @PathVariable String userId,
            HttpServletRequest request) {
        String ipAddress = getClientIpAddress(request);
        List<ScreeningResponse> responses = kycService.screenPepSanctions(resolveUserId(userId), ipAddress);
        return ResponseEntity.ok(ApiResponse.<List<ScreeningResponse>>builder()
                .success(true)
                .data(responses)
                .message("Screening completed successfully")
                .build());
    }

    @GetMapping("/documents/{docId}/file")
    public ResponseEntity<org.springframework.core.io.Resource> getDocumentFile(
            @PathVariable String userId,
            @PathVariable Long docId) {
        Long resolvedUserId = resolveUserId(userId);
        KycDocumentEntity doc = kycService.getDocumentEntity(docId);
        if (doc == null || !doc.getUserId().equals(resolvedUserId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(doc.getFilePath());
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(path.toUri());
            if (!resource.exists()) return ResponseEntity.notFound().build();
            String contentType = java.nio.file.Files.probeContentType(path);
            if (contentType == null) contentType = "application/octet-stream";
            return ResponseEntity.ok()
                    .header("Content-Type", contentType)
                    .header("Content-Disposition", "inline; filename=\"" + path.getFileName() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

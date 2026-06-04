package com.remitz.modules.user.service;

import com.remitz.common.dto.KycDocumentResponse;
import com.remitz.common.dto.KycStatusResponse;
import com.remitz.common.dto.ScreeningResponse;
import com.remitz.common.enums.*;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.dto.ScreeningResult;
import com.remitz.modules.user.dto.VerificationResult;
import com.remitz.modules.user.config.RedisPublisher;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.user.entity.*;
import com.remitz.modules.user.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KycService {

    private final KycDocumentRepository kycDocumentRepository;
    private final KycVerificationRepository kycVerificationRepository;
    private final KycAuditLogRepository kycAuditLogRepository;
    private final UserRepository userRepository;
    private final KycVerificationProvider kycVerificationProvider;
    private final KycTierEvaluator kycTierEvaluator;
    private final RedisPublisher redisPublisher;

    @Value("${app.kyc.upload-dir}")
    private String uploadDir;

    @Value("${app.kyc.max-file-size-mb}")
    private int maxFileSizeMb;

    @Value("${app.kyc.allowed-extensions}")
    private String allowedExtensions;

    @Transactional
    public KycDocumentResponse uploadDocument(Long userId, KycDocumentType type,
                                               String documentNumber, MultipartFile file,
                                               String ipAddress, java.time.LocalDate issueDate,
                                               java.time.LocalDate expiryDate,
                                               boolean autoApprove) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        validateFile(file);

        String fileHash = calculateSha256(file);
        String savedFilePath = saveFile(userId, type, file);

        // When an admin uploads on the user's behalf, approve immediately so the user is
        // verified + activated in one step (no separate review step needed).
        KycDocumentEntity document = KycDocumentEntity.builder()
                .userId(userId)
                .documentType(type)
                .documentNumber(documentNumber)
                .filePath(savedFilePath)
                .fileHash(fileHash)
                .status(autoApprove ? KycDocumentStatus.APPROVED : KycDocumentStatus.PENDING)
                .verifiedAt(autoApprove ? LocalDateTime.now() : null)
                .issueDate(issueDate)
                .expiryDate(expiryDate)
                .build();

        KycDocumentEntity saved = kycDocumentRepository.save(document);
        log.info("KYC document uploaded: userId={}, type={}, docId={}, autoApprove={}",
                userId, type, saved.getId(), autoApprove);

        KycAuditLogEntity auditLog = KycAuditLogEntity.builder()
                .userId(userId)
                .action(autoApprove ? "DOCUMENT_UPLOADED_AND_APPROVED" : "DOCUMENT_UPLOADED")
                .actorId(userId)
                .details(String.format("{\"documentId\": %d, \"documentType\": \"%s\", \"fileHash\": \"%s\", \"autoApprove\": %s}",
                        saved.getId(), type, fileHash, autoApprove))
                .ipAddress(ipAddress)
                .build();
        kycAuditLogRepository.save(auditLog);

        // Admin-approved upload: upgrade tier + activate the account, then notify the user.
        if (autoApprove) {
            kycTierEvaluator.evaluateAndUpgrade(userId);
            triggerRescreenOnIdentityApproval(saved);
            try {
                Map<String, String> vars = new HashMap<>();
                vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "Customer");
                vars.put("documentType", type != null ? type.toString().replace("_", " ") : "Document");
                redisPublisher.publishKycEvent("KYC_DOCUMENT_APPROVED",
                        user.getId(), user.getEmail(), user.getFirstName(), vars);
            } catch (Exception e) {
                log.warn("Failed to publish KYC auto-approval event: {}", e.getMessage());
            }
        }

        return toDocumentResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<KycDocumentResponse> getDocuments(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        return kycDocumentRepository.findByUserId(userId).stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public KycDocumentResponse reviewDocument(Long documentId, KycDocumentStatus status,
                                               String rejectionReason, Long reviewerId,
                                               String ipAddress) {
        KycDocumentEntity document = kycDocumentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("KYC Document", "id", documentId));

        if (document.getStatus() != KycDocumentStatus.PENDING) {
            throw new RemitzException(
                    "Document is not in PENDING status, current status: " + document.getStatus(),
                    HttpStatus.BAD_REQUEST);
        }

        if (status == KycDocumentStatus.REJECTED && (rejectionReason == null || rejectionReason.isBlank())) {
            throw new RemitzException("Rejection reason is required when rejecting a document",
                    HttpStatus.BAD_REQUEST);
        }

        document.setStatus(status);
        document.setVerifiedBy(reviewerId);
        document.setVerifiedAt(LocalDateTime.now());
        if (status == KycDocumentStatus.REJECTED) {
            document.setRejectionReason(rejectionReason);
        }

        KycDocumentEntity saved = kycDocumentRepository.save(document);
        log.info("KYC document reviewed: docId={}, status={}, reviewer={}", documentId, status, reviewerId);

        KycAuditLogEntity auditLog = KycAuditLogEntity.builder()
                .userId(document.getUserId())
                .action("STATUS_CHANGED")
                .actorId(reviewerId)
                .actorRole("ADMIN")
                .details(String.format("{\"documentId\": %d, \"newStatus\": \"%s\", \"rejectionReason\": %s}",
                        documentId, status,
                        rejectionReason != null ? "\"" + rejectionReason + "\"" : "null"))
                .ipAddress(ipAddress)
                .build();
        kycAuditLogRepository.save(auditLog);

        if (status == KycDocumentStatus.APPROVED) {
            kycTierEvaluator.evaluateAndUpgrade(document.getUserId());
            triggerRescreenOnIdentityApproval(document);
        }

        // Publish KYC event for email notification
        try {
            UserEntity user = userRepository.findById(document.getUserId()).orElse(null);
            if (user != null) {
                String docType = document.getDocumentType() != null
                        ? document.getDocumentType().toString().replace("_", " ")
                        : "Document";
                Map<String, String> vars = new HashMap<>();
                vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "Customer");
                vars.put("documentType", docType);

                if (status == KycDocumentStatus.APPROVED) {
                    redisPublisher.publishKycEvent("KYC_DOCUMENT_APPROVED",
                            user.getId(), user.getEmail(), user.getFirstName(), vars);
                } else if (status == KycDocumentStatus.REJECTED) {
                    vars.put("rejectionReason", rejectionReason != null ? rejectionReason : "Not specified");
                    redisPublisher.publishKycEvent("KYC_DOCUMENT_REJECTED",
                            user.getId(), user.getEmail(), user.getFirstName(), vars);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to publish KYC notification event: {}", e.getMessage());
        }

        return toDocumentResponse(saved);
    }

    @Transactional(readOnly = true)
    public KycStatusResponse getKycStatus(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        List<KycDocumentEntity> documents = kycDocumentRepository.findByUserId(userId);
        List<KycVerificationEntity> verifications = kycVerificationRepository.findByUserId(userId);

        List<KycDocumentResponse> documentResponses = documents.stream()
                .map(this::toDocumentResponse)
                .collect(Collectors.toList());

        List<String> verificationDescriptions = verifications.stream()
                .map(v -> String.format("%s: %s (provider: %s)",
                        v.getVerificationType(), v.getStatus(), v.getProvider()))
                .collect(Collectors.toList());

        List<String> nextTierRequirements = calculateNextTierRequirements(user.getKycTier(), documents);

        String overallStatus = computeOverallStatus(documents);

        return KycStatusResponse.builder()
                .userId(user.getUuid())
                .currentTier(user.getKycTier())
                .overallStatus(overallStatus)
                .documents(documentResponses)
                .verifications(verificationDescriptions)
                .nextTierRequirements(nextTierRequirements)
                .build();
    }

    @Transactional
    public void triggerVerification(Long userId, VerificationType type) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        VerificationResult result;

        switch (type) {
            case IDENTITY -> {
                List<KycDocumentEntity> docs = kycDocumentRepository.findByUserId(userId);
                KycDocumentEntity idDoc = docs.stream()
                        .filter(d -> d.getDocumentType() == KycDocumentType.PASSPORT
                                || d.getDocumentType() == KycDocumentType.DRIVING_LICENCE
                                || d.getDocumentType() == KycDocumentType.NATIONAL_ID)
                        .findFirst()
                        .orElseThrow(() -> new RemitzException(
                                "No identity document found for verification", HttpStatus.BAD_REQUEST));
                result = kycVerificationProvider.verifyIdentity(userId.toString(), idDoc);
            }
            case LIVENESS -> {
                result = kycVerificationProvider.checkLiveness(userId.toString(), new byte[0]);
            }
            default -> {
                result = VerificationResult.builder()
                        .status(VerificationStatus.PENDING)
                        .providerReference("MANUAL-" + UUID.randomUUID().toString().substring(0, 8))
                        .resultData("{\"message\": \"Verification queued for processing\"}")
                        .build();
            }
        }

        KycVerificationEntity verification = KycVerificationEntity.builder()
                .userId(userId)
                .verificationType(type)
                .provider(VerificationProvider.MANUAL)
                .providerReference(result.getProviderReference())
                .status(result.getStatus())
                .resultData(result.getResultData())
                .build();

        kycVerificationRepository.save(verification);
        log.info("Verification triggered: userId={}, type={}, status={}", userId, type, result.getStatus());

        KycAuditLogEntity auditLog = KycAuditLogEntity.builder()
                .userId(userId)
                .action("VERIFICATION_INITIATED")
                .actorId(userId)
                .details(String.format("{\"verificationType\": \"%s\", \"provider\": \"MANUAL\", \"status\": \"%s\"}",
                        type, result.getStatus()))
                .build();
        kycAuditLogRepository.save(auditLog);
    }

    @Transactional
    public List<ScreeningResponse> screenPepSanctions(Long userId, String ipAddress) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String fullName = (user.getFirstName() != null ? user.getFirstName() : "") + " " +
                (user.getLastName() != null ? user.getLastName() : "");
        fullName = fullName.trim();

        String dateOfBirth = user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null;
        String country = user.getCountry();

        ScreeningResult pepResult = kycVerificationProvider.screenPEP(fullName, dateOfBirth, country);
        ScreeningResult sanctionsResult = kycVerificationProvider.screenSanctions(fullName, country);

        KycVerificationEntity pepVerification = KycVerificationEntity.builder()
                .userId(userId)
                .verificationType(VerificationType.PEP_CHECK)
                .provider(VerificationProvider.MANUAL)
                .status(VerificationStatus.PENDING)
                .resultData(pepResult.getMatchDetails())
                .build();
        kycVerificationRepository.save(pepVerification);

        KycVerificationEntity sanctionsVerification = KycVerificationEntity.builder()
                .userId(userId)
                .verificationType(VerificationType.SANCTIONS_CHECK)
                .provider(VerificationProvider.MANUAL)
                .status(VerificationStatus.PENDING)
                .resultData(sanctionsResult.getMatchDetails())
                .build();
        kycVerificationRepository.save(sanctionsVerification);

        KycAuditLogEntity auditLog = KycAuditLogEntity.builder()
                .userId(userId)
                .action("SCREENING_RUN")
                .details(String.format("{\"screeningTypes\": [\"PEP_CHECK\", \"SANCTIONS_CHECK\"], \"fullName\": \"%s\"}", fullName))
                .ipAddress(ipAddress)
                .build();
        kycAuditLogRepository.save(auditLog);

        log.info("PEP/Sanctions screening completed for userId={}", userId);

        List<ScreeningResponse> responses = new ArrayList<>();
        responses.add(ScreeningResponse.builder()
                .id(pepVerification.getId())
                .entityType(EntityType.CUSTOMER)
                .entityId(userId)
                .listChecked(ScreeningListType.HMT)
                .matchScore(pepResult.getMatchScore())
                .status(pepResult.getStatus())
                .matchDetails(pepResult.getMatchDetails())
                .build());

        responses.add(ScreeningResponse.builder()
                .id(sanctionsVerification.getId())
                .entityType(EntityType.CUSTOMER)
                .entityId(userId)
                .listChecked(ScreeningListType.OFAC)
                .matchScore(sanctionsResult.getMatchScore())
                .status(sanctionsResult.getStatus())
                .matchDetails(sanctionsResult.getMatchDetails())
                .build());

        return responses;
    }

    private void triggerRescreenOnIdentityApproval(KycDocumentEntity document) {
        try {
            if (document.getDocumentType() == null) return;
            String type = document.getDocumentType().name();
            boolean isIdentityDoc = type.equals("PASSPORT")
                    || type.equals("DRIVING_LICENCE")
                    || type.equals("NATIONAL_ID");
            if (!isIdentityDoc) return;

            UserEntity user = userRepository.findById(document.getUserId()).orElse(null);
            if (user == null) return;

            String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                    + (user.getLastName() != null ? user.getLastName() : "")).trim();
            if (fullName.isBlank()) return;

            String dob = user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null;
            String country = user.getCountry();

            kycVerificationProvider.screenSanctions(fullName, country);
            kycVerificationProvider.screenPEP(fullName, dob, country);
            log.info("Re-screen dispatched for userId={} on identity doc approval", user.getId());
        } catch (Exception e) {
            log.warn("Re-screen on identity approval failed: {}", e.getMessage());
        }
    }

    private String computeOverallStatus(List<KycDocumentEntity> documents) {
        if (documents.isEmpty()) return "NOT_SUBMITTED";
        boolean anyRejected = documents.stream().anyMatch(d -> d.getStatus() == KycDocumentStatus.REJECTED);
        boolean anyPending = documents.stream().anyMatch(d -> d.getStatus() == KycDocumentStatus.PENDING);
        if (anyRejected && !anyPending) return "REJECTED";
        if (anyPending) {
            // A pending doc only counts as a real review if it was uploaded via the app
            // (file_hash set). Auto-imported pending docs (no hash) = PARTIAL, not PENDING.
            boolean anyRealPending = documents.stream().anyMatch(d ->
                    d.getStatus() == KycDocumentStatus.PENDING
                            && d.getFileHash() != null && !d.getFileHash().isBlank());
            return anyRealPending ? "PENDING" : "PARTIAL";
        }
        return "VERIFIED";
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RemitzException("File is empty", HttpStatus.BAD_REQUEST);
        }

        long maxSizeBytes = (long) maxFileSizeMb * 1024 * 1024;
        if (file.getSize() > maxSizeBytes) {
            throw new RemitzException(
                    String.format("File size exceeds maximum allowed size of %dMB", maxFileSizeMb),
                    HttpStatus.BAD_REQUEST);
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.contains(".")) {
            throw new RemitzException("File must have a valid extension", HttpStatus.BAD_REQUEST);
        }

        String extension = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase();
        Set<String> allowed = Set.of(allowedExtensions.split(","));
        if (!allowed.contains(extension)) {
            throw new RemitzException(
                    String.format("File extension '%s' is not allowed. Allowed: %s", extension, allowedExtensions),
                    HttpStatus.BAD_REQUEST);
        }
    }

    private String saveFile(Long userId, KycDocumentType type, MultipartFile file) {
        try {
            Path uploadPath = Paths.get(uploadDir, userId.toString());
            Files.createDirectories(uploadPath);

            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null
                    ? originalFilename.substring(originalFilename.lastIndexOf('.'))
                    : ".bin";
            String filename = type.name().toLowerCase() + "_" + System.currentTimeMillis() + extension;
            Path filePath = uploadPath.resolve(filename);

            try (InputStream inputStream = file.getInputStream()) {
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
            }

            log.debug("File saved: {}", filePath);
            return filePath.toString();
        } catch (IOException e) {
            log.error("Failed to save file for userId={}: {}", userId, e.getMessage());
            throw new RemitzException("Failed to save uploaded file", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private String calculateSha256(MultipartFile file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(file.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error("Failed to calculate file hash: {}", e.getMessage());
            throw new RemitzException("Failed to calculate file hash", HttpStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private List<String> calculateNextTierRequirements(KycTier currentTier, List<KycDocumentEntity> documents) {
        Set<KycDocumentType> approvedTypes = documents.stream()
                .filter(d -> d.getStatus() == KycDocumentStatus.APPROVED)
                .map(KycDocumentEntity::getDocumentType)
                .collect(Collectors.toSet());

        List<String> requirements = new ArrayList<>();

        boolean hasIdDoc = approvedTypes.contains(KycDocumentType.PASSPORT)
                || approvedTypes.contains(KycDocumentType.DRIVING_LICENCE)
                || approvedTypes.contains(KycDocumentType.NATIONAL_ID);

        switch (currentTier) {
            case TIER_0 -> {
                if (!hasIdDoc) {
                    requirements.add("Upload and get approved: Identity document (Passport, Driving Licence, or National ID)");
                }
            }
            case TIER_1 -> {
                if (!approvedTypes.contains(KycDocumentType.PROOF_OF_ADDRESS)) {
                    requirements.add("Upload and get approved: Proof of Address document");
                }
            }
            case TIER_2 -> {
                if (!approvedTypes.contains(KycDocumentType.SOURCE_OF_FUNDS)) {
                    requirements.add("Upload and get approved: Source of Funds document");
                }
            }
            case TIER_3 -> {
                requirements.add("Maximum tier reached");
            }
        }

        return requirements;
    }

    @Transactional(readOnly = true)
    public KycDocumentEntity getDocumentEntity(Long docId) {
        return kycDocumentRepository.findById(docId).orElse(null);
    }

    private KycDocumentResponse toDocumentResponse(KycDocumentEntity entity) {
        return KycDocumentResponse.builder()
                .id(entity.getId())
                .documentType(entity.getDocumentType())
                .documentNumber(entity.getDocumentNumber())
                .filePath(entity.getFilePath())
                .status(entity.getStatus())
                .verifiedBy(entity.getVerifiedBy() != null ? entity.getVerifiedBy().toString() : null)
                .verifiedAt(entity.getVerifiedAt())
                .rejectionReason(entity.getRejectionReason())
                .expiryDate(entity.getExpiryDate())
                .issueDate(entity.getIssueDate())
                .fileName(extractFileName(entity.getFilePath()))
                .fileUrl("/api/users/" + entity.getUserId() + "/kyc/documents/" + entity.getId() + "/file")
                .createdAt(entity.getCreatedAt())
                .realUpload(entity.getFileHash() != null && !entity.getFileHash().isBlank())
                .build();
    }

    private String extractFileName(String filePath) {
        if (filePath == null) return null;
        return filePath.substring(filePath.lastIndexOf('/') + 1);
    }
}

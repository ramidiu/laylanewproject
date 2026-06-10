package com.remitz.modules.payin.customer.service;

import com.remitz.modules.payin.customer.dto.CreateCustomerRequest;
import com.remitz.modules.payin.customer.dto.CreateCustomerResponse;
import com.remitz.modules.payin.customer.dto.PayinCustomerDto;
import com.remitz.modules.payin.customer.entity.PayinCustomerEntity;
import com.remitz.modules.payin.customer.mapper.PayinCustomerMapper;
import com.remitz.modules.payin.customer.repository.PayinCustomerDocumentRepository;
import com.remitz.modules.payin.customer.repository.PayinCustomerRepository;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayinCustomerServiceImpl implements PayinCustomerService {

    private final PayinCustomerRepository repository;
    private final PayinCustomerMapper mapper;
    private final PayinCustomerDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final com.remitz.modules.user.repository.KycDocumentRepository kycDocumentRepository;
    private final BackendCustomerLoginProvisioner loginProvisioner;

    @Override
    @Transactional
    public CreateCustomerResponse createCustomer(CreateCustomerRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        log.info("PayIn customer creation request for email: {}***", maskEmail(email));

        if (repository.existsByEmail(email)) {
            log.warn("Duplicate customer creation attempt for email: {}***", maskEmail(email));
            return CreateCustomerResponse.failure("Email already registered");
        }

        PayinCustomerEntity entity = mapper.toEntity(request);
        entity.setIsVerified(true);   // partner-created customers are verified on creation (always TIER_2)
        PayinCustomerEntity saved = repository.save(entity);

        // Provision a login account with the default password (FIRSTNAME + first 4 digits
        // of phone) and force a password change on first login. Runs in its OWN transaction
        // (REQUIRES_NEW) and is wrapped so it can NEVER roll back customer creation or affect
        // document uploads.
        try {
            loginProvisioner.provision(saved.getFirstName(), saved.getLastName(), email, saved.getPhone(),
                    saved.getCountry(), saved.getNationality(), saved.getAddressLine1(),
                    saved.getCity(), saved.getPostalCode());
        } catch (Exception ex) {
            log.error("Login-account provisioning failed for payin customer {} — customer created anyway: {}",
                    saved.getCustomerId(), ex.getMessage());
        }

        log.info("PayIn customer created successfully — customerId: {}", saved.getCustomerId());
        return CreateCustomerResponse.success(saved.getCustomerId());
    }

    @Override
    @Transactional
    public int backfillLoginAccounts() {
        List<PayinCustomerEntity> all = repository.findAll();
        for (PayinCustomerEntity c : all) {
            if (c.getEmail() == null || c.getEmail().isBlank()) continue;
            // 1. Login account with default password + TIER_2 (isolated transaction).
            //    Use the RETURNED user — a fresh re-query (findByEmail) from this outer
            //    transaction can't see a row the REQUIRES_NEW provision just committed
            //    (MySQL REPEATABLE READ snapshot), which would skip the mirror for new accounts.
            UserEntity linked = null;
            try {
                linked = loginProvisioner.provision(c.getFirstName(), c.getLastName(), c.getEmail().trim().toLowerCase(),
                        c.getPhone(), c.getCountry(), c.getNationality(), c.getAddressLine1(),
                        c.getCity(), c.getPostalCode());
            } catch (Exception ex) {
                log.error("Backfill login provisioning failed for {}: {}", c.getCustomerId(), ex.getMessage());
            }
            // 2. Mark the customer verified (partner-created customers are always verified).
            if (!Boolean.TRUE.equals(c.getIsVerified())) {
                c.setIsVerified(true);
                repository.save(c);
            }
            // 3. Approve any existing documents + mirror them into users-side KYC so they
            //    show in the Users / customer KYC views. Dedup PER DOCUMENT by file hash, so
            //    it's idempotent (safe to re-run) AND covers customers who already have some
            //    users-side docs (e.g. uploaded online) without skipping their pay-in docs.
            java.util.Set<String> existingHashes = linked == null ? java.util.Set.of()
                    : kycDocumentRepository.findByUserId(linked.getId()).stream()
                        .map(com.remitz.modules.user.entity.KycDocumentEntity::getFileHash)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            for (com.remitz.modules.payin.customer.entity.PayinCustomerDocumentEntity d
                    : documentRepository.findByCustomerId(c.getCustomerId())) {
                if (d.getStatus() == null || "PENDING".equalsIgnoreCase(d.getStatus())) {
                    d.setStatus("APPROVED");
                    documentRepository.save(d);
                }
                String dhash = com.remitz.modules.payin.customer.controller.PayinCustomerDocumentController.sha256(d.getFilePath());
                if (linked != null && !existingHashes.contains(dhash)) {
                    existingHashes.add(dhash);
                    String cat = d.getDocCategory() != null ? d.getDocCategory().toUpperCase() : "";
                    boolean addr = (d.getDocSide() != null && d.getDocSide().toUpperCase().contains("ADDRESS"))
                            || cat.contains("ADDRESS") || cat.contains("BILL") || cat.contains("STATEMENT")
                            || cat.contains("TENANCY") || cat.contains("TAX") || cat.contains("UTILITY") || cat.contains("COUNCIL");
                    com.remitz.common.enums.KycDocumentType ktype = addr
                            ? com.remitz.common.enums.KycDocumentType.PROOF_OF_ADDRESS
                            : cat.equals("PASSPORT") ? com.remitz.common.enums.KycDocumentType.PASSPORT
                            : (cat.contains("DRIVING") || cat.contains("LICEN")) ? com.remitz.common.enums.KycDocumentType.DRIVING_LICENCE
                            : com.remitz.common.enums.KycDocumentType.NATIONAL_ID;
                    kycDocumentRepository.save(com.remitz.modules.user.entity.KycDocumentEntity.builder()
                            .userId(linked.getId())
                            .documentType(ktype)
                            .documentNumber(d.getDocumentNumber())
                            .filePath(d.getFilePath())
                            .fileHash(dhash)
                            .status(com.remitz.common.enums.KycDocumentStatus.APPROVED)
                            .issueDate(d.getIssueDate())
                            .expiryDate(d.getExpiryDate())
                            .build());
                }
            }
        }
        log.info("Backfilled {} pay-in customers (login + verify + approve docs)", all.size());
        return all.size();
    }

    @Override
    public List<PayinCustomerDto> listCustomers() {
        return repository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private PayinCustomerDto toDto(PayinCustomerEntity e) {
        boolean expiredDocs = documentRepository.findByCustomerId(e.getCustomerId()).stream()
                .anyMatch(doc -> doc.getExpiryDate() != null && doc.getExpiryDate().isBefore(LocalDate.now()));
        int payinDocsCount = (int) documentRepository.countByCustomerId(e.getCustomerId());

        // Cross-reference the users table — same person may be verified there (TIER_>0 + approved KYC docs)
        // but still flagged is_verified=0 in payin_customers (legacy / never synced).
        boolean verified = Boolean.TRUE.equals(e.getIsVerified());
        int usersDocsCount = 0;
        if (!verified && e.getEmail() != null) {
            UserEntity match = userRepository.findByEmail(e.getEmail()).orElse(null);
            if (match != null) {
                if (match.getKycTier() != null && !match.getKycTier().name().equals("TIER_0")) {
                    verified = true;
                }
                usersDocsCount = (int) kycDocumentRepository.countByUserId(match.getId());
            }
        }

        return PayinCustomerDto.builder()
                .kycDocsCount(Math.max(payinDocsCount, usersDocsCount))
                .customerId(e.getCustomerId())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .email(e.getEmail())
                .phone(e.getPhone())
                .dob(e.getDob())
                .nationality(e.getNationality())
                .addressLine1(e.getAddressLine1())
                .city(e.getCity())
                .country(e.getCountry())
                .postalCode(e.getPostalCode())
                .isVerified(verified)
                .hasExpiredDocuments(expiredDocs)
                .createdSource(e.getCreatedSource() != null ? e.getCreatedSource().name() : null)
                .createdAt(e.getCreatedAt())
                .build();
    }

    @Override
    public List<PayinCustomerDto> listAllCustomers() {
        List<PayinCustomerDto> result = new ArrayList<>(repository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList()));

        // Track emails already in payin_customers to avoid duplicates from users table
        java.util.Set<String> existingEmails = result.stream()
                .map(PayinCustomerDto::getEmail)
                .filter(e -> e != null)
                .collect(java.util.stream.Collectors.toSet());

        // Imported backend customers (CUSTOMER role, non-UK) — show as BACKEND
        List<UserEntity> importedCustomers = userRepository.findImportedBackendCustomers();
        for (UserEntity u : importedCustomers) {
            if (u.getEmail() != null && existingEmails.contains(u.getEmail().toLowerCase())) continue;
            result.add(PayinCustomerDto.builder()
                    .userId(u.getId())
                    .customerId(u.getUuid() != null ? u.getUuid() : String.valueOf(u.getId()))
                    .firstName(u.getFirstName())
                    .lastName(u.getLastName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .nationality(u.getNationality())
                    .country(u.getCountryCode() != null ? u.getCountryCode() : u.getCountry())
                    .isVerified(u.getKycTier() != null && !u.getKycTier().name().equals("TIER_0"))
                    .createdSource("BACKEND")
                    .createdAt(u.getCreatedAt())
                    .build());
            if (u.getEmail() != null) existingEmails.add(u.getEmail().toLowerCase());
            result.get(result.size() - 1).setKycDocsCount((int) kycDocumentRepository.countByUserId(u.getId()));
        }

        // UK frontend users (registered via customer app) — show as FRONTEND_USER with toggle
        List<UserEntity> ukUsers = userRepository.findUkFrontendCustomers();
        for (UserEntity u : ukUsers) {
            if (u.getEmail() != null && existingEmails.contains(u.getEmail().toLowerCase())) continue;
            result.add(PayinCustomerDto.builder()
                    .userId(u.getId())
                    .customerId(u.getUuid())
                    .firstName(u.getFirstName())
                    .lastName(u.getLastName())
                    .email(u.getEmail())
                    .phone(u.getPhone())
                    .nationality(u.getNationality())
                    .country(u.getCountryCode() != null ? u.getCountryCode() : u.getCountry())
                    .isVerified(u.getKycTier() != null && !u.getKycTier().name().equals("TIER_0"))
                    .createdSource("FRONTEND_USER")
                    .payinEnabled(Boolean.TRUE.equals(u.getPayinEnabled()))
                    .createdAt(u.getCreatedAt())
                    .build());
        }

        // Sort alphabetically by firstName, then lastName (case-insensitive). Nulls last.
        result.sort((a, b) -> {
            String an = ((a.getFirstName() == null ? "" : a.getFirstName()) + " "
                       + (a.getLastName()  == null ? "" : a.getLastName())).trim().toLowerCase();
            String bn = ((b.getFirstName() == null ? "" : b.getFirstName()) + " "
                       + (b.getLastName()  == null ? "" : b.getLastName())).trim().toLowerCase();
            if (an.isEmpty() && bn.isEmpty()) return 0;
            if (an.isEmpty()) return 1;
            if (bn.isEmpty()) return -1;
            return an.compareTo(bn);
        });
        return result;
    }

    @Override
    @Transactional
    public boolean toggleFrontendUserPayin(Long userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        boolean newValue = !Boolean.TRUE.equals(user.getPayinEnabled());
        user.setPayinEnabled(newValue);
        userRepository.save(user);
        log.info("Toggled payin_enabled={} for user id={}", newValue, userId);
        return newValue;
    }

    @Override
    @Transactional
    public PayinCustomerDto updateProfile(String customerId, LocalDate dob, Boolean isVerified) {
        // Try payin_customers first
        java.util.Optional<PayinCustomerEntity> payin = repository.findByCustomerId(customerId);
        if (payin.isPresent()) {
            PayinCustomerEntity entity = payin.get();
            if (dob != null) entity.setDob(dob);
            if (isVerified != null) entity.setIsVerified(isVerified);
            PayinCustomerEntity saved = repository.save(entity);
            log.info("Updated payin customer {} (dob={}, isVerified={})", customerId, dob, isVerified);
            return toDto(saved);
        }
        // Fallback: treat customerId as the users.uuid (frontend / imported customer)
        UserEntity user = userRepository.findByUuid(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "customerId", customerId));
        if (dob != null) user.setDateOfBirth(dob);
        if (Boolean.TRUE.equals(isVerified) &&
                (user.getKycTier() == null || "TIER_0".equals(user.getKycTier().name()))) {
            user.setKycTier(com.remitz.common.enums.KycTier.TIER_2);
        }
        userRepository.save(user);
        log.info("Updated frontend user {} (dob={}, isVerified={})", customerId, dob, isVerified);
        // Build a DTO so the caller still gets a 200 OK with usable shape
        return PayinCustomerDto.builder()
                .customerId(customerId)
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .dob(user.getDateOfBirth())
                .isVerified(user.getKycTier() != null && !"TIER_0".equals(user.getKycTier().name()))
                .createdSource("FRONTEND_USER")
                .build();
    }

    private static String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIdx = email.indexOf('@');
        return email.substring(0, Math.min(3, atIdx)) + "***" + email.substring(atIdx);
    }
}

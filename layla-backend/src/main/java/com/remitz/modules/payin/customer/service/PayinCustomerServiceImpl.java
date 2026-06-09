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
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.remitz.modules.auth.repository.RoleRepository roleRepository;

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
        PayinCustomerEntity saved = repository.save(entity);

        // Provision a login account with the default password (FIRSTNAME + first 4 digits
        // of phone) and force a password change on first login.
        provisionLoginAccount(saved.getFirstName(), saved.getLastName(), email, saved.getPhone(),
                saved.getCountry(), saved.getNationality(), saved.getAddressLine1(),
                saved.getCity(), saved.getPostalCode());

        log.info("PayIn customer created successfully — customerId: {}", saved.getCustomerId());
        return CreateCustomerResponse.success(saved.getCustomerId());
    }

    /**
     * Default first-login password for a backend (pay-in) customer:
     * FIRSTNAME (first word, uppercased) + first 4 digits of the phone number.
     * e.g. "vinay kumar" / "9542854803" -> "VINAY9542".
     */
    static String defaultPassword(String firstName, String phone) {
        String first = firstName == null ? "" : firstName.trim().split("\\s+")[0].toUpperCase();
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        String four = digits.length() >= 4 ? digits.substring(0, 4) : digits;
        return first + four;
    }

    /**
     * Creates (or, for an existing account, resets) the customer's {@code users} login row
     * with the default password and the password-change-required flag set, so they can log
     * in with the default password and are forced to change it on first login.
     */
    private void provisionLoginAccount(String firstName, String lastName, String email, String phone,
                                       String country, String nationality, String addressLine1,
                                       String city, String postalCode) {
        String rawPassword = defaultPassword(firstName, phone);
        String hash = passwordEncoder.encode(rawPassword);
        UserEntity existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            existing.setPasswordHash(hash);
            existing.setPasswordChangeRequired(true);
            existing.setEmailVerified(true);   // let them log in with the default password (no OTP gate)
            userRepository.save(existing);
            log.info("PayIn customer login reset for existing user: {}***", maskEmail(email));
            return;
        }
        UserEntity user = UserEntity.builder()
                .uuid(java.util.UUID.randomUUID().toString())
                .email(email)
                .passwordHash(hash)
                .firstName(firstName)
                .lastName(lastName)
                .phone(phone)
                .country(country)
                .nationality(nationality)
                .countryOfResidence(country)
                .addressLine1(addressLine1)
                .city(city)
                .postcode(postalCode)
                .userType(com.remitz.common.enums.UserType.INDIVIDUAL)
                .kycTier(com.remitz.common.enums.KycTier.TIER_0)
                .status(com.remitz.common.enums.UserStatus.ACTIVE)
                .mfaEnabled(false)
                .emailVerified(true)
                .passwordChangeRequired(true)
                .preferredLanguage("en")
                .build();
        com.remitz.modules.auth.entity.RoleEntity role = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Default CUSTOMER role not found"));
        user.getRoles().add(role);
        userRepository.save(user);
        log.info("PayIn customer login account created for {}*** (password change required)", maskEmail(email));
    }

    @Override
    @Transactional
    public int backfillLoginAccounts() {
        List<PayinCustomerEntity> all = repository.findAll();
        for (PayinCustomerEntity c : all) {
            if (c.getEmail() == null || c.getEmail().isBlank()) continue;
            provisionLoginAccount(c.getFirstName(), c.getLastName(), c.getEmail().trim().toLowerCase(),
                    c.getPhone(), c.getCountry(), c.getNationality(), c.getAddressLine1(),
                    c.getCity(), c.getPostalCode());
        }
        log.info("Backfilled login accounts for {} pay-in customers", all.size());
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

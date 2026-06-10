package com.remitz.modules.payin.customer.service;

import com.remitz.modules.auth.entity.RoleEntity;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.RoleRepository;
import com.remitz.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provisions a backend (pay-in) customer's {@code users} login account in its OWN
 * transaction (REQUIRES_NEW), fully isolated from pay-in customer creation. If anything
 * here fails, only this transaction rolls back — the customer (and its document uploads)
 * are never affected. Lives in a separate bean so the REQUIRES_NEW proxy actually applies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BackendCustomerLoginProvisioner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Default first-login password: FIRSTNAME (first word, uppercased) + first 4 digits of
     * the phone number, e.g. "vinay kumar" / "9542854803" -> "VINAY9542".
     */
    public static String defaultPassword(String firstName, String phone) {
        String first = firstName == null ? "" : firstName.trim().split("\\s+")[0].toUpperCase();
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        String four = digits.length() >= 4 ? digits.substring(0, 4) : digits;
        return first + four;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UserEntity provision(String firstName, String lastName, String email, String phone,
                          String country, String nationality, String addressLine1,
                          String city, String postalCode) {
        String hash = passwordEncoder.encode(defaultPassword(firstName, phone));
        UserEntity existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            existing.setPasswordHash(hash);
            existing.setPasswordChangeRequired(true);
            existing.setEmailVerified(true);   // can log in with the default password (no OTP gate)
            // Pay-in customers are created by a trusted partner — always verified (TIER_2).
            if (existing.getKycTier() == null || existing.getKycTier().name().equals("TIER_0")) {
                existing.setKycTier(com.remitz.common.enums.KycTier.TIER_2);
            }
            UserEntity saved = userRepository.save(existing);
            log.info("PayIn customer login reset for existing user: {}", email);
            return saved;
        }
        UserEntity user = UserEntity.builder()
                .uuid(UUID.randomUUID().toString())
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
                .kycTier(com.remitz.common.enums.KycTier.TIER_2)   // trusted partner-created customer — always verified
                .status(com.remitz.common.enums.UserStatus.ACTIVE)
                .mfaEnabled(false)
                .emailVerified(true)
                .passwordChangeRequired(true)
                .preferredLanguage("en")
                .build();
        RoleEntity role = roleRepository.findByName("CUSTOMER")
                .orElseThrow(() -> new RuntimeException("Default CUSTOMER role not found"));
        user.getRoles().add(role);
        UserEntity saved = userRepository.save(user);
        log.info("PayIn customer login account created for {} (password change required)", email);
        return saved;
    }
}

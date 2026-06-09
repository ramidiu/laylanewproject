package com.remitz.modules.auth.entity;

import com.remitz.common.enums.AccountStatus;
import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.UserStatus;
import com.remitz.common.enums.UserType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36)
    private String uuid;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    private UserType userType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_tier")
    private KycTier kycTier;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private UserStatus status;

    // --- Account deletion (Google Play Account Deletion policy) ---
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status")
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "delete_requested_at")
    private LocalDateTime deleteRequestedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "delete_reason", length = 1000)
    private String deleteReason;

    @Column(name = "deleted_by", length = 255)
    private String deletedBy;

    @Column(length = 3)
    private String country;

    @Column(name = "address_line_1", length = 255)
    private String addressLine1;

    @Column(name = "address_line_2", length = 255)
    private String addressLine2;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String postcode;

    @Column(name = "preferred_language", length = 5)
    @Builder.Default
    private String preferredLanguage = "en";

    @Column(name = "mfa_enabled")
    @Builder.Default
    private Boolean mfaEnabled = false;

    @Column(name = "mfa_secret", length = 255)
    private String mfaSecret;

    @Column(name = "email_verified")
    @Builder.Default
    private Boolean emailVerified = false;

    /** True when the account was created with a default password and the user must change
     *  it on first login. Cleared on change-password or forgot-password reset. */
    @Column(name = "password_change_required", nullable = false)
    @Builder.Default
    private Boolean passwordChangeRequired = false;

    @Column(length = 100)
    private String nationality;

    @Column(name = "country_of_residence", length = 100)
    private String countryOfResidence;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "payin_enabled")
    @Builder.Default
    private Boolean payinEnabled = false;

    @Column(length = 20)
    private String gender;

    @Column(length = 100)
    private String occupation;

    @Column(name = "risk_score", length = 20)
    @Builder.Default
    private String riskScore = "MEDIUM";

    @Column(name = "risk_points")
    @Builder.Default
    private Integer riskPoints = 0;

    @Column(name = "risk_override", length = 20)
    private String riskOverride;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "demo_access_expires_at")
    private LocalDateTime demoAccessExpiresAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<RoleEntity> roles = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

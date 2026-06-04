package com.remitz.modules.payout.usi.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Sandbox / test payout-account templates supplied by USI Money for each
 * payout corridor (see V532 migration). Used by partner tooling and the
 * create-transaction beneficiary form to one-click populate known-good
 * sandbox fixtures during onboarding/QA.
 */
@Entity
@Table(name = "usi_test_accounts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsiTestAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", length = 3, nullable = false)
    private String countryCode;

    @Column(name = "country_name", length = 100, nullable = false)
    private String countryName;

    /** BANK_TRANSFER | MOBILE_MONEY | CASH_COLLECTION (matches payout_types.payout_type). */
    @Column(name = "payout_type", length = 30, nullable = false)
    private String payoutType;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_branch", length = 150)
    private String bankBranch;

    @Column(name = "bank_branch_state", length = 100)
    private String bankBranchState;

    @Column(name = "bank_branch_city", length = 100)
    private String bankBranchCity;

    @Column(name = "account_number", length = 64)
    private String accountNumber;

    @Column(name = "iban", length = 64)
    private String iban;

    @Column(name = "mobile_network", length = 60)
    private String mobileNetwork;

    @Column(name = "mobile_number", length = 32)
    private String mobileNumber;

    @Column(name = "collection_point", length = 150)
    private String collectionPoint;

    @Column(name = "notes", length = 255)
    private String notes;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}

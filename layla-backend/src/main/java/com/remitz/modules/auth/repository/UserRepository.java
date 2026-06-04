package com.remitz.modules.auth.repository;

import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.common.enums.KycTier;
import com.remitz.common.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByEmail(String email);

    Optional<UserEntity> findByUuid(String uuid);

    boolean existsByEmail(String email);

    List<UserEntity> findByEmailVerifiedFalseAndCreatedAtBefore(LocalDateTime cutoff);

    Page<UserEntity> findByStatusAndKycTier(UserStatus status, KycTier kycTier, Pageable pageable);

    long countByStatus(UserStatus status);

    long countByKycTier(KycTier kycTier);

    long countByKycTierIn(List<KycTier> kycTiers);

    @Query("SELECT u FROM UserEntity u JOIN u.roles r WHERE r.name = 'CUSTOMER' AND " +
            "(u.country = 'GB' OR u.countryCode = 'GB' OR u.countryOfResidence = 'GB' OR " +
            "u.country = 'GBR' OR u.countryCode = 'GBR')")
    List<UserEntity> findUkFrontendCustomers();

    @Query("SELECT u FROM UserEntity u JOIN u.roles r WHERE r.name = 'CUSTOMER' AND " +
            "(u.country IS NULL OR (u.country != 'GB' AND u.country != 'GBR')) AND " +
            "(u.countryCode IS NULL OR (u.countryCode != 'GB' AND u.countryCode != 'GBR')) AND " +
            "(u.countryOfResidence IS NULL OR (u.countryOfResidence != 'GB' AND u.countryOfResidence != 'GBR'))")
    List<UserEntity> findImportedBackendCustomers();

    @Query("SELECT u FROM UserEntity u WHERE " +
            "(:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:kycTier IS NULL OR u.kycTier = :kycTier) " +
            "AND (:kycStatus IS NULL " +
            "     OR (:kycStatus = 'VERIFIED' AND u.kycTier IN (com.remitz.common.enums.KycTier.TIER_1, com.remitz.common.enums.KycTier.TIER_2, com.remitz.common.enums.KycTier.TIER_3)) " +
            "     OR (:kycStatus = 'REJECTED' AND EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity d WHERE d.userId = u.id AND d.status = com.remitz.common.enums.KycDocumentStatus.REJECTED)) " +
            // PENDING = a real (app-uploaded, file_hash set) pending document awaiting review.
            "     OR (:kycStatus = 'PENDING' AND EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity d WHERE d.userId = u.id AND d.fileHash IS NOT NULL AND d.status = com.remitz.common.enums.KycDocumentStatus.PENDING)) " +
            // PARTIAL = has a pending document but it is auto-imported (no file_hash) — i.e. no real submission.
            "     OR (:kycStatus = 'PARTIAL' AND EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity dp WHERE dp.userId = u.id AND dp.status = com.remitz.common.enums.KycDocumentStatus.PENDING) AND NOT EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity d WHERE d.userId = u.id AND d.fileHash IS NOT NULL AND d.status = com.remitz.common.enums.KycDocumentStatus.PENDING))" +
            ")")
    Page<UserEntity> searchUsers(
            @Param("search") String search,
            @Param("status") UserStatus status,
            @Param("kycTier") KycTier kycTier,
            @Param("kycStatus") String kycStatus,
            Pageable pageable);

    // Same filters as searchUsers but with an explicit alphabetical ORDER BY that
    // pushes NULL / empty firstName rows to the bottom (MySQL has no NULLS LAST syntax).
    @Query("SELECT u FROM UserEntity u WHERE " +
            "(:search IS NULL OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:kycTier IS NULL OR u.kycTier = :kycTier) " +
            "AND (:kycStatus IS NULL " +
            "     OR (:kycStatus = 'VERIFIED' AND u.kycTier IN (com.remitz.common.enums.KycTier.TIER_1, com.remitz.common.enums.KycTier.TIER_2, com.remitz.common.enums.KycTier.TIER_3)) " +
            "     OR (:kycStatus = 'REJECTED' AND EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity d WHERE d.userId = u.id AND d.status = com.remitz.common.enums.KycDocumentStatus.REJECTED)) " +
            // PENDING = a real (app-uploaded, file_hash set) pending document awaiting review.
            "     OR (:kycStatus = 'PENDING' AND EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity d WHERE d.userId = u.id AND d.fileHash IS NOT NULL AND d.status = com.remitz.common.enums.KycDocumentStatus.PENDING)) " +
            // PARTIAL = has a pending document but it is auto-imported (no file_hash) — i.e. no real submission.
            "     OR (:kycStatus = 'PARTIAL' AND EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity dp WHERE dp.userId = u.id AND dp.status = com.remitz.common.enums.KycDocumentStatus.PENDING) AND NOT EXISTS (SELECT 1 FROM com.remitz.modules.user.entity.KycDocumentEntity d WHERE d.userId = u.id AND d.fileHash IS NOT NULL AND d.status = com.remitz.common.enums.KycDocumentStatus.PENDING))" +
            ") " +
            "ORDER BY CASE WHEN u.firstName IS NULL OR u.firstName = '' THEN 1 ELSE 0 END ASC, " +
            "LOWER(u.firstName) ASC, LOWER(u.lastName) ASC")
    Page<UserEntity> searchUsersAlpha(
            @Param("search") String search,
            @Param("status") UserStatus status,
            @Param("kycTier") KycTier kycTier,
            @Param("kycStatus") String kycStatus,
            Pageable pageable);
}

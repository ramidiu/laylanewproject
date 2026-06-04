package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BeneficiaryRepository extends JpaRepository<BeneficiaryEntity, Long> {

    List<BeneficiaryEntity> findByUserIdAndIsBlockedFalse(Long userId);

    List<BeneficiaryEntity> findByUserIdAndIsFavouriteTrueAndIsBlockedFalse(Long userId);

    Optional<BeneficiaryEntity> findByUserIdAndId(Long userId, Long id);

    List<BeneficiaryEntity> findByUserIdAndFullNameContainingIgnoreCaseAndIsBlockedFalse(Long userId, String fullName);

    List<BeneficiaryEntity> findByUserIdAndAccountNumberAndIsBlockedFalse(Long userId, String accountNumber);
}

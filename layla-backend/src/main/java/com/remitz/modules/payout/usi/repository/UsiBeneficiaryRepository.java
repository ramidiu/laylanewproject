package com.remitz.modules.payout.usi.repository;

import com.remitz.modules.payout.usi.entity.UsiBeneficiaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsiBeneficiaryRepository extends JpaRepository<UsiBeneficiaryEntity, Long> {
    Optional<UsiBeneficiaryEntity> findByLocalBeneficiaryId(String localBeneficiaryId);
    Optional<UsiBeneficiaryEntity> findByUsiBeneficiaryId(String usiBeneficiaryId);
}

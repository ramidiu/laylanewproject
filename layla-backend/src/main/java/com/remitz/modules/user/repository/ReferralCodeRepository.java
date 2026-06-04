package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.ReferralCodeEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReferralCodeRepository extends JpaRepository<ReferralCodeEntity, Long> {

    Optional<ReferralCodeEntity> findByUserId(Long userId);

    Optional<ReferralCodeEntity> findByCode(String code);

    Optional<ReferralCodeEntity> findByCodeAndIsActiveTrue(String code);
}

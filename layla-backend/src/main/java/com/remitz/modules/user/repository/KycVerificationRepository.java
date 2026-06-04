package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.KycVerificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface KycVerificationRepository extends JpaRepository<KycVerificationEntity, Long> {

    List<KycVerificationEntity> findByUserId(Long userId);
}

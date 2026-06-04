package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.ReferralConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReferralConfigRepository extends JpaRepository<ReferralConfigEntity, Long> {

    List<ReferralConfigEntity> findAll();

    Optional<ReferralConfigEntity> findByCorridorIdIsNullAndIsActiveTrue();

    Optional<ReferralConfigEntity> findByCorridorIdAndIsActiveTrue(Long corridorId);
}

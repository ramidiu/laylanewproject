package com.remitz.modules.compliance.repository;

import com.remitz.common.enums.EntityType;
import com.remitz.modules.compliance.entity.RiskScoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScoreEntity, Long> {

    Optional<RiskScoreEntity> findTopByEntityTypeAndEntityIdOrderByCalculatedAtDesc(EntityType entityType, Long entityId);
}

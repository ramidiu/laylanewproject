package com.remitz.modules.compliance.repository;

import com.remitz.common.enums.EntityType;
import com.remitz.modules.compliance.entity.ScreeningResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ScreeningResultRepository extends JpaRepository<ScreeningResultEntity, Long> {

    List<ScreeningResultEntity> findByEntityTypeAndEntityId(EntityType entityType, Long entityId);
}

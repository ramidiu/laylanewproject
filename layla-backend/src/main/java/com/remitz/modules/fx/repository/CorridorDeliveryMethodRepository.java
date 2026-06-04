package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.CorridorDeliveryMethodEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorridorDeliveryMethodRepository extends JpaRepository<CorridorDeliveryMethodEntity, Long> {

    List<CorridorDeliveryMethodEntity> findByCorridorIdAndIsActiveTrue(Long corridorId);
}

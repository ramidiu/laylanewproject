package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.CorridorRateSlabEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorridorRateSlabRepository extends JpaRepository<CorridorRateSlabEntity, Long> {
    List<CorridorRateSlabEntity> findByCorridorIdAndIsActiveTrueOrderByMinAmountAsc(Long corridorId);
    List<CorridorRateSlabEntity> findByCorridorIdOrderByMinAmountAsc(Long corridorId);
    void deleteByCorridorId(Long corridorId);
}

package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.CorridorRateSlabEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CorridorRateSlabRepository extends JpaRepository<CorridorRateSlabEntity, Long> {
    List<CorridorRateSlabEntity> findByCorridorIdAndIsActiveTrueOrderByMinAmountAsc(Long corridorId);
    List<CorridorRateSlabEntity> findByCorridorIdOrderByMinAmountAsc(Long corridorId);
    void deleteByCorridorId(Long corridorId);

    // ---- Per-payment-type (delivery method) scoped queries ----

    /** Active slabs for a specific payment type, lowest band first (used by the quote rate lookup). */
    @Query("SELECT s FROM CorridorRateSlabEntity s WHERE s.corridorId = :corridorId " +
           "AND s.isActive = true AND s.deliveryMethod = :deliveryMethod ORDER BY s.minAmount ASC")
    List<CorridorRateSlabEntity> findActiveByCorridorAndMethod(@Param("corridorId") Long corridorId,
                                                               @Param("deliveryMethod") String deliveryMethod);

    /** Active corridor-wide slabs (delivery_method NULL) — the fallback that applies to all payment types. */
    @Query("SELECT s FROM CorridorRateSlabEntity s WHERE s.corridorId = :corridorId " +
           "AND s.isActive = true AND s.deliveryMethod IS NULL ORDER BY s.minAmount ASC")
    List<CorridorRateSlabEntity> findActiveByCorridorNoMethod(@Param("corridorId") Long corridorId);

    /** All slabs (any active state) for one scope — NULL deliveryMethod means the corridor-wide scope. */
    @Query("SELECT s FROM CorridorRateSlabEntity s WHERE s.corridorId = :corridorId " +
           "AND ((:deliveryMethod IS NULL AND s.deliveryMethod IS NULL) OR s.deliveryMethod = :deliveryMethod) " +
           "ORDER BY s.minAmount ASC")
    List<CorridorRateSlabEntity> listByCorridorAndScope(@Param("corridorId") Long corridorId,
                                                        @Param("deliveryMethod") String deliveryMethod);

    /** Delete only the given scope's slabs, leaving other payment types' slabs untouched. */
    @Modifying
    @Query("DELETE FROM CorridorRateSlabEntity s WHERE s.corridorId = :corridorId " +
           "AND ((:deliveryMethod IS NULL AND s.deliveryMethod IS NULL) OR s.deliveryMethod = :deliveryMethod)")
    void deleteByCorridorAndScope(@Param("corridorId") Long corridorId,
                                  @Param("deliveryMethod") String deliveryMethod);
}

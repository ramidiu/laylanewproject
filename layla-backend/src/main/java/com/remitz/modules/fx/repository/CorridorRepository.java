package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.CorridorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CorridorRepository extends JpaRepository<CorridorEntity, Long> {

    List<CorridorEntity> findByIsActiveTrue();

    /** Active corridors with delivery methods eagerly loaded — safe to read outside a session (USI rate sweep). */
    @Query("SELECT DISTINCT c FROM CorridorEntity c LEFT JOIN FETCH c.deliveryMethods WHERE c.isActive = true")
    List<CorridorEntity> findActiveWithDeliveryMethods();

    Optional<CorridorEntity> findBySendCurrencyAndReceiveCurrencyAndIsActiveTrue(
            String sendCurrency, String receiveCurrency);

    Optional<CorridorEntity> findBySendCurrencyAndReceiveCurrency(
            String sendCurrency, String receiveCurrency);
}

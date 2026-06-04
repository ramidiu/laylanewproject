package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.FxRateHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface FxRateHistoryRepository extends JpaRepository<FxRateHistoryEntity, Long> {

    Optional<FxRateHistoryEntity> findTopByBaseCurrencyAndTargetCurrencyOrderByFetchedAtDesc(
            String baseCurrency, String targetCurrency);

    List<FxRateHistoryEntity> findByBaseCurrencyAndTargetCurrencyAndFetchedAtBetween(
            String baseCurrency, String targetCurrency, LocalDateTime from, LocalDateTime to);
}

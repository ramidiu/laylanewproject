package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.CompetitorRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompetitorRateRepository extends JpaRepository<CompetitorRateEntity, Long> {

    List<CompetitorRateEntity> findBySendCurrencyAndReceiveCurrency(String sendCurrency, String receiveCurrency);
}

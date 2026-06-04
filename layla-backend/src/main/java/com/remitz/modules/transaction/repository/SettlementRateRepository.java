package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.SettlementRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SettlementRateRepository extends JpaRepository<SettlementRate, Long> {

    Optional<SettlementRate> findByCurrency(String currency);
}

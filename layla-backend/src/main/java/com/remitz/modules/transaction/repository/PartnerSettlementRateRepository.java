package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PartnerSettlementRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerSettlementRateRepository extends JpaRepository<PartnerSettlementRate, Long> {

    Optional<PartnerSettlementRate> findByPartnerIdAndCurrency(Long partnerId, String currency);

    List<PartnerSettlementRate> findByPartnerId(Long partnerId);

    void deleteByPartnerIdAndCurrency(Long partnerId, String currency);
}

package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.CorridorPartnerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CorridorPartnerMappingRepository extends JpaRepository<CorridorPartnerMapping, Long> {

    List<CorridorPartnerMapping> findByFromCurrencyAndToCurrency(String fromCurrency, String toCurrency);

    List<CorridorPartnerMapping> findByPartnerId(Long partnerId);
}

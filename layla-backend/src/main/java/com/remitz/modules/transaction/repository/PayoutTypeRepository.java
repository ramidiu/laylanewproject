package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PayoutType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutTypeRepository extends JpaRepository<PayoutType, Long> {

    List<PayoutType> findByCountryCode(String countryCode);

    List<PayoutType> findByCountryCodeAndIsActive(String countryCode, Boolean isActive);

    List<PayoutType> findByCurrencyAndIsActive(String currency, Boolean isActive);

    @Query("SELECT DISTINCT p.countryCode FROM PayoutType p WHERE p.isActive = true")
    List<String> findActiveCountryCodes();

    @Query("SELECT DISTINCT p.currency FROM PayoutType p WHERE p.isActive = true AND p.currency IS NOT NULL")
    List<String> findActiveCurrencies();
}

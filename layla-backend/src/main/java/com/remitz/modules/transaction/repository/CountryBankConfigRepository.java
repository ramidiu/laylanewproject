package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.CountryBankConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CountryBankConfigRepository extends JpaRepository<CountryBankConfig, Long> {

    Optional<CountryBankConfig> findByCountryCode(String countryCode);

    Optional<CountryBankConfig> findByCurrency(String currency);
}

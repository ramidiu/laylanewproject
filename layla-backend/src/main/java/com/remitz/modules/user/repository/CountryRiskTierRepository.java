package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.CountryRiskTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CountryRiskTierRepository extends JpaRepository<CountryRiskTier, Long> {

    Optional<CountryRiskTier> findByCountryCode(String countryCode);

    List<CountryRiskTier> findAll();
}

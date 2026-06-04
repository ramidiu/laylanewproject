package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.MobileMoneyService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MobileMoneyServiceRepository extends JpaRepository<MobileMoneyService, Long> {

    List<MobileMoneyService> findByCountryCodeAndIsActive(String countryCode, Boolean isActive);

    List<MobileMoneyService> findByCountryCode(String countryCode);
}

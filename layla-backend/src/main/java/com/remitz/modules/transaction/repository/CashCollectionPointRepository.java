package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.CashCollectionPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CashCollectionPointRepository extends JpaRepository<CashCollectionPoint, Long> {

    List<CashCollectionPoint> findByCountryCodeAndIsActive(String countryCode, Boolean isActive);

    List<CashCollectionPoint> findByCountryCode(String countryCode);
}

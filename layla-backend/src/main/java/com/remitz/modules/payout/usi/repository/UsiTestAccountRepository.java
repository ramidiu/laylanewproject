package com.remitz.modules.payout.usi.repository;

import com.remitz.modules.payout.usi.entity.UsiTestAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UsiTestAccountRepository extends JpaRepository<UsiTestAccountEntity, Long> {

    List<UsiTestAccountEntity> findByIsActiveTrueOrderByCountryCodeAscPayoutTypeAsc();

    List<UsiTestAccountEntity> findByCountryCodeAndIsActiveTrueOrderByPayoutTypeAsc(String countryCode);

    List<UsiTestAccountEntity> findByCountryCodeAndPayoutTypeAndIsActiveTrue(String countryCode, String payoutType);
}

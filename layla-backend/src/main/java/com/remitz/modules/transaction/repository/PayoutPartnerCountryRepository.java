package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PayoutPartnerCountry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutPartnerCountryRepository extends JpaRepository<PayoutPartnerCountry, Long> {

    List<PayoutPartnerCountry> findByPartnerId(Long partnerId);
}

package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PayoutPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayoutPartnerRepository extends JpaRepository<PayoutPartner, Long> {

    Optional<PayoutPartner> findByUserId(Long userId);

    Optional<PayoutPartner> findByContactEmail(String contactEmail);
}

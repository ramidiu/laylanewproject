package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PayinPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayinPartnerRepository extends JpaRepository<PayinPartner, Long> {

    Optional<PayinPartner> findByUserId(Long userId);

    // Code added by Naresh: email-based fallback resolver for monolith (JWT has no userId claim).
    Optional<PayinPartner> findByContactEmail(String contactEmail);
}

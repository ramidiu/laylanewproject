package com.remitz.modules.payin.trust.repository;

import com.remitz.modules.payin.trust.entity.TrustPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrustPaymentRepository extends JpaRepository<TrustPaymentEntity, Long> {
    Optional<TrustPaymentEntity> findByOrderReference(String orderReference);
}

package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

    List<PaymentEntity> findByTransactionId(Long transactionId);

    Optional<PaymentEntity> findByProviderReference(String providerReference);
}

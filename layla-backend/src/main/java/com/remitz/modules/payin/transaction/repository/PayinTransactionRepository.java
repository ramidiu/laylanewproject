package com.remitz.modules.payin.transaction.repository;

import com.remitz.common.enums.PayinTransactionStatus;
import com.remitz.modules.payin.transaction.entity.PayinTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayinTransactionRepository extends JpaRepository<PayinTransactionEntity, Long> {

    Optional<PayinTransactionEntity> findByExternalReferenceId(String externalReferenceId);

    Optional<PayinTransactionEntity> findByTransactionId(String transactionId);

    List<PayinTransactionEntity> findByStatus(PayinTransactionStatus status);
}

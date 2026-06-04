package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.TransactionStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionStatusHistoryRepository extends JpaRepository<TransactionStatusHistoryEntity, Long> {

    List<TransactionStatusHistoryEntity> findByTransactionIdOrderByCreatedAtAsc(Long transactionId);
}

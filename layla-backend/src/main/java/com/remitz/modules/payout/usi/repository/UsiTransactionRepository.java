package com.remitz.modules.payout.usi.repository;

import com.remitz.modules.payout.usi.entity.UsiTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsiTransactionRepository extends JpaRepository<UsiTransactionEntity, Long> {
    Optional<UsiTransactionEntity> findByTransactionId(String transactionId);
    Optional<UsiTransactionEntity> findByTransSessionId(String transSessionId);
    Optional<UsiTransactionEntity> findByReferenceNumber(String referenceNumber);
    List<UsiTransactionEntity> findByStatus(String status);
    List<UsiTransactionEntity> findByStatusIn(java.util.Collection<String> statuses);
}

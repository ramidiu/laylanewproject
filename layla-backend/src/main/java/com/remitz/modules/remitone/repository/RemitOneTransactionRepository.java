package com.remitz.modules.remitone.repository;

import com.remitz.modules.remitone.entity.RemitOneTransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RemitOneTransactionRepository extends JpaRepository<RemitOneTransactionEntity, Long> {

    Optional<RemitOneTransactionEntity> findByTransactionId(String transactionId);
}

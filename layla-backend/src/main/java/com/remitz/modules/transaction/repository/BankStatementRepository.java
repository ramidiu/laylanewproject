package com.remitz.modules.transaction.repository;

import com.remitz.common.enums.BankStatementStatus;
import com.remitz.modules.transaction.entity.BankStatementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankStatementRepository extends JpaRepository<BankStatementEntity, Long> {

    List<BankStatementEntity> findByStatus(BankStatementStatus status);
}

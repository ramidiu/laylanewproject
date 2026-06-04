package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PlatformLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlatformLedgerRepository extends JpaRepository<PlatformLedger, Long> {

    List<PlatformLedger> findAllByOrderByIdAsc();

    Optional<PlatformLedger> findTopByOrderByIdDesc();

    List<PlatformLedger> findByAccountTypeOrderByIdAsc(String accountType);
}

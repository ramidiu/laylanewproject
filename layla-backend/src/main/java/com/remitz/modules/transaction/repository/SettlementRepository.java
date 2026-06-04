package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {

    List<Settlement> findByStatusOrderByCreatedAtDesc(String status);

    List<Settlement> findAllByOrderByCreatedAtDesc();
}

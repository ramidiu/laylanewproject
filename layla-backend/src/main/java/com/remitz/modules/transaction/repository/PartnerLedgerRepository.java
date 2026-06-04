package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PartnerLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartnerLedgerRepository extends JpaRepository<PartnerLedger, Long> {

    List<PartnerLedger> findByPartnerIdOrderByIdAsc(Long partnerId);

    Optional<PartnerLedger> findTopByPartnerIdOrderByIdDesc(Long partnerId);
}

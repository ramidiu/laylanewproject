package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PayinPartnerLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayinPartnerLedgerRepository extends JpaRepository<PayinPartnerLedger, Long> {

    List<PayinPartnerLedger> findByPartnerIdOrderByIdAsc(Long partnerId);

    Optional<PayinPartnerLedger> findTopByPartnerIdOrderByIdDesc(Long partnerId);
}

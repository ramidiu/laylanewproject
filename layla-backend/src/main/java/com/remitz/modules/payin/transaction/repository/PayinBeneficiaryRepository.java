package com.remitz.modules.payin.transaction.repository;

import com.remitz.modules.payin.transaction.entity.PayinBeneficiaryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayinBeneficiaryRepository extends JpaRepository<PayinBeneficiaryEntity, Long> {

    Optional<PayinBeneficiaryEntity> findByIdAndCustomerId(Long id, String customerId);

    java.util.List<PayinBeneficiaryEntity> findByCustomerId(String customerId);
}

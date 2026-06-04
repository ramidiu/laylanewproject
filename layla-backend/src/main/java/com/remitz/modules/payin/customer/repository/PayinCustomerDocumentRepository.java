package com.remitz.modules.payin.customer.repository;

import com.remitz.modules.payin.customer.entity.PayinCustomerDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PayinCustomerDocumentRepository extends JpaRepository<PayinCustomerDocumentEntity, Long> {

    List<PayinCustomerDocumentEntity> findByCustomerId(String customerId);

    Optional<PayinCustomerDocumentEntity> findByIdAndCustomerId(Long id, String customerId);

    long countByCustomerId(String customerId);
}

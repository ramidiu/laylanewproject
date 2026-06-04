package com.remitz.modules.payin.customer.repository;

import com.remitz.modules.payin.customer.entity.PayinCustomerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PayinCustomerRepository extends JpaRepository<PayinCustomerEntity, Long> {

    Optional<PayinCustomerEntity> findByEmail(String email);

    boolean existsByEmail(String email);

    Optional<PayinCustomerEntity> findByCustomerId(String customerId);

    boolean existsByCustomerId(String customerId);
}

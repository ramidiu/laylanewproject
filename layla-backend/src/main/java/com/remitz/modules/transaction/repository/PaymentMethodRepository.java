package com.remitz.modules.transaction.repository;

import com.remitz.modules.transaction.entity.PaymentMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethod, Long> {

    List<PaymentMethod> findByCountryCode(String countryCode);

    List<PaymentMethod> findByCountryCodeAndIsActive(String countryCode, Boolean isActive);

    List<PaymentMethod> findByCurrencyAndIsActive(String currency, Boolean isActive);

    @Query("SELECT DISTINCT p.countryCode FROM PaymentMethod p WHERE p.isActive = true")
    List<String> findActiveCountryCodes();
}

package com.remitz.modules.fx.repository;

import com.remitz.modules.fx.entity.NostroAccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NostroAccountRepository extends JpaRepository<NostroAccountEntity, Long> {

    Optional<NostroAccountEntity> findByCurrencyAndCountry(String currency, String country);
}

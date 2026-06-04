package com.remitz.modules.payout.usi.repository;

import com.remitz.modules.payout.usi.entity.UsiRemitterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsiRemitterRepository extends JpaRepository<UsiRemitterEntity, Long> {
    Optional<UsiRemitterEntity> findByUserId(Long userId);
    Optional<UsiRemitterEntity> findByRemitterId(String remitterId);
}

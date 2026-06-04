package com.remitz.modules.user.repository;

import com.remitz.modules.user.entity.WalletLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletLedgerRepository extends JpaRepository<WalletLedgerEntity, Long> {

    List<WalletLedgerEntity> findByWalletIdOrderByCreatedAtDesc(Long walletId);
}

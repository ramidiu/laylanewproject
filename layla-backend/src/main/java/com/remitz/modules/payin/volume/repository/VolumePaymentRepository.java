package com.remitz.modules.payin.volume.repository;

import com.remitz.modules.payin.volume.entity.VolumePaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VolumePaymentRepository extends JpaRepository<VolumePaymentEntity, Long> {
    Optional<VolumePaymentEntity> findByMerchantPaymentId(String merchantPaymentId);
    Optional<VolumePaymentEntity> findByTransactionId(String transactionId);
}

package com.remitz.modules.payin.trust.repository;

import com.remitz.modules.payin.trust.entity.TrustPaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TrustPaymentRepository extends JpaRepository<TrustPaymentEntity, Long> {
    Optional<TrustPaymentEntity> findByOrderReference(String orderReference);

    // All saved rows for a Trust Payments transactionreference. Used by the server-to-server
    // /callback webhook to recover OUR internal transaction ref (transactionRef) from a row the
    // browser /confirm already linked, when the notification itself doesn't carry it.
    List<TrustPaymentEntity> findByTransactionReference(String transactionReference);
}

package com.remitz.modules.transaction.repository;

import com.remitz.common.enums.TransactionStatus;
import com.remitz.modules.transaction.entity.TransactionEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, Long> {

    Optional<TransactionEntity> findByReferenceNumber(String referenceNumber);

    // Highest numeric suffix among existing "TXN<n>" reference numbers. Used to seed the
    // sequential reference generator at startup so new transactions continue the series.
    @Query(value = "SELECT COALESCE(MAX(CAST(SUBSTRING(reference_number, 4) AS UNSIGNED)), 0) " +
            "FROM transactions WHERE reference_number LIKE 'TXN%' " +
            "AND SUBSTRING(reference_number, 4) REGEXP '^[0-9]+$'",
            nativeQuery = true)
    Long findMaxTxnSequence();

    // USI-eligible transactions: either already linked to USI (have a usi_transactions row)
    // OR destination country in the USI Money corridor set (UG/TR/EG/QA/SA/AE).
    // Ordered newest first; cap the result via Pageable to keep the admin page snappy.
    @Query(value =
            "SELECT t.* FROM transactions t " +
            " LEFT JOIN beneficiaries b ON b.id = t.beneficiary_id " +
            " WHERE t.reference_number IN (SELECT transaction_id FROM usi_transactions) " +
            "    OR UPPER(b.country) IN ('UG','UGA','UGANDA',"
            +                            "'TR','TUR','TURKEY',"
            +                            "'EG','EGY','EGYPT',"
            +                            "'QA','QAT','QATAR',"
            +                            "'SA','SAU','SAUDI ARABIA',"
            +                            "'AE','ARE','UNITED ARAB EMIRATES','UAE') " +
            " ORDER BY t.created_at DESC",
            nativeQuery = true)
    java.util.List<TransactionEntity> findUsiEligibleTransactions(org.springframework.data.domain.Pageable pageable);

    Optional<TransactionEntity> findByIdempotencyKey(String idempotencyKey);

    long countBySenderId(Long senderId);

    Page<TransactionEntity> findBySenderId(Long senderId, Pageable pageable);

    Page<TransactionEntity> findBySenderIdAndStatus(Long senderId, TransactionStatus status, Pageable pageable);

    List<TransactionEntity> findByPayoutPartnerIdAndStatus(Long payoutPartnerId, TransactionStatus status);

    List<TransactionEntity> findByPayoutPartnerIdAndStatusIn(Long payoutPartnerId, java.util.Collection<TransactionStatus> statuses);

    List<TransactionEntity> findByPayinPartnerIdAndStatus(Long payinPartnerId, TransactionStatus status);

    List<TransactionEntity> findByPayinPartnerId(Long payinPartnerId);

    /**
     * Has this sender ever had a transaction reach PROCESSING or beyond? If yes, the
     * sender has already been compliance-cleared (either screening passed or admin
     * manually released a COMPLIANCE_HOLD) and does not need to be re-screened.
     */
    @Query("SELECT (COUNT(t) > 0) FROM TransactionEntity t WHERE t.senderId = :senderId AND " +
            "t.status IN (com.remitz.common.enums.TransactionStatus.PROCESSING, " +
            "com.remitz.common.enums.TransactionStatus.FUNDS_RECEIVED, " +
            "com.remitz.common.enums.TransactionStatus.SENT_TO_PAYOUT, " +
            "com.remitz.common.enums.TransactionStatus.PAID, " +
            "com.remitz.common.enums.TransactionStatus.COMPLETED)")
    boolean existsSenderComplianceCleared(@Param("senderId") Long senderId);

    /**
     * Has this (sender, beneficiary) pair ever had a transaction reach PROCESSING+? If
     * yes, this beneficiary has already been compliance-cleared for this sender.
     */
    @Query("SELECT (COUNT(t) > 0) FROM TransactionEntity t WHERE t.senderId = :senderId " +
            "AND t.beneficiaryId = :beneficiaryId AND t.status IN (" +
            "com.remitz.common.enums.TransactionStatus.PROCESSING, " +
            "com.remitz.common.enums.TransactionStatus.FUNDS_RECEIVED, " +
            "com.remitz.common.enums.TransactionStatus.SENT_TO_PAYOUT, " +
            "com.remitz.common.enums.TransactionStatus.PAID, " +
            "com.remitz.common.enums.TransactionStatus.COMPLETED)")
    boolean existsBeneficiaryComplianceCleared(@Param("senderId") Long senderId,
                                                @Param("beneficiaryId") Long beneficiaryId);

    @Query("SELECT t FROM TransactionEntity t WHERE " +
            "(:senderId IS NULL OR t.senderId = :senderId) AND " +
            "(:status IS NULL OR t.status = :status) AND " +
            // Hide archived (stale-pending) transactions from the default list unless explicitly filtered.
            "(:status IS NOT NULL OR t.status <> com.remitz.common.enums.TransactionStatus.ARCHIVED) AND " +
            "(:corridorId IS NULL OR t.corridorId = :corridorId) AND " +
            "(:startDate IS NULL OR t.createdAt >= :startDate) AND " +
            "(:endDate IS NULL OR t.createdAt <= :endDate) AND " +
            "(:search IS NULL OR t.referenceNumber LIKE CONCAT('%', :search, '%') OR " +
            "  CAST(t.senderId AS string) = :search)")
    Page<TransactionEntity> searchTransactions(
            @Param("senderId") Long senderId,
            @Param("status") TransactionStatus status,
            @Param("corridorId") Long corridorId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("search") String search,
            Pageable pageable);

    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.status = :status")
    long countByStatus(@Param("status") TransactionStatus status);

    /** Transactions stuck in a given status since before the cutoff — used by the archive scheduler. */
    List<TransactionEntity> findByStatusAndCreatedAtBefore(TransactionStatus status, LocalDateTime cutoff);

    @Query("SELECT COALESCE(SUM(t.sendAmount), 0) FROM TransactionEntity t WHERE t.status IN ('PAID','COMPLETED')")
    BigDecimal sumPaidVolume();

    @Query("SELECT COALESCE(SUM(t.feeAmount), 0) FROM TransactionEntity t WHERE t.status IN ('PAID','COMPLETED')")
    BigDecimal sumPaidRevenue();

    @Query("SELECT COALESCE(SUM(t.fxMarginAmount), 0) FROM TransactionEntity t WHERE t.status IN ('PAID','COMPLETED')")
    BigDecimal sumPaidFxMargin();

    Page<TransactionEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByPayoutPartnerId(Long payoutPartnerId);

    @Query("SELECT COALESCE(SUM(t.sendAmount), 0) FROM TransactionEntity t WHERE t.senderId = :senderId AND t.createdAt >= CURRENT_DATE")
    BigDecimal sumSendAmountByUserToday(@Param("senderId") Long senderId);

    @Query("SELECT COALESCE(SUM(t.sendAmount), 0) FROM TransactionEntity t WHERE t.senderId = :senderId AND t.createdAt >= :since")
    BigDecimal sumSendAmountByUserSince(@Param("senderId") Long senderId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT COUNT(t) FROM TransactionEntity t WHERE t.senderId = :senderId AND t.createdAt >= :since")
    long countBySenderIdSince(@Param("senderId") Long senderId, @Param("since") java.time.LocalDateTime since);

    @Query("SELECT MAX(t.createdAt) FROM TransactionEntity t WHERE t.senderId = :senderId")
    java.time.LocalDateTime findMaxCreatedAtBySender(@Param("senderId") Long senderId);

    @Query("SELECT t FROM TransactionEntity t WHERE t.sendAmount >= :threshold AND t.sendCurrency = :currency AND t.createdAt >= :start AND t.createdAt < :end ORDER BY t.senderId, t.createdAt")
    List<TransactionEntity> findCtrCandidates(@Param("threshold") BigDecimal threshold,
                                              @Param("currency") String currency,
                                              @Param("start") java.time.LocalDateTime start,
                                              @Param("end") java.time.LocalDateTime end);
}

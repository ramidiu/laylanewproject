package com.remitz.modules.compliance.repository;

import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.AlertStatus;
import com.remitz.modules.compliance.entity.ComplianceAlertEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplianceAlertRepository extends JpaRepository<ComplianceAlertEntity, Long> {

    List<ComplianceAlertEntity> findByStatus(AlertStatus status);

    List<ComplianceAlertEntity> findByUserId(Long userId);

    List<ComplianceAlertEntity> findBySeverity(AlertSeverity severity);

    @Query("SELECT a FROM ComplianceAlertEntity a WHERE " +
            "(:status IS NULL OR a.status = :status) AND " +
            "(:severity IS NULL OR a.severity = :severity) AND " +
            "(:userId IS NULL OR a.userId = :userId)")
    Page<ComplianceAlertEntity> findWithFilters(
            @Param("status") AlertStatus status,
            @Param("severity") AlertSeverity severity,
            @Param("userId") Long userId,
            Pageable pageable);

    long countByUserIdAndTransactionIdIsNotNull(Long userId);

    long countByUserIdAndTransactionIdIsNotNullAndCreatedAtAfter(Long userId, java.time.LocalDateTime after);

    boolean existsByUserIdAndListEntryIdAndStatus(Long userId, Long listEntryId, AlertStatus status);

    long countByStatusInAndCreatedAtAfter(java.util.Collection<AlertStatus> statuses, java.time.LocalDateTime after);

    long countByCreatedAtAfter(java.time.LocalDateTime after);

    @Query(value = "SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at)) " +
            "FROM compliance_alerts " +
            "WHERE resolved_at IS NOT NULL AND created_at >= :after",
            nativeQuery = true)
    Double avgMinutesToDispositionSince(@Param("after") java.time.LocalDateTime after);

    long countByStatusAndResolvedAtAfter(AlertStatus status, java.time.LocalDateTime after);

    List<ComplianceAlertEntity> findByUserIdAndStatusIn(Long userId, java.util.Collection<AlertStatus> statuses);

    @Query("SELECT a.userId AS uid, COUNT(a) AS cnt " +
            "FROM ComplianceAlertEntity a " +
            "WHERE a.userId IN :userIds AND a.status IN :statuses " +
            "GROUP BY a.userId")
    List<OpenAlertCount> countOpenByUserIds(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("statuses") java.util.Collection<AlertStatus> statuses);

    interface OpenAlertCount {
        Long getUid();
        Long getCnt();
    }
}

package com.remitz.modules.compliance.repository;

import com.remitz.modules.compliance.entity.CtrReportEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface CtrReportRepository extends JpaRepository<CtrReportEntity, Long> {

    Optional<CtrReportEntity> findByReportDateAndUserId(LocalDate reportDate, Long userId);

    @Query(value = "SELECT c FROM CtrReportEntity c WHERE " +
            "(:filingStatus IS NULL OR c.filingStatus = :filingStatus) AND " +
            "(:startDate IS NULL OR c.reportDate >= :startDate) AND " +
            "(:endDate IS NULL OR c.reportDate <= :endDate)",
           countQuery = "SELECT COUNT(c) FROM CtrReportEntity c WHERE " +
            "(:filingStatus IS NULL OR c.filingStatus = :filingStatus) AND " +
            "(:startDate IS NULL OR c.reportDate >= :startDate) AND " +
            "(:endDate IS NULL OR c.reportDate <= :endDate)")
    Page<CtrReportEntity> findWithFilters(
            @Param("filingStatus") CtrReportEntity.FilingStatus filingStatus,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);
}

package com.remitz.modules.transaction.repository;

import com.remitz.common.enums.RecurringStatus;
import com.remitz.modules.transaction.entity.RecurringTransferEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RecurringTransferRepository extends JpaRepository<RecurringTransferEntity, Long> {

    List<RecurringTransferEntity> findByUserIdAndStatus(Long userId, RecurringStatus status);

    List<RecurringTransferEntity> findByUserId(Long userId);

    List<RecurringTransferEntity> findByStatusAndNextExecutionDateLessThanEqual(RecurringStatus status, LocalDate date);

    Optional<RecurringTransferEntity> findByIdAndUserId(Long id, Long userId);
}

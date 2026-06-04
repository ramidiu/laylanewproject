package com.remitz.modules.transaction.service;

import com.remitz.common.dto.CreateTransactionRequest;
import com.remitz.common.enums.RecurringFrequency;
import com.remitz.common.enums.RecurringStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.dto.CreateRecurringTransferRequest;
import com.remitz.modules.transaction.dto.RecurringTransferResponse;
import com.remitz.modules.transaction.dto.UpdateRecurringTransferRequest;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.entity.RecurringTransferEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.repository.RecurringTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTransferService {

    private final RecurringTransferRepository recurringTransferRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    @Transactional
    public RecurringTransferResponse createSchedule(Long userId, CreateRecurringTransferRequest request) {
        // Validate beneficiary
        BeneficiaryEntity beneficiary = beneficiaryRepository.findByUserIdAndId(userId, request.getBeneficiaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", request.getBeneficiaryId()));

        if (beneficiary.getIsBlocked()) {
            throw new RemitzException("Beneficiary is blocked", HttpStatus.BAD_REQUEST);
        }

        RecurringTransferEntity entity = RecurringTransferEntity.builder()
                .userId(userId)
                .beneficiaryId(request.getBeneficiaryId())
                .corridorId(request.getCorridorId())
                .deliveryMethod(request.getDeliveryMethod())
                .sendAmount(request.getSendAmount())
                .sendCurrency(request.getSendCurrency())
                .receiveCurrency(request.getReceiveCurrency())
                .frequency(request.getFrequency())
                .customIntervalDays(request.getCustomIntervalDays())
                .nextExecutionDate(request.getStartDate())
                .status(RecurringStatus.ACTIVE)
                .paymentMethodType(request.getPaymentMethodType())
                .totalExecutions(0)
                .maxExecutions(request.getMaxExecutions())
                .notes(request.getNotes())
                .build();

        RecurringTransferEntity saved = recurringTransferRepository.save(entity);
        log.info("Recurring transfer schedule created id={} for userId={}", saved.getId(), userId);
        return mapToResponse(saved, beneficiary.getFullName());
    }

    @Transactional(readOnly = true)
    public List<RecurringTransferResponse> getSchedules(Long userId) {
        return recurringTransferRepository.findByUserId(userId).stream()
                .map(entity -> {
                    String bName = beneficiaryRepository.findById(entity.getBeneficiaryId())
                            .map(BeneficiaryEntity::getFullName).orElse("Unknown");
                    return mapToResponse(entity, bName);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public RecurringTransferResponse updateSchedule(Long userId, Long id, UpdateRecurringTransferRequest request) {
        RecurringTransferEntity entity = recurringTransferRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringTransfer", "id", id));

        if (entity.getStatus() == RecurringStatus.CANCELLED) {
            throw new RemitzException("Cannot update a cancelled schedule", HttpStatus.BAD_REQUEST);
        }

        if (request.getSendAmount() != null) entity.setSendAmount(request.getSendAmount());
        if (request.getFrequency() != null) entity.setFrequency(request.getFrequency());
        if (request.getCustomIntervalDays() != null) entity.setCustomIntervalDays(request.getCustomIntervalDays());
        if (request.getNextExecutionDate() != null) entity.setNextExecutionDate(request.getNextExecutionDate());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        if (request.getMaxExecutions() != null) entity.setMaxExecutions(request.getMaxExecutions());
        if (request.getNotes() != null) entity.setNotes(request.getNotes());

        RecurringTransferEntity saved = recurringTransferRepository.save(entity);
        String bName = beneficiaryRepository.findById(saved.getBeneficiaryId())
                .map(BeneficiaryEntity::getFullName).orElse("Unknown");
        log.info("Recurring transfer schedule updated id={} for userId={}", id, userId);
        return mapToResponse(saved, bName);
    }

    @Transactional
    public void cancelSchedule(Long userId, Long id) {
        RecurringTransferEntity entity = recurringTransferRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("RecurringTransfer", "id", id));

        entity.setStatus(RecurringStatus.CANCELLED);
        recurringTransferRepository.save(entity);
        log.info("Recurring transfer schedule cancelled id={} for userId={}", id, userId);
    }

    /**
     * Scheduled task that checks for due recurring transfers and logs them.
     * In a production system, this would create actual transactions via TransactionService.
     * Runs every day at 06:00 UTC.
     */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void executeScheduledTransfers() {
        LocalDate today = LocalDate.now();
        List<RecurringTransferEntity> dueTransfers =
                recurringTransferRepository.findByStatusAndNextExecutionDateLessThanEqual(RecurringStatus.ACTIVE, today);

        log.info("Found {} recurring transfers due for execution", dueTransfers.size());

        for (RecurringTransferEntity schedule : dueTransfers) {
            try {
                // Check if max executions reached
                if (schedule.getMaxExecutions() != null && schedule.getTotalExecutions() >= schedule.getMaxExecutions()) {
                    schedule.setStatus(RecurringStatus.CANCELLED);
                    recurringTransferRepository.save(schedule);
                    log.info("Recurring schedule id={} reached max executions, cancelled", schedule.getId());
                    continue;
                }

                // In production: call TransactionService.createTransaction() here
                log.info("Executing recurring transfer id={} for userId={}, amount={} {}",
                        schedule.getId(), schedule.getUserId(), schedule.getSendAmount(), schedule.getSendCurrency());

                // Update execution tracking
                schedule.setTotalExecutions(schedule.getTotalExecutions() + 1);
                schedule.setLastExecutionDate(today);
                schedule.setNextExecutionDate(calculateNextExecutionDate(schedule));
                recurringTransferRepository.save(schedule);

            } catch (Exception e) {
                log.error("Failed to execute recurring transfer id={}: {}", schedule.getId(), e.getMessage(), e);
            }
        }
    }

    private LocalDate calculateNextExecutionDate(RecurringTransferEntity schedule) {
        LocalDate current = schedule.getNextExecutionDate();
        return switch (schedule.getFrequency()) {
            case WEEKLY -> current.plusWeeks(1);
            case BIWEEKLY -> current.plusWeeks(2);
            case MONTHLY -> current.plusMonths(1);
            case CUSTOM -> {
                int days = schedule.getCustomIntervalDays() != null ? schedule.getCustomIntervalDays() : 30;
                yield current.plusDays(days);
            }
        };
    }

    private RecurringTransferResponse mapToResponse(RecurringTransferEntity entity, String beneficiaryName) {
        return RecurringTransferResponse.builder()
                .id(entity.getId())
                .beneficiaryId(entity.getBeneficiaryId())
                .beneficiaryName(beneficiaryName)
                .corridorId(entity.getCorridorId())
                .deliveryMethod(entity.getDeliveryMethod())
                .sendAmount(entity.getSendAmount())
                .sendCurrency(entity.getSendCurrency())
                .receiveCurrency(entity.getReceiveCurrency())
                .frequency(entity.getFrequency())
                .customIntervalDays(entity.getCustomIntervalDays())
                .nextExecutionDate(entity.getNextExecutionDate())
                .lastExecutionDate(entity.getLastExecutionDate())
                .status(entity.getStatus())
                .paymentMethodType(entity.getPaymentMethodType())
                .totalExecutions(entity.getTotalExecutions())
                .maxExecutions(entity.getMaxExecutions())
                .notes(entity.getNotes())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}

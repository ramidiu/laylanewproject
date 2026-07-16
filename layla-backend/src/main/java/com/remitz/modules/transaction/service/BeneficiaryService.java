package com.remitz.modules.transaction.service;

import com.remitz.common.dto.BeneficiaryResponse;
import com.remitz.common.dto.CreateBeneficiaryRequest;
import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.dto.UpdateBeneficiaryRequest;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.payout.usi.service.UsiPayoutService;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.user.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    // Code added by Naresh: System Controls Phase 7 — runtime beneficiary write gate.
    private final SystemConfigService systemConfigService;
    private final UsiPayoutService usiPayoutService;

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    /** Reject incomplete beneficiaries by delivery method (prevents blank recipients). */
    private void validateBeneficiaryFields(CreateBeneficiaryRequest r) {
        if (blank(r.getFullName()))
            throw new RemitzException("Beneficiary full name is required", HttpStatus.BAD_REQUEST);
        DeliveryMethod dm = r.getDeliveryMethod();
        if (dm == DeliveryMethod.BANK_DEPOSIT) {
            if (blank(r.getBankName()))
                throw new RemitzException("Bank name is required for bank transfers", HttpStatus.BAD_REQUEST);
            if (blank(r.getAccountNumber()) && blank(r.getIban()))
                throw new RemitzException("Account number (or IBAN) is required for bank transfers", HttpStatus.BAD_REQUEST);
        } else if (dm == DeliveryMethod.MOBILE_WALLET) {
            if (blank(r.getMobileNumber()))
                throw new RemitzException("Mobile number is required for mobile money", HttpStatus.BAD_REQUEST);
        } else if (dm == DeliveryMethod.CASH_PICKUP) {
            if (blank(r.getBankName()))
                throw new RemitzException("Collection point is required for cash pickup", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureBeneficiaryWritesEnabled() {
        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        if (!systemConfigService.getBoolean("beneficiary.enabled", true)) {
            throw new RemitzException(
                    "Beneficiary management is temporarily disabled. Existing beneficiaries remain available.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Transactional
    public BeneficiaryResponse addBeneficiary(Long userId, CreateBeneficiaryRequest request) {
        ensureBeneficiaryWritesEnabled();
        validateBeneficiaryFields(request);
        BeneficiaryEntity entity = BeneficiaryEntity.builder()
                .userId(userId)
                .fullName(request.getFullName())
                .country(request.getCountry())
                .deliveryMethod(request.getDeliveryMethod())
                .bankName(request.getBankName())
                .accountNumber(request.getAccountNumber())
                .iban(request.getIban())
                .swiftBic(request.getSwiftBic())
                .sortCode(request.getSortCode())
                .branchState(request.getBranchState())
                .branchCity(request.getBranchCity())
                .mobileNumber(request.getMobileNumber())
                .mobileProvider(request.getMobileProvider())
                .idNumber(request.getIdNumber())
                .idType(request.getIdType())
                .relationship(request.getRelationship())
                .address(request.getAddress())
                .city(request.getCity())
                .isFavourite(false)
                .isBlocked(false)
                .build();

        BeneficiaryEntity saved = beneficiaryRepository.save(entity);
        log.info("Beneficiary created with id={} for userId={}", saved.getId(), userId);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiaries(Long userId) {
        return beneficiaryRepository.findByUserIdAndIsBlockedFalse(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BeneficiaryResponse getBeneficiary(Long userId, Long beneficiaryId) {
        return beneficiaryRepository.findByUserIdAndId(userId, beneficiaryId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new com.remitz.common.exception.ResourceNotFoundException("Beneficiary", "id", beneficiaryId));
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getFavouriteBeneficiaries(Long userId) {
        return beneficiaryRepository.findByUserIdAndIsFavouriteTrueAndIsBlockedFalse(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BeneficiaryResponse updateBeneficiary(Long userId, Long id, UpdateBeneficiaryRequest request) {
        ensureBeneficiaryWritesEnabled();
        BeneficiaryEntity entity = beneficiaryRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", id));

        applyBeneficiaryUpdates(entity, request);

        BeneficiaryEntity saved = beneficiaryRepository.save(entity);
        log.info("Beneficiary updated id={} for userId={}", id, userId);

        // Push the updated recipient to USI Money. Runs after the DB commit and is best-effort,
        // so a USI/HTTP failure can never roll back the beneficiary save.
        triggerUsiBeneficiarySync(saved.getId());

        return mapToResponse(saved);
    }

    /**
     * Admin edit: update ANY user's beneficiary by id (no ownership scoping). Gated by the
     * 'user:manage_beneficiary' permission at the controller. Like the owner edit, it pushes the
     * change to USI Money after commit. Used by the admin "Edit recipients" UI.
     */
    @Transactional
    public BeneficiaryResponse adminUpdateBeneficiary(Long id, UpdateBeneficiaryRequest request) {
        ensureBeneficiaryWritesEnabled();
        BeneficiaryEntity entity = beneficiaryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", id));

        applyBeneficiaryUpdates(entity, request);

        BeneficiaryEntity saved = beneficiaryRepository.save(entity);
        log.info("Beneficiary admin-updated id={} (ownerUserId={})", id, saved.getUserId());

        triggerUsiBeneficiarySync(saved.getId());

        return mapToResponse(saved);
    }

    /** Admin: list a user's beneficiaries (read-only, no ownership scoping). */
    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> getBeneficiariesForUserAdmin(Long userId) {
        return beneficiaryRepository.findByUserIdAndIsBlockedFalse(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /** Apply the non-null fields of an update request onto a beneficiary (shared by owner + admin edit). */
    private void applyBeneficiaryUpdates(BeneficiaryEntity entity, UpdateBeneficiaryRequest request) {
        if (request.getFullName() != null) entity.setFullName(request.getFullName());
        if (request.getCountry() != null) entity.setCountry(request.getCountry());
        if (request.getDeliveryMethod() != null) entity.setDeliveryMethod(request.getDeliveryMethod());
        if (request.getBankName() != null) entity.setBankName(request.getBankName());
        if (request.getAccountNumber() != null) entity.setAccountNumber(request.getAccountNumber());
        if (request.getIban() != null) entity.setIban(request.getIban());
        if (request.getSwiftBic() != null) entity.setSwiftBic(request.getSwiftBic());
        if (request.getSortCode() != null) entity.setSortCode(request.getSortCode());
        if (request.getMobileNumber() != null) entity.setMobileNumber(request.getMobileNumber());
        if (request.getMobileProvider() != null) entity.setMobileProvider(request.getMobileProvider());
        if (request.getIdNumber() != null) entity.setIdNumber(request.getIdNumber());
        if (request.getIdType() != null) entity.setIdType(request.getIdType());
        if (request.getRelationship() != null) entity.setRelationship(request.getRelationship());
        if (request.getAddress() != null) entity.setAddress(request.getAddress());
        if (request.getCity() != null) entity.setCity(request.getCity());
    }

    /** Sync this beneficiary to USI after the update commits (best-effort, non-fatal). */
    private void triggerUsiBeneficiarySync(Long beneficiaryId) {
        Runnable sync = () -> {
            try {
                java.util.Map<String, Object> result = usiPayoutService.syncBeneficiary(beneficiaryId);
                log.info("USI beneficiary sync for {} — status: {}", beneficiaryId, result.get("status"));
            } catch (Exception e) {
                log.error("USI beneficiary sync failed for {} (non-fatal, update unaffected): {}",
                        beneficiaryId, e.getMessage());
            }
        };

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sync.run();
                }
            });
        } else {
            sync.run();
        }
    }

    @Transactional
    public void deleteBeneficiary(Long userId, Long id) {
        BeneficiaryEntity entity = beneficiaryRepository.findByUserIdAndId(userId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", id));

        // Soft delete by blocking
        entity.setIsBlocked(true);
        beneficiaryRepository.save(entity);
        log.info("Beneficiary soft-deleted (blocked) id={} for userId={}", id, userId);
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> checkDuplicates(Long userId, String fullName, String accountNumber) {
        Set<Long> seen = new HashSet<>();
        List<BeneficiaryResponse> results = new ArrayList<>();

        if (fullName != null && !fullName.isBlank()) {
            beneficiaryRepository.findByUserIdAndFullNameContainingIgnoreCaseAndIsBlockedFalse(userId, fullName)
                    .stream()
                    .filter(b -> seen.add(b.getId()))
                    .map(this::mapToResponse)
                    .forEach(results::add);
        }

        if (accountNumber != null && !accountNumber.isBlank()) {
            beneficiaryRepository.findByUserIdAndAccountNumberAndIsBlockedFalse(userId, accountNumber)
                    .stream()
                    .filter(b -> seen.add(b.getId()))
                    .map(this::mapToResponse)
                    .forEach(results::add);
        }

        return results;
    }

    private BeneficiaryResponse mapToResponse(BeneficiaryEntity entity) {
        return BeneficiaryResponse.builder()
                .id(entity.getId())
                .fullName(entity.getFullName())
                .country(entity.getCountry())
                .deliveryMethod(entity.getDeliveryMethod())
                .bankName(entity.getBankName())
                .accountNumber(entity.getAccountNumber())
                .iban(entity.getIban())
                .swiftBic(entity.getSwiftBic())
                .sortCode(entity.getSortCode())
                .branchState(entity.getBranchState())
                .branchCity(entity.getBranchCity())
                .mobileNumber(entity.getMobileNumber())
                .mobileProvider(entity.getMobileProvider())
                .idType(entity.getIdType())
                .idNumber(entity.getIdNumber())
                .address(entity.getAddress())
                .city(entity.getCity())
                .relationship(entity.getRelationship())
                .isFavourite(entity.getIsFavourite())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

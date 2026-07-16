package com.remitz.modules.fx.service;

import com.remitz.modules.fx.entity.CorridorRateSlabEntity;
import com.remitz.modules.fx.repository.CorridorRateSlabRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Resolves and manages amount-based exchange-rate slabs per corridor, optionally
 * scoped to a payout delivery method (cash vs bank vs mobile). A slab with a NULL
 * delivery method applies to every payment type (corridor-wide, legacy behaviour).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorridorRateSlabService {

    private final CorridorRateSlabRepository repository;

    /**
     * The slab rate for a corridor + send amount + payment type, if one matches.
     * Resolution: first a band configured for this exact delivery method, else a
     * corridor-wide (NULL delivery method) band. A slab matches when
     * amount &gt;= minAmount and (maxAmount is null OR amount &lt;= maxAmount).
     * Slabs are evaluated lowest-band first.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getSlabRate(Long corridorId, BigDecimal amount, String deliveryMethod) {
        if (corridorId == null || amount == null) return Optional.empty();
        // 1. Payment-type-specific band wins when present.
        if (deliveryMethod != null && !deliveryMethod.isBlank()) {
            Optional<BigDecimal> specific =
                    matchBand(repository.findActiveByCorridorAndMethod(corridorId, deliveryMethod), amount);
            if (specific.isPresent()) return specific;
        }
        // 2. Fall back to corridor-wide bands (apply to all payment types).
        return matchBand(repository.findActiveByCorridorNoMethod(corridorId), amount);
    }

    /** Backward-compatible overload: no payment type = corridor-wide slabs only. */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getSlabRate(Long corridorId, BigDecimal amount) {
        return getSlabRate(corridorId, amount, null);
    }

    private Optional<BigDecimal> matchBand(List<CorridorRateSlabEntity> slabs, BigDecimal amount) {
        for (CorridorRateSlabEntity slab : slabs) {
            boolean aboveMin = slab.getMinAmount() == null || amount.compareTo(slab.getMinAmount()) >= 0;
            boolean belowMax = slab.getMaxAmount() == null || amount.compareTo(slab.getMaxAmount()) <= 0;
            if (aboveMin && belowMax) return Optional.ofNullable(slab.getRate());
        }
        return Optional.empty();
    }

    /** List slabs for one scope. deliveryMethod null/blank = the corridor-wide (all-types) scope. */
    @Transactional(readOnly = true)
    public List<CorridorRateSlabEntity> list(Long corridorId, String deliveryMethod) {
        String scope = (deliveryMethod != null && !deliveryMethod.isBlank()) ? deliveryMethod : null;
        return repository.listByCorridorAndScope(corridorId, scope);
    }

    /** Backward-compatible: all slabs for a corridor regardless of payment type. */
    @Transactional(readOnly = true)
    public List<CorridorRateSlabEntity> list(Long corridorId) {
        return repository.findByCorridorIdOrderByMinAmountAsc(corridorId);
    }

    /**
     * Replace the full set of slabs for ONE scope (corridor + payment type). Other
     * payment types' slabs on the same corridor are left untouched.
     * deliveryMethod null/blank replaces the corridor-wide (all-types) scope.
     */
    @Transactional
    public List<CorridorRateSlabEntity> replaceSlabs(Long corridorId, String deliveryMethod,
                                                     List<CorridorRateSlabEntity> slabs) {
        String scope = (deliveryMethod != null && !deliveryMethod.isBlank()) ? deliveryMethod : null;
        repository.deleteByCorridorAndScope(corridorId, scope);
        if (slabs != null) {
            for (CorridorRateSlabEntity s : slabs) {
                s.setId(null);
                s.setCorridorId(corridorId);
                s.setDeliveryMethod(scope);
                if (s.getIsActive() == null) s.setIsActive(true);
                repository.save(s);
            }
        }
        log.info("Replaced rate slabs for corridor {} scope [{}] ({} slabs)",
                corridorId, scope == null ? "ALL" : scope, slabs == null ? 0 : slabs.size());
        return repository.listByCorridorAndScope(corridorId, scope);
    }

    /** Backward-compatible overload: replace the corridor-wide scope. */
    @Transactional
    public List<CorridorRateSlabEntity> replaceSlabs(Long corridorId, List<CorridorRateSlabEntity> slabs) {
        return replaceSlabs(corridorId, null, slabs);
    }
}

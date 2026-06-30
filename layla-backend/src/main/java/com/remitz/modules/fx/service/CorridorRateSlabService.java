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
 * Resolves and manages amount-based exchange-rate slabs per corridor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CorridorRateSlabService {

    private final CorridorRateSlabRepository repository;

    /**
     * The slab rate for a corridor + send amount, if one matches.
     * A slab matches when amount >= minAmount and (maxAmount is null OR amount <= maxAmount).
     * Slabs are evaluated lowest-band first.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> getSlabRate(Long corridorId, BigDecimal amount) {
        if (corridorId == null || amount == null) return Optional.empty();
        for (CorridorRateSlabEntity slab : repository.findByCorridorIdAndIsActiveTrueOrderByMinAmountAsc(corridorId)) {
            boolean aboveMin = slab.getMinAmount() == null || amount.compareTo(slab.getMinAmount()) >= 0;
            boolean belowMax = slab.getMaxAmount() == null || amount.compareTo(slab.getMaxAmount()) <= 0;
            if (aboveMin && belowMax) return Optional.ofNullable(slab.getRate());
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<CorridorRateSlabEntity> list(Long corridorId) {
        return repository.findByCorridorIdOrderByMinAmountAsc(corridorId);
    }

    /** Replace the full set of slabs for a corridor (admin "save all" semantics). */
    @Transactional
    public List<CorridorRateSlabEntity> replaceSlabs(Long corridorId, List<CorridorRateSlabEntity> slabs) {
        repository.deleteByCorridorId(corridorId);
        if (slabs != null) {
            for (CorridorRateSlabEntity s : slabs) {
                s.setId(null);
                s.setCorridorId(corridorId);
                if (s.getIsActive() == null) s.setIsActive(true);
                repository.save(s);
            }
        }
        log.info("Replaced rate slabs for corridor {} ({} slabs)", corridorId, slabs == null ? 0 : slabs.size());
        return repository.findByCorridorIdOrderByMinAmountAsc(corridorId);
    }
}

package com.remitz.modules.payout.usi.controller;

import com.remitz.modules.payout.usi.dto.UsiChargesResult;
import com.remitz.modules.payout.usi.dto.UsiRateRow;
import com.remitz.modules.payout.usi.service.UsiRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/**
 * Phase 1 (READ-ONLY): live USI rate/charges for corridors. Diagnostic only — does not change
 * customer quotes. Used by the FX Rates admin view to show the USI rate next to the manual/API rate.
 */
@RestController
@RequestMapping("/api/admin/usi/rates")
@RequiredArgsConstructor
@Tag(name = "USI Rates (Admin)", description = "Live USI getCharges rate per corridor (read-only, does not affect quotes)")
public class UsiRateController {

    private final UsiRateService usiRateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "USI live rate for every active corridor x payout method (getCharges, read-only)")
    public ResponseEntity<List<UsiRateRow>> activeRates(
            @RequestParam(required = false) BigDecimal amount) {
        return ResponseEntity.ok(usiRateService.activeCorridorRates(amount));
    }

    @GetMapping("/single")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "USI getCharges for one explicit corridor + trans_type (read-only)")
    public ResponseEntity<UsiChargesResult> single(
            @RequestParam String country,
            @RequestParam(defaultValue = "Account") String transType,
            @RequestParam(defaultValue = "GBP") String src,
            @RequestParam String dst,
            @RequestParam(required = false) BigDecimal amount) {
        BigDecimal amt = amount != null ? amount : BigDecimal.valueOf(100);
        return ResponseEntity.ok(usiRateService.single(country, transType, src, dst, amt));
    }
}

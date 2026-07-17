package com.remitz.modules.payout.usi.controller;

import com.remitz.modules.payout.usi.dto.UsiLedgerSummary;
import com.remitz.modules.payout.usi.service.UsiLedgerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/usi/ledger")
@RequiredArgsConstructor
@Tag(name = "USI Ledger (Admin)", description = "USI Money P&L — profit is the fee; USI commission shown for reference")
public class UsiLedgerController {

    private final UsiLedgerService usiLedgerService;

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Aggregate USI P&L (fee = profit) for a date range, with by-corridor, by-day and per-transaction breakdowns")
    public ResponseEntity<UsiLedgerSummary> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(usiLedgerService.summary(from, to));
    }
}

package com.remitz.modules.payout.usi.controller;

import com.remitz.modules.payout.usi.entity.UsiTestAccountEntity;
import com.remitz.modules.payout.usi.repository.UsiTestAccountRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payout/usi/test-accounts")
@RequiredArgsConstructor
@Tag(name = "USI Money — Sandbox Accounts",
        description = "Sandbox payout-account fixtures supplied by USI Money for each corridor (Uganda, Turkey, Egypt, Qatar, Saudi Arabia, UAE). Used by partner tooling and the beneficiary form for one-click populate during onboarding/QA.")
public class UsiTestAccountController {

    private final UsiTestAccountRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'PAYOUT_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List all active USI Money sandbox accounts (optional country filter)")
    public ResponseEntity<List<UsiTestAccountEntity>> list(
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String payoutType) {
        if (country != null && !country.isBlank() && payoutType != null && !payoutType.isBlank()) {
            return ResponseEntity.ok(repository.findByCountryCodeAndPayoutTypeAndIsActiveTrue(
                    country.toUpperCase(), payoutType.toUpperCase()));
        }
        if (country != null && !country.isBlank()) {
            return ResponseEntity.ok(repository.findByCountryCodeAndIsActiveTrueOrderByPayoutTypeAsc(country.toUpperCase()));
        }
        return ResponseEntity.ok(repository.findByIsActiveTrueOrderByCountryCodeAscPayoutTypeAsc());
    }
}

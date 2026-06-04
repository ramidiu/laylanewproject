package com.remitz.modules.fx.controller;

import com.remitz.common.dto.*;
import com.remitz.modules.fx.dto.CompetitorComparisonResponse;
import com.remitz.modules.fx.dto.CompetitorRateRequest;
import com.remitz.modules.fx.dto.NostroBalanceUpdateRequest;
import com.remitz.modules.fx.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/fx")
@RequiredArgsConstructor
@Tag(name = "FX Management", description = "FX rate management, quoting, margins, nostro and competitor rates")
public class FxController {

    private final FxRateService fxRateService;
    private final FxMarginService fxMarginService;
    private final QuoteService quoteService;
    private final NostroService nostroService;
    private final CompetitorRateService competitorRateService;

    // --- Quote ---

    @PostMapping("/quote")
    @Operation(summary = "Generate FX quote", description = "Generate a locked FX quote with rate, fee, and margin applied")
    public ResponseEntity<ApiResponse<QuoteResponse>> generateQuote(@Valid @RequestBody QuoteRequest request) {
        try {
            QuoteResponse quote = quoteService.generateQuote(request);
            return ResponseEntity.ok(ApiResponse.<QuoteResponse>builder()
                    .success(true)
                    .data(quote)
                    .message("Quote generated successfully")
                    .build());
        } catch (Exception e) {
            log.error("Quote generation failed: {} - {}", e.getClass().getSimpleName(), e.getMessage(), e);
            throw e;
        }
    }

    // --- Rates ---

    @GetMapping("/rates")
    @Operation(summary = "Get all current FX rates", description = "Returns current GBP-based rates for all supported corridors")
    public ResponseEntity<ApiResponse<List<FxRateResponse>>> getAllRates() {
        List<FxRateResponse> rates = fxRateService.getAllRates();
        return ResponseEntity.ok(ApiResponse.<List<FxRateResponse>>builder()
                .success(true)
                .data(rates)
                .message("Rates retrieved successfully")
                .build());
    }

    @GetMapping("/rates/{base}/{target}")
    @Operation(summary = "Get specific FX rate", description = "Returns the current rate for a specific currency pair")
    public ResponseEntity<ApiResponse<FxRateResponse>> getRate(
            @PathVariable String base, @PathVariable String target) {
        var rate = fxRateService.getRate(base, target);
        FxRateResponse response = FxRateResponse.builder()
                .baseCurrency(base)
                .targetCurrency(target)
                .rate(rate)
                .fetchedAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(ApiResponse.<FxRateResponse>builder()
                .success(true)
                .data(response)
                .message("Rate retrieved successfully")
                .build());
    }

    @GetMapping("/rates/history")
    @Operation(summary = "Get FX rate history", description = "Returns historical rates for a currency pair within a date range")
    public ResponseEntity<ApiResponse<List<FxRateResponse>>> getRateHistory(
            @RequestParam String base,
            @RequestParam String target,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        List<FxRateResponse> history = fxRateService.getRateHistory(base, target, from, to);
        return ResponseEntity.ok(ApiResponse.<List<FxRateResponse>>builder()
                .success(true)
                .data(history)
                .message("Rate history retrieved successfully")
                .build());
    }

    // --- Margins ---

    @PostMapping("/margins")
    @PreAuthorize("hasPermission(null, 'fx:set_margins')")
    @Operation(summary = "Create margin configuration", description = "Create a new FX margin configuration for a currency pair")
    public ResponseEntity<ApiResponse<MarginConfigResponse>> createMargin(
            @Valid @RequestBody MarginConfigRequest request) {
        MarginConfigResponse margin = fxMarginService.createMargin(request);
        return ResponseEntity.ok(ApiResponse.<MarginConfigResponse>builder()
                .success(true)
                .data(margin)
                .message("Margin created successfully")
                .build());
    }

    @GetMapping("/margins")
    @Operation(summary = "List all margin configurations", description = "Returns all FX margin configurations")
    public ResponseEntity<ApiResponse<List<MarginConfigResponse>>> listMargins() {
        List<MarginConfigResponse> margins = fxMarginService.listMargins();
        return ResponseEntity.ok(ApiResponse.<List<MarginConfigResponse>>builder()
                .success(true)
                .data(margins)
                .message("Margins retrieved successfully")
                .build());
    }

    @PutMapping("/margins/{id}")
    @PreAuthorize("hasPermission(null, 'fx:set_margins')")
    @Operation(summary = "Update margin configuration", description = "Update an existing FX margin configuration")
    public ResponseEntity<ApiResponse<MarginConfigResponse>> updateMargin(
            @PathVariable Long id, @Valid @RequestBody MarginConfigRequest request) {
        MarginConfigResponse margin = fxMarginService.updateMargin(id, request);
        return ResponseEntity.ok(ApiResponse.<MarginConfigResponse>builder()
                .success(true)
                .data(margin)
                .message("Margin updated successfully")
                .build());
    }

    // --- Nostro ---

    @GetMapping("/nostro")
    @PreAuthorize("hasPermission(null, 'fx:view_nostro')")
    @Operation(summary = "Get nostro account balances", description = "Returns all nostro account balances")
    public ResponseEntity<ApiResponse<List<NostroAccountResponse>>> getBalances() {
        List<NostroAccountResponse> balances = nostroService.getBalances();
        return ResponseEntity.ok(ApiResponse.<List<NostroAccountResponse>>builder()
                .success(true)
                .data(balances)
                .message("Nostro balances retrieved successfully")
                .build());
    }

    @PutMapping("/nostro/{id}")
    @PreAuthorize("hasPermission(null, 'fx:view_nostro')")
    @Operation(summary = "Update nostro account balance", description = "Manually update a nostro account balance")
    public ResponseEntity<ApiResponse<NostroAccountResponse>> updateBalance(
            @PathVariable Long id, @Valid @RequestBody NostroBalanceUpdateRequest request) {
        NostroAccountResponse response = nostroService.updateBalance(id, request.getNewBalance());
        return ResponseEntity.ok(ApiResponse.<NostroAccountResponse>builder()
                .success(true)
                .data(response)
                .message("Nostro balance updated successfully")
                .build());
    }

    // --- Competitor Rates ---

    @PostMapping("/competitor-rates")
    @Operation(summary = "Log competitor rate", description = "Log a competitor's FX rate for comparison")
    public ResponseEntity<ApiResponse<Void>> logRate(@Valid @RequestBody CompetitorRateRequest request) {
        competitorRateService.logRate(request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Competitor rate logged successfully")
                .build());
    }

    // ── Manual Rate Override ──────────────────────────────────────────────────

    @GetMapping("/rate-mode")
    @Operation(summary = "Get rate mode", description = "Returns whether manual rate override mode is enabled")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRateMode() {
        Map<String, Object> result = Map.of(
            "manualMode", fxRateService.isManualModeEnabled()
        );
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true).data(result).message("Rate mode retrieved").build());
    }

    @PutMapping("/rate-mode")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    @Operation(summary = "Set rate mode", description = "Superadmin only: toggle between automatic API rates and manual overrides")
    public ResponseEntity<ApiResponse<Void>> setRateMode(@RequestParam boolean enabled) {
        fxRateService.setManualModeEnabled(enabled);
        String msg = enabled ? "Manual rate mode enabled" : "Automatic rate mode enabled";
        return ResponseEntity.ok(ApiResponse.<Void>builder().success(true).message(msg).build());
    }

    @GetMapping("/manual-rates")
    @PreAuthorize("hasPermission(null, 'fx:set_margins')")
    @Operation(summary = "Get manual rates", description = "Returns all manually set exchange rate overrides")
    public ResponseEntity<ApiResponse<Map<String, BigDecimal>>> getManualRates() {
        return ResponseEntity.ok(ApiResponse.<Map<String, BigDecimal>>builder()
                .success(true).data(fxRateService.getManualRates()).message("Manual rates retrieved").build());
    }

    @PutMapping("/manual-rates/{base}/{target}")
    @PreAuthorize("hasPermission(null, 'fx:set_margins')")
    @Operation(summary = "Set manual rate", description = "Set a manual exchange rate override for a currency pair")
    public ResponseEntity<ApiResponse<Void>> setManualRate(
            @PathVariable String base, @PathVariable String target,
            @RequestParam BigDecimal rate, Principal principal) {
        if (rate.compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(ApiResponse.<Void>builder()
                    .success(false).message("Rate must be greater than zero").build());
        }
        String actor = principal != null ? principal.getName() : "ADMIN";
        fxRateService.setManualRate(base.toUpperCase(), target.toUpperCase(), rate, actor);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Manual rate set for " + base + "/" + target).build());
    }

    @DeleteMapping("/manual-rates/{base}/{target}")
    @PreAuthorize("hasPermission(null, 'fx:set_margins')")
    @Operation(summary = "Clear manual rate", description = "Remove a manual exchange rate override for a currency pair")
    public ResponseEntity<ApiResponse<Void>> clearManualRate(
            @PathVariable String base, @PathVariable String target, Principal principal) {
        String actor = principal != null ? principal.getName() : "ADMIN";
        fxRateService.clearManualRate(base.toUpperCase(), target.toUpperCase(), actor);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true).message("Manual rate cleared for " + base + "/" + target).build());
    }

    @GetMapping("/competitor-rates")
    @Operation(summary = "Get competitor rate comparison", description = "Compare our rates against competitors for a currency pair")
    public ResponseEntity<ApiResponse<CompetitorComparisonResponse>> getComparison(
            @RequestParam String sendCurrency, @RequestParam String receiveCurrency) {
        CompetitorComparisonResponse comparison = competitorRateService.getComparison(sendCurrency, receiveCurrency);
        return ResponseEntity.ok(ApiResponse.<CompetitorComparisonResponse>builder()
                .success(true)
                .data(comparison)
                .message("Competitor comparison retrieved successfully")
                .build());
    }
}

package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.PartnerSettlementRate;
import com.remitz.modules.transaction.entity.PayoutPartner;
import com.remitz.modules.transaction.entity.SettlementRate;
import com.remitz.modules.transaction.repository.PartnerSettlementRateRepository;
import com.remitz.modules.transaction.repository.PayoutPartnerRepository;
import com.remitz.modules.transaction.repository.SettlementRateRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions/settlement-rates")
@RequiredArgsConstructor
@Tag(name = "Settlement Rates", description = "Settlement rate management")
public class SettlementRateController {

    private final SettlementRateRepository settlementRateRepository;
    private final PartnerSettlementRateRepository partnerSettlementRateRepository;
    private final PayoutPartnerRepository payoutPartnerRepository;

    @GetMapping
    @Operation(summary = "Get all global settlement rates")
    public ResponseEntity<ApiResponse<List<SettlementRate>>> getAllRates() {
        List<SettlementRate> rates = settlementRateRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<SettlementRate>>builder()
                .success(true)
                .data(rates)
                .build());
    }

    @PutMapping("/{currency}")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Update global settlement rate")
    public ResponseEntity<ApiResponse<SettlementRate>> updateRate(@PathVariable String currency,
                                                                   @RequestBody Map<String, Object> body,
                                                                   Authentication authentication) {
        SettlementRate rate = settlementRateRepository.findByCurrency(currency)
                .orElseThrow(() -> new ResourceNotFoundException("SettlementRate", "currency", currency));

        rate.setRateToUsd(new BigDecimal(body.get("rateToUsd").toString()));
        rate.setUpdatedBy(authentication.getName());

        SettlementRate saved = settlementRateRepository.save(rate);
        return ResponseEntity.ok(ApiResponse.<SettlementRate>builder()
                .success(true)
                .data(saved)
                .message("Settlement rate updated successfully")
                .build());
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Add new global settlement rate")
    public ResponseEntity<ApiResponse<SettlementRate>> addRate(@RequestBody SettlementRate rate,
                                                                Authentication authentication) {
        rate.setUpdatedBy(authentication.getName());
        SettlementRate saved = settlementRateRepository.save(rate);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<SettlementRate>builder()
                        .success(true)
                        .data(saved)
                        .message("Settlement rate added successfully")
                        .build());
    }

    @GetMapping("/partners")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Get all partners with custom rates")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllPartnersWithRates() {
        List<PayoutPartner> partners = payoutPartnerRepository.findAll();
        List<Map<String, Object>> result = partners.stream()
                .map(p -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("partner", p);
                    map.put("rates", partnerSettlementRateRepository.findByPartnerId(p.getId()));
                    return map;
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @GetMapping("/partner/{partnerId}")
    @Operation(summary = "Get one partner's custom rates")
    public ResponseEntity<ApiResponse<List<PartnerSettlementRate>>> getPartnerRates(@PathVariable Long partnerId) {
        List<PartnerSettlementRate> rates = partnerSettlementRateRepository.findByPartnerId(partnerId);
        return ResponseEntity.ok(ApiResponse.<List<PartnerSettlementRate>>builder()
                .success(true)
                .data(rates)
                .build());
    }

    @PutMapping("/partner/{partnerId}/{currency}")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Operation(summary = "Set partner-specific rate override")
    public ResponseEntity<ApiResponse<PartnerSettlementRate>> setPartnerRate(
            @PathVariable Long partnerId, @PathVariable String currency,
            @RequestBody Map<String, Object> body, Authentication authentication) {

        PartnerSettlementRate rate = partnerSettlementRateRepository
                .findByPartnerIdAndCurrency(partnerId, currency)
                .orElse(PartnerSettlementRate.builder()
                        .partnerId(partnerId)
                        .currency(currency)
                        .build());

        rate.setRateToUsd(new BigDecimal(body.get("rateToUsd").toString()));
        rate.setUpdatedBy(authentication.getName());

        PartnerSettlementRate saved = partnerSettlementRateRepository.save(rate);
        return ResponseEntity.ok(ApiResponse.<PartnerSettlementRate>builder()
                .success(true)
                .data(saved)
                .message("Partner rate override set successfully")
                .build());
    }

    @DeleteMapping("/partner/{partnerId}/{currency}")
    @PreAuthorize("hasPermission(null, 'settlement:manage')")
    @Transactional
    @Operation(summary = "Remove partner rate override")
    public ResponseEntity<ApiResponse<Void>> removePartnerRate(
            @PathVariable Long partnerId, @PathVariable String currency) {
        partnerSettlementRateRepository.deleteByPartnerIdAndCurrency(partnerId, currency);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Partner rate override removed successfully")
                .build());
    }
}

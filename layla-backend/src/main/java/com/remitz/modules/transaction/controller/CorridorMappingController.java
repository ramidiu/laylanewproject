package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.modules.transaction.entity.CorridorFeeConfig;
import com.remitz.modules.transaction.entity.CorridorPartnerMapping;
import com.remitz.modules.transaction.entity.PayinPartner;
import com.remitz.modules.transaction.entity.PayoutPartner;
import com.remitz.modules.transaction.repository.CorridorFeeConfigRepository;
import com.remitz.modules.transaction.repository.CorridorPartnerMappingRepository;
import com.remitz.modules.transaction.repository.PayinPartnerRepository;
import com.remitz.modules.transaction.repository.PayoutPartnerRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/transactions/partners")
@RequiredArgsConstructor
@Tag(name = "Corridor Mappings", description = "Corridor-to-partner mapping management")
public class CorridorMappingController {

    private final CorridorPartnerMappingRepository corridorPartnerMappingRepository;
    private final CorridorFeeConfigRepository corridorFeeConfigRepository;
    private final PayinPartnerRepository payinPartnerRepository;
    private final PayoutPartnerRepository payoutPartnerRepository;

    @GetMapping("/corridor-mappings")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Operation(summary = "Get all corridor-partner mappings")
    public ResponseEntity<ApiResponse<List<CorridorPartnerMapping>>> getAllMappings() {
        List<CorridorPartnerMapping> mappings = corridorPartnerMappingRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<CorridorPartnerMapping>>builder()
                .success(true)
                .data(mappings)
                .build());
    }

    @PostMapping("/corridor-mappings")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Operation(summary = "Create or update corridor-partner mapping")
    public ResponseEntity<ApiResponse<CorridorPartnerMapping>> createMapping(@RequestBody CorridorPartnerMapping mapping) {
        CorridorPartnerMapping saved = corridorPartnerMappingRepository.save(mapping);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CorridorPartnerMapping>builder()
                        .success(true)
                        .data(saved)
                        .message("Corridor mapping created successfully")
                        .build());
    }

    @DeleteMapping("/corridor-mappings/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Operation(summary = "Remove corridor-partner mapping")
    public ResponseEntity<ApiResponse<Void>> deleteMapping(@PathVariable Long id) {
        corridorPartnerMappingRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Corridor mapping removed successfully")
                .build());
    }

    // ── Corridor Fee Config Endpoints ──────────────────────────────────

    @GetMapping("/corridor-configs")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Operation(summary = "Get all corridor fee configs with partner names")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAllCorridorConfigs() {
        List<CorridorFeeConfig> configs = corridorFeeConfigRepository.findAll();

        Map<Long, String> payinNames = new HashMap<>();
        payinPartnerRepository.findAll().forEach(p -> payinNames.put(p.getId(), p.getPartnerName()));

        Map<Long, String> payoutNames = new HashMap<>();
        payoutPartnerRepository.findAll().forEach(p -> payoutNames.put(p.getId(), p.getPartnerName()));

        List<Map<String, Object>> result = new ArrayList<>();
        for (CorridorFeeConfig cfg : configs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", cfg.getId());
            row.put("fromCurrency", cfg.getFromCurrency());
            row.put("toCurrency", cfg.getToCurrency());
            row.put("payinPartnerId", cfg.getPayinPartnerId());
            row.put("payinPartnerName", cfg.getPayinPartnerId() != null ? payinNames.getOrDefault(cfg.getPayinPartnerId(), "Unknown") : null);
            row.put("payinShareType", cfg.getPayinShareType());
            row.put("payinShareValue", cfg.getPayinShareValue());
            row.put("payoutPartnerId", cfg.getPayoutPartnerId());
            row.put("payoutPartnerName", cfg.getPayoutPartnerId() != null ? payoutNames.getOrDefault(cfg.getPayoutPartnerId(), "Unknown") : null);
            row.put("payoutShareType", cfg.getPayoutShareType());
            row.put("payoutShareValue", cfg.getPayoutShareValue());
            row.put("isActive", cfg.getIsActive());
            result.add(row);
        }

        return ResponseEntity.ok(ApiResponse.<List<Map<String, Object>>>builder()
                .success(true)
                .data(result)
                .build());
    }

    @PutMapping("/corridor-configs/{fromCurrency}/{toCurrency}")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Operation(summary = "Create or update corridor fee config for a currency pair")
    public ResponseEntity<ApiResponse<Map<String, Object>>> upsertCorridorConfig(
            @PathVariable String fromCurrency,
            @PathVariable String toCurrency,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        List<CorridorFeeConfig> existing = corridorFeeConfigRepository.findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
        CorridorFeeConfig config = existing.isEmpty() ? new CorridorFeeConfig() : existing.get(0);

        config.setFromCurrency(fromCurrency);
        config.setToCurrency(toCurrency);
        config.setPayinPartnerId(body.get("payinPartnerId") != null ? Long.valueOf(body.get("payinPartnerId").toString()) : null);
        config.setPayinShareType(body.get("payinShareType") != null ? body.get("payinShareType").toString() : null);
        config.setPayinShareValue(body.get("payinShareValue") != null ? new BigDecimal(body.get("payinShareValue").toString()) : null);
        config.setPayoutPartnerId(body.get("payoutPartnerId") != null ? Long.valueOf(body.get("payoutPartnerId").toString()) : null);
        config.setPayoutShareType(body.get("payoutShareType") != null ? body.get("payoutShareType").toString() : null);
        config.setPayoutShareValue(body.get("payoutShareValue") != null ? new BigDecimal(body.get("payoutShareValue").toString()) : null);
        config.setIsActive(true);
        if (authentication != null) {
            config.setUpdatedBy(authentication.getName());
        }

        CorridorFeeConfig saved = corridorFeeConfigRepository.save(config);

        // Sync corridor_partner_mappings so TransactionService auto-assigns this partner
        List<CorridorPartnerMapping> existingMappings = corridorPartnerMappingRepository
                .findByFromCurrencyAndToCurrency(fromCurrency, toCurrency);
        existingMappings.forEach(m -> corridorPartnerMappingRepository.deleteById(m.getId()));

        if (saved.getPayoutPartnerId() != null) {
            CorridorPartnerMapping mapping = CorridorPartnerMapping.builder()
                    .fromCurrency(fromCurrency)
                    .toCurrency(toCurrency)
                    .partnerId(saved.getPayoutPartnerId())
                    .isActive(true)
                    .build();
            corridorPartnerMappingRepository.save(mapping);
        }

        // Build response with partner names
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", saved.getId());
        result.put("fromCurrency", saved.getFromCurrency());
        result.put("toCurrency", saved.getToCurrency());
        result.put("payinPartnerId", saved.getPayinPartnerId());
        if (saved.getPayinPartnerId() != null) {
            result.put("payinPartnerName", payinPartnerRepository.findById(saved.getPayinPartnerId())
                    .map(PayinPartner::getPartnerName).orElse("Unknown"));
        }
        result.put("payinShareType", saved.getPayinShareType());
        result.put("payinShareValue", saved.getPayinShareValue());
        result.put("payoutPartnerId", saved.getPayoutPartnerId());
        if (saved.getPayoutPartnerId() != null) {
            result.put("payoutPartnerName", payoutPartnerRepository.findById(saved.getPayoutPartnerId())
                    .map(PayoutPartner::getPartnerName).orElse("Unknown"));
        }
        result.put("payoutShareType", saved.getPayoutShareType());
        result.put("payoutShareValue", saved.getPayoutShareValue());
        result.put("isActive", saved.getIsActive());

        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(result)
                .message("Corridor fee config saved successfully")
                .build());
    }
}

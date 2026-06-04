package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.CountryBankConfig;
import com.remitz.modules.transaction.entity.PaymentMethod;
import com.remitz.modules.transaction.entity.PayoutType;
import com.remitz.modules.transaction.repository.CountryBankConfigRepository;
import com.remitz.modules.transaction.repository.PaymentMethodRepository;
import com.remitz.modules.transaction.repository.PayoutTypeRepository;
import com.remitz.modules.transaction.service.TransactionCorridorSetupService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions/config")
@RequiredArgsConstructor
@Tag(name = "Transfer Config", description = "Payout types, payment methods, and country configuration")
public class TransferConfigController {

    private final PayoutTypeRepository payoutTypeRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final CountryBankConfigRepository countryBankConfigRepository;
    private final TransactionCorridorSetupService corridorAutoCreateService;

    // ---- Customer endpoints ----

    @GetMapping("/payout-types")
    @Operation(summary = "Get active payout types by currency")
    public ResponseEntity<ApiResponse<List<PayoutType>>> getActivePayoutTypes(@RequestParam String currency) {
        List<PayoutType> types = payoutTypeRepository.findByCurrencyAndIsActive(currency, true);
        return ResponseEntity.ok(ApiResponse.<List<PayoutType>>builder()
                .success(true)
                .data(types)
                .build());
    }

    @GetMapping("/payment-methods")
    @Operation(summary = "Get active payment methods by currency")
    public ResponseEntity<ApiResponse<List<PaymentMethod>>> getActivePaymentMethods(@RequestParam String currency) {
        List<PaymentMethod> methods = paymentMethodRepository.findByCurrencyAndIsActive(currency, true);
        return ResponseEntity.ok(ApiResponse.<List<PaymentMethod>>builder()
                .success(true)
                .data(methods)
                .build());
    }

    @GetMapping("/active-countries")
    @Operation(summary = "Get active sending countries (countries with active payment methods)")
    public ResponseEntity<ApiResponse<List<PaymentMethod>>> getActiveCountries() {
        // Return distinct countries that have at least one active payment method
        List<PaymentMethod> allActive = paymentMethodRepository.findAll().stream()
                .filter(pm -> Boolean.TRUE.equals(pm.getIsActive()))
                .toList();
        // Deduplicate by countryCode
        java.util.Map<String, PaymentMethod> seen = new java.util.LinkedHashMap<>();
        for (PaymentMethod pm : allActive) {
            seen.putIfAbsent(pm.getCountryCode(), pm);
        }
        return ResponseEntity.ok(ApiResponse.<List<PaymentMethod>>builder()
                .success(true)
                .data(new java.util.ArrayList<>(seen.values()))
                .build());
    }

    @GetMapping("/active-receive-countries")
    @Operation(summary = "Get active receiving countries (countries with active payout types)")
    public ResponseEntity<ApiResponse<List<PayoutType>>> getActiveReceiveCountries() {
        List<PayoutType> allActive = payoutTypeRepository.findAll().stream()
                .filter(pt -> Boolean.TRUE.equals(pt.getIsActive()))
                .toList();
        java.util.Map<String, PayoutType> seen = new java.util.LinkedHashMap<>();
        for (PayoutType pt : allActive) {
            seen.putIfAbsent(pt.getCountryCode(), pt);
        }
        return ResponseEntity.ok(ApiResponse.<List<PayoutType>>builder()
                .success(true)
                .data(new java.util.ArrayList<>(seen.values()))
                .build());
    }

    // ---- Admin endpoints ----

    @GetMapping("/admin/payout-types")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Get all payout types (admin)")
    public ResponseEntity<ApiResponse<List<PayoutType>>> getAllPayoutTypes() {
        List<PayoutType> types = payoutTypeRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<PayoutType>>builder()
                .success(true)
                .data(types)
                .build());
    }

    @PutMapping("/admin/payout-types/{id}/toggle")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Toggle single payout type")
    public ResponseEntity<ApiResponse<PayoutType>> togglePayoutType(@PathVariable Long id) {
        PayoutType type = payoutTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PayoutType", "id", id));
        type.setIsActive(!type.getIsActive());
        PayoutType saved = payoutTypeRepository.save(type);

        // Auto-create corridors when payout type is activated
        if (Boolean.TRUE.equals(saved.getIsActive())) {
            corridorAutoCreateService.onPayoutTypeActivated(saved);
        }

        return ResponseEntity.ok(ApiResponse.<PayoutType>builder()
                .success(true)
                .data(saved)
                .message("Payout type toggled successfully")
                .build());
    }

    @PutMapping("/admin/payout-types/country/{code}/toggle")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Toggle all payout types for a country")
    public ResponseEntity<ApiResponse<List<PayoutType>>> togglePayoutTypesForCountry(
            @PathVariable String code, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        List<PayoutType> types = payoutTypeRepository.findByCountryCode(code);
        types.forEach(t -> t.setIsActive(active));
        List<PayoutType> saved = payoutTypeRepository.saveAll(types);

        // Auto-create corridors when payout types are activated for a country
        if (active) {
            corridorAutoCreateService.onPayoutTypesForCountryActivated(saved);
        }

        return ResponseEntity.ok(ApiResponse.<List<PayoutType>>builder()
                .success(true)
                .data(saved)
                .message("Payout types toggled for country " + code)
                .build());
    }

    @GetMapping("/admin/payment-methods")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Get all payment methods (admin)")
    public ResponseEntity<ApiResponse<List<PaymentMethod>>> getAllPaymentMethods() {
        List<PaymentMethod> methods = paymentMethodRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<PaymentMethod>>builder()
                .success(true)
                .data(methods)
                .build());
    }

    @PutMapping("/admin/payment-methods/{id}/toggle")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Toggle single payment method")
    public ResponseEntity<ApiResponse<PaymentMethod>> togglePaymentMethod(@PathVariable Long id) {
        PaymentMethod method = paymentMethodRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PaymentMethod", "id", id));
        method.setIsActive(!method.getIsActive());
        PaymentMethod saved = paymentMethodRepository.save(method);

        // Auto-create corridors when payment method is activated
        if (Boolean.TRUE.equals(saved.getIsActive())) {
            corridorAutoCreateService.onPaymentMethodActivated(saved);
        }

        return ResponseEntity.ok(ApiResponse.<PaymentMethod>builder()
                .success(true)
                .data(saved)
                .message("Payment method toggled successfully")
                .build());
    }

    @PutMapping("/admin/payment-methods/country/{code}/toggle")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Toggle all payment methods for a country")
    public ResponseEntity<ApiResponse<List<PaymentMethod>>> togglePaymentMethodsForCountry(
            @PathVariable String code, @RequestBody Map<String, Boolean> body) {
        boolean active = Boolean.TRUE.equals(body.get("active"));
        List<PaymentMethod> methods = paymentMethodRepository.findByCountryCode(code);
        methods.forEach(m -> m.setIsActive(active));
        List<PaymentMethod> saved = paymentMethodRepository.saveAll(methods);

        // Auto-create corridors when payment methods are activated for a country
        if (active) {
            corridorAutoCreateService.onPaymentMethodsForCountryActivated(saved);
        }

        return ResponseEntity.ok(ApiResponse.<List<PaymentMethod>>builder()
                .success(true)
                .data(saved)
                .message("Payment methods toggled for country " + code)
                .build());
    }
}

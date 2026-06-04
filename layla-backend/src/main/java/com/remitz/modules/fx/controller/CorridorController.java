package com.remitz.modules.fx.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.dto.CorridorFeeResponse;
import com.remitz.common.dto.CorridorResponse;
import com.remitz.common.enums.KycTier;
import com.remitz.modules.fx.dto.CorridorCreateRequest;
import com.remitz.modules.fx.dto.CorridorFeeRequest;
import com.remitz.modules.fx.dto.CorridorLimitsResponse;
import com.remitz.modules.fx.dto.CorridorUpdateRequest;
import com.remitz.modules.fx.dto.DeliveryMethodResponse;
import com.remitz.modules.fx.dto.CorridorAutoCreateRequest;
import com.remitz.modules.fx.service.CorridorAutoCreateService;
import com.remitz.modules.fx.service.CorridorFeeService;
import com.remitz.modules.fx.service.CorridorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class CorridorController {

    private final CorridorService corridorService;
    private final CorridorFeeService corridorFeeService;
    private final CorridorAutoCreateService corridorAutoCreateService;

    // ============================================
    // Customer-facing endpoints: /api/corridors
    // ============================================

    @GetMapping("/api/corridors")
    @Tag(name = "Corridors", description = "Customer-facing corridor endpoints")
    @Operation(summary = "Get active corridors", description = "Returns all active corridors available for remittance")
    public ResponseEntity<ApiResponse<List<CorridorResponse>>> getActiveCorridors() {
        List<CorridorResponse> corridors = corridorService.getActiveCorridors();
        return ResponseEntity.ok(ApiResponse.<List<CorridorResponse>>builder()
                .success(true)
                .data(corridors)
                .message("Active corridors retrieved successfully")
                .build());
    }

    @GetMapping("/api/corridors/{id}/delivery-methods")
    @Tag(name = "Corridors")
    @Operation(summary = "Get delivery methods for corridor", description = "Returns available delivery methods for a specific corridor")
    public ResponseEntity<ApiResponse<List<DeliveryMethodResponse>>> getDeliveryMethods(@PathVariable Long id) {
        List<DeliveryMethodResponse> methods = corridorService.getDeliveryMethods(id);
        return ResponseEntity.ok(ApiResponse.<List<DeliveryMethodResponse>>builder()
                .success(true)
                .data(methods)
                .message("Delivery methods retrieved successfully")
                .build());
    }

    @GetMapping("/api/corridors/{id}/limits")
    @Tag(name = "Corridors")
    @Operation(summary = "Get corridor limits", description = "Returns transfer limits for a specific corridor and KYC tier")
    public ResponseEntity<ApiResponse<CorridorLimitsResponse>> getCorridorLimits(
            @PathVariable Long id, @RequestParam(required = false) KycTier tier) {
        CorridorLimitsResponse limits = corridorService.getCorridorLimits(id, tier);
        return ResponseEntity.ok(ApiResponse.<CorridorLimitsResponse>builder()
                .success(true)
                .data(limits)
                .message("Corridor limits retrieved successfully")
                .build());
    }

    // ============================================
    // Internal endpoint: auto-create corridors
    // ============================================

    @PostMapping("/api/fx/corridors/auto-create")
    @Tag(name = "Corridors Internal", description = "Internal corridor auto-creation endpoint")
    @Operation(summary = "Auto-create corridor", description = "Creates a corridor with defaults if it does not already exist. Called internally by transaction-service.")
    public ResponseEntity<ApiResponse<CorridorResponse>> autoCreateCorridor(
            @RequestBody CorridorAutoCreateRequest request) {
        CorridorResponse corridor = corridorAutoCreateService.autoCreateCorridor(request);
        return ResponseEntity.ok(ApiResponse.<CorridorResponse>builder()
                .success(true)
                .data(corridor)
                .message(corridor != null ? "Corridor auto-created or already exists" : "Skipped (same currency pair)")
                .build());
    }

    // ============================================
    // Admin endpoints: /api/admin/corridors
    // ============================================

    @PostMapping("/api/admin/corridors")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin", description = "Admin corridor management endpoints")
    @Operation(summary = "Create corridor", description = "Create a new remittance corridor")
    public ResponseEntity<ApiResponse<CorridorResponse>> createCorridor(
            @Valid @RequestBody CorridorCreateRequest request) {
        CorridorResponse corridor = corridorService.createCorridor(request);
        return ResponseEntity.ok(ApiResponse.<CorridorResponse>builder()
                .success(true)
                .data(corridor)
                .message("Corridor created successfully")
                .build());
    }

    @GetMapping("/api/admin/corridors")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Get all corridors", description = "Returns all corridors including inactive ones")
    public ResponseEntity<ApiResponse<List<CorridorResponse>>> getAllCorridors() {
        List<CorridorResponse> corridors = corridorService.getAllCorridors();
        return ResponseEntity.ok(ApiResponse.<List<CorridorResponse>>builder()
                .success(true)
                .data(corridors)
                .message("All corridors retrieved successfully")
                .build());
    }

    @PutMapping("/api/admin/corridors/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Update corridor", description = "Update an existing corridor configuration")
    public ResponseEntity<ApiResponse<CorridorResponse>> updateCorridor(
            @PathVariable Long id, @Valid @RequestBody CorridorUpdateRequest request) {
        CorridorResponse corridor = corridorService.updateCorridor(id, request);
        return ResponseEntity.ok(ApiResponse.<CorridorResponse>builder()
                .success(true)
                .data(corridor)
                .message("Corridor updated successfully")
                .build());
    }

    @PutMapping("/api/admin/corridors/{id}/toggle")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Toggle corridor active status", description = "Enable or disable a corridor")
    public ResponseEntity<ApiResponse<CorridorResponse>> toggleCorridor(@PathVariable Long id) {
        CorridorResponse corridor = corridorService.toggleCorridor(id);
        return ResponseEntity.ok(ApiResponse.<CorridorResponse>builder()
                .success(true)
                .data(corridor)
                .message("Corridor toggled successfully")
                .build());
    }

    @PostMapping("/api/admin/corridors/{id}/fees")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Add fee to corridor", description = "Add a new fee configuration for a corridor and delivery method")
    public ResponseEntity<ApiResponse<CorridorFeeResponse>> addFee(
            @PathVariable Long id, @Valid @RequestBody CorridorFeeRequest request, Principal principal) {
        request.setUpdatedBy(principal != null ? principal.getName() : "ADMIN");
        CorridorFeeResponse fee = corridorFeeService.addFee(id, request);
        return ResponseEntity.ok(ApiResponse.<CorridorFeeResponse>builder()
                .success(true)
                .data(fee)
                .message("Corridor fee added successfully")
                .build());
    }

    @PutMapping("/api/admin/corridors/{id}/fees/{feeId}")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Update corridor fee", description = "Update an existing fee configuration")
    public ResponseEntity<ApiResponse<CorridorFeeResponse>> updateFee(
            @PathVariable Long id, @PathVariable Long feeId,
            @Valid @RequestBody CorridorFeeRequest request, Principal principal) {
        request.setUpdatedBy(principal != null ? principal.getName() : "ADMIN");
        CorridorFeeResponse fee = corridorFeeService.updateFee(feeId, request);
        return ResponseEntity.ok(ApiResponse.<CorridorFeeResponse>builder()
                .success(true)
                .data(fee)
                .message("Corridor fee updated successfully")
                .build());
    }

    @DeleteMapping("/api/admin/corridors/fees/{feeId}")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Delete corridor fee", description = "Delete a fee configuration")
    public ResponseEntity<ApiResponse<Void>> deleteFee(@PathVariable Long feeId, Principal principal) {
        corridorFeeService.deleteFee(feeId, principal != null ? principal.getName() : "ADMIN");
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Corridor fee deleted successfully")
                .build());
    }

    @GetMapping("/api/fx/corridor-fees")
    @PreAuthorize("hasPermission(null, 'fx:manage_rates')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Get all corridor fees", description = "Returns all corridor fee configurations")
    public ResponseEntity<ApiResponse<List<CorridorFeeResponse>>> getAllFees() {
        List<CorridorFeeResponse> fees = corridorFeeService.getAllFees();
        return ResponseEntity.ok(ApiResponse.<List<CorridorFeeResponse>>builder()
                .success(true)
                .data(fees)
                .message("Corridor fees retrieved successfully")
                .build());
    }

    @GetMapping("/api/fx/corridor-fees/corridor/{corridorId}")
    @PreAuthorize("hasPermission(null, 'fx:manage_rates')")
    @Tag(name = "Corridor Admin")
    @Operation(summary = "Get fees for corridor", description = "Returns fee configurations for a specific corridor")
    public ResponseEntity<ApiResponse<List<CorridorFeeResponse>>> getFeesForCorridor(@PathVariable Long corridorId) {
        List<CorridorFeeResponse> fees = corridorFeeService.getFeesByCorridorId(corridorId);
        return ResponseEntity.ok(ApiResponse.<List<CorridorFeeResponse>>builder()
                .success(true)
                .data(fees)
                .message("Corridor fees retrieved successfully")
                .build());
    }
}

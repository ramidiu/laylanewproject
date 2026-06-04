package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.CashCollectionPoint;
import com.remitz.modules.transaction.entity.MobileMoneyService;
import com.remitz.modules.transaction.repository.CashCollectionPointRepository;
import com.remitz.modules.transaction.repository.MobileMoneyServiceRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController("transactionLookupController")
@RequestMapping("/api/transactions/lookup")
@RequiredArgsConstructor
@Tag(name = "Lookup", description = "Mobile money services, cash collection points, and relations")
public class LookupController {

    private final MobileMoneyServiceRepository mobileMoneyServiceRepository;
    private final CashCollectionPointRepository cashCollectionPointRepository;

    // ---- Customer endpoints ----

    @GetMapping("/mobile-services")
    @Operation(summary = "Get active mobile money services by country")
    public ResponseEntity<ApiResponse<List<MobileMoneyService>>> getMobileServices(
            @RequestParam String countryCode) {
        List<MobileMoneyService> services = mobileMoneyServiceRepository
                .findByCountryCodeAndIsActive(countryCode, true);
        return ResponseEntity.ok(ApiResponse.<List<MobileMoneyService>>builder()
                .success(true)
                .data(services)
                .build());
    }

    @GetMapping("/cash-points")
    @Operation(summary = "Get active cash collection points by country")
    public ResponseEntity<ApiResponse<List<CashCollectionPoint>>> getCashPoints(
            @RequestParam String countryCode) {
        List<CashCollectionPoint> points = cashCollectionPointRepository
                .findByCountryCodeAndIsActive(countryCode, true);
        return ResponseEntity.ok(ApiResponse.<List<CashCollectionPoint>>builder()
                .success(true)
                .data(points)
                .build());
    }

    @GetMapping("/relations")
    @Operation(summary = "Get active relations list")
    public ResponseEntity<ApiResponse<List<String>>> getRelations() {
        // Static list of relations commonly used in remittance
        List<String> relations = List.of(
                "Father", "Mother", "Brother", "Sister", "Spouse", "Son", "Daughter",
                "Uncle", "Aunt", "Cousin", "Friend", "Business Partner", "Employee",
                "Employer", "Self", "Other"
        );
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .success(true)
                .data(relations)
                .build());
    }

    // ---- Admin CRUD for mobile services ----

    @GetMapping("/admin/mobile-services")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Get all mobile money services (admin)")
    public ResponseEntity<ApiResponse<List<MobileMoneyService>>> getAllMobileServices() {
        List<MobileMoneyService> services = mobileMoneyServiceRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<MobileMoneyService>>builder()
                .success(true)
                .data(services)
                .build());
    }

    @PostMapping("/admin/mobile-services")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Add mobile money service")
    public ResponseEntity<ApiResponse<MobileMoneyService>> addMobileService(
            @RequestBody MobileMoneyService service) {
        MobileMoneyService saved = mobileMoneyServiceRepository.save(service);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<MobileMoneyService>builder()
                        .success(true)
                        .data(saved)
                        .message("Mobile money service added successfully")
                        .build());
    }

    @PutMapping("/admin/mobile-services/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Update mobile money service")
    public ResponseEntity<ApiResponse<MobileMoneyService>> updateMobileService(
            @PathVariable Long id, @RequestBody MobileMoneyService request) {
        MobileMoneyService service = mobileMoneyServiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("MobileMoneyService", "id", id));

        if (request.getServiceName() != null) service.setServiceName(request.getServiceName());
        if (request.getCountryCode() != null) service.setCountryCode(request.getCountryCode());
        if (request.getCountryName() != null) service.setCountryName(request.getCountryName());
        if (request.getIsActive() != null) service.setIsActive(request.getIsActive());

        MobileMoneyService saved = mobileMoneyServiceRepository.save(service);
        return ResponseEntity.ok(ApiResponse.<MobileMoneyService>builder()
                .success(true)
                .data(saved)
                .message("Mobile money service updated successfully")
                .build());
    }

    @DeleteMapping("/admin/mobile-services/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Delete mobile money service")
    public ResponseEntity<ApiResponse<Void>> deleteMobileService(@PathVariable Long id) {
        mobileMoneyServiceRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Mobile money service deleted successfully")
                .build());
    }

    // ---- Admin CRUD for cash collection points ----

    @GetMapping("/admin/cash-points")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Get all cash collection points (admin)")
    public ResponseEntity<ApiResponse<List<CashCollectionPoint>>> getAllCashPoints() {
        List<CashCollectionPoint> points = cashCollectionPointRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<CashCollectionPoint>>builder()
                .success(true)
                .data(points)
                .build());
    }

    @PostMapping("/admin/cash-points")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Add cash collection point")
    public ResponseEntity<ApiResponse<CashCollectionPoint>> addCashPoint(
            @RequestBody CashCollectionPoint point) {
        CashCollectionPoint saved = cashCollectionPointRepository.save(point);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<CashCollectionPoint>builder()
                        .success(true)
                        .data(saved)
                        .message("Cash collection point added successfully")
                        .build());
    }

    @PutMapping("/admin/cash-points/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Update cash collection point")
    public ResponseEntity<ApiResponse<CashCollectionPoint>> updateCashPoint(
            @PathVariable Long id, @RequestBody CashCollectionPoint request) {
        CashCollectionPoint point = cashCollectionPointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CashCollectionPoint", "id", id));

        if (request.getPointName() != null) point.setPointName(request.getPointName());
        if (request.getCountryCode() != null) point.setCountryCode(request.getCountryCode());
        if (request.getCountryName() != null) point.setCountryName(request.getCountryName());
        if (request.getAddress() != null) point.setAddress(request.getAddress());
        if (request.getCity() != null) point.setCity(request.getCity());
        if (request.getContactNumber() != null) point.setContactNumber(request.getContactNumber());
        if (request.getIsActive() != null) point.setIsActive(request.getIsActive());

        CashCollectionPoint saved = cashCollectionPointRepository.save(point);
        return ResponseEntity.ok(ApiResponse.<CashCollectionPoint>builder()
                .success(true)
                .data(saved)
                .message("Cash collection point updated successfully")
                .build());
    }

    @DeleteMapping("/admin/cash-points/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Delete cash collection point")
    public ResponseEntity<ApiResponse<Void>> deleteCashPoint(@PathVariable Long id) {
        cashCollectionPointRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Cash collection point deleted successfully")
                .build());
    }
}

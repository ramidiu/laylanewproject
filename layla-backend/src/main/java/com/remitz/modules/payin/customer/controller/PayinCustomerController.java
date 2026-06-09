package com.remitz.modules.payin.customer.controller;

import com.remitz.modules.payin.customer.dto.CreateCustomerRequest;
import com.remitz.modules.payin.customer.dto.CreateCustomerResponse;
import com.remitz.modules.payin.customer.dto.PayinCustomerDto;
import com.remitz.modules.payin.customer.service.PayinCustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payin/customer")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PayIn Customer", description = "Customer management for PayIn partners")
public class PayinCustomerController {

    private final PayinCustomerService service;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create customer", description = "Creates a new customer record via PayIn partner")
    public ResponseEntity<CreateCustomerResponse> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().get(0).getDefaultMessage();
            log.warn("Customer creation validation failed: {}", message);
            return ResponseEntity.badRequest().body(CreateCustomerResponse.failure(message));
        }

        return ResponseEntity.ok(service.createCustomer(request));
    }

    @PostMapping("/backfill-login")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Backfill login accounts",
            description = "One-off: (re)provision login accounts with the default password + force-change flag for all existing pay-in customers")
    public ResponseEntity<java.util.Map<String, Object>> backfillLogins() {
        int count = service.backfillLoginAccounts();
        return ResponseEntity.ok(java.util.Map.of("success", true, "processed", count));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List customers", description = "Returns payin customers + UK frontend users combined")
    public ResponseEntity<List<PayinCustomerDto>> listCustomers() {
        return ResponseEntity.ok(service.listAllCustomers());
    }

    @PutMapping("/user/{userId}/payin-toggle")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Toggle frontend user payin access", description = "Enables or disables a UK frontend user for payin transactions")
    public ResponseEntity<java.util.Map<String, Object>> togglePayin(@PathVariable Long userId) {
        boolean enabled = service.toggleFrontendUserPayin(userId);
        return ResponseEntity.ok(java.util.Map.of("userId", userId, "payinEnabled", enabled));
    }

    @PutMapping("/{customerId}/profile")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update payin customer profile",
            description = "Update DOB and/or verification status. Used by partner KYC step in create-transaction flow.")
    public ResponseEntity<PayinCustomerDto> updateProfile(
            @PathVariable String customerId,
            @RequestBody java.util.Map<String, Object> body) {
        java.time.LocalDate dob = null;
        if (body.get("dob") != null && !body.get("dob").toString().isBlank()) {
            dob = java.time.LocalDate.parse(body.get("dob").toString());
        }
        Boolean isVerified = body.get("isVerified") != null
                ? Boolean.valueOf(body.get("isVerified").toString())
                : null;
        return ResponseEntity.ok(service.updateProfile(customerId, dob, isVerified));
    }
}

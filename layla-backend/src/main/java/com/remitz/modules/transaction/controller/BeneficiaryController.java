package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.dto.BeneficiaryResponse;
import com.remitz.common.dto.CreateBeneficiaryRequest;
import com.remitz.common.dto.UpdateBeneficiaryRequest;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.transaction.service.BeneficiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
@Tag(name = "Beneficiaries", description = "Beneficiary management")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final UserRepository userRepository;

    @PostMapping
    @Operation(summary = "Add a beneficiary", description = "Creates a new beneficiary for the authenticated user")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> addBeneficiary(
            @Valid @RequestBody CreateBeneficiaryRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        BeneficiaryResponse response = beneficiaryService.addBeneficiary(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BeneficiaryResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Beneficiary added successfully")
                        .build());
    }

    @GetMapping
    @Operation(summary = "List beneficiaries", description = "Lists all beneficiaries for the authenticated user, optionally filtered by favourites")
    public ResponseEntity<ApiResponse<List<BeneficiaryResponse>>> listBeneficiaries(
            @RequestParam(required = false, defaultValue = "false") Boolean favourite,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<BeneficiaryResponse> responses;
        if (Boolean.TRUE.equals(favourite)) {
            responses = beneficiaryService.getFavouriteBeneficiaries(userId);
        } else {
            responses = beneficiaryService.getBeneficiaries(userId);
        }
        return ResponseEntity.ok(ApiResponse.<List<BeneficiaryResponse>>builder()
                .success(true)
                .data(responses)
                .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a beneficiary by ID", description = "Retrieves a single beneficiary by ID for the authenticated user")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> getBeneficiary(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        BeneficiaryResponse response = beneficiaryService.getBeneficiary(userId, id);
        return ResponseEntity.ok(ApiResponse.<BeneficiaryResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a beneficiary", description = "Updates an existing beneficiary")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> updateBeneficiary(
            @PathVariable Long id,
            @Valid @RequestBody UpdateBeneficiaryRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        BeneficiaryResponse response = beneficiaryService.updateBeneficiary(userId, id, request);
        return ResponseEntity.ok(ApiResponse.<BeneficiaryResponse>builder()
                .success(true)
                .data(response)
                .message("Beneficiary updated successfully")
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a beneficiary", description = "Soft-deletes a beneficiary by blocking it")
    public ResponseEntity<ApiResponse<Void>> deleteBeneficiary(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        beneficiaryService.deleteBeneficiary(userId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Beneficiary deleted successfully")
                .build());
    }

    @GetMapping("/check-duplicates")
    @Operation(summary = "Check for duplicate beneficiaries", description = "Checks if similar beneficiaries already exist for the user")
    public ResponseEntity<ApiResponse<List<BeneficiaryResponse>>> checkDuplicates(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String accountNumber,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<BeneficiaryResponse> duplicates = beneficiaryService.checkDuplicates(userId, fullName, accountNumber);
        return ResponseEntity.ok(ApiResponse.<List<BeneficiaryResponse>>builder()
                .success(true)
                .data(duplicates)
                .build());
    }

    private Long extractUserId(Authentication authentication) {
        String principal = authentication.getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            return userRepository.findByUuid(principal)
                    .map(u -> u.getId())
                    .orElseThrow(() -> new com.remitz.common.exception.ResourceNotFoundException("User", "uuid", principal));
        }
    }
}

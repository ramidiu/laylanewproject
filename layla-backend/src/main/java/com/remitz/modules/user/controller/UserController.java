package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.dto.UserResponse;
import com.remitz.common.dto.UserUpdateRequest;
import com.remitz.modules.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "APIs for user profile management")
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get user by UUID", description = "Retrieve a user profile by their UUID")
    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<UserResponse>> getUser(
            @Parameter(description = "User UUID") @PathVariable String uuid) {
        UserResponse user = userService.getUserByUuid(uuid);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true)
                .data(user)
                .message("User retrieved successfully")
                .build());
    }

    @Operation(summary = "Update user profile", description = "Update a user's profile information")
    @PutMapping("/{uuid}")
    public ResponseEntity<ApiResponse<UserResponse>> updateUser(
            @Parameter(description = "User UUID") @PathVariable String uuid,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse user = userService.updateUser(uuid, request);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true)
                .data(user)
                .message("User updated successfully")
                .build());
    }

    @Operation(summary = "List users", description = "List all users with optional filtering and pagination (admin only)")
    @GetMapping
    @PreAuthorize("hasPermission(null, 'user:view')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> listUsers(
            @Parameter(description = "Page number (0-indexed)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Search term (name or email)") @RequestParam(required = false) String search,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by KYC tier") @RequestParam(required = false) String kycTier,
            @Parameter(description = "Filter by KYC status: VERIFIED, REJECTED, PENDING, PARTIAL") @RequestParam(required = false) String kycStatus,
            @Parameter(description = "Sort: alpha (A-Z by name), recent (last updated), default = createdAt desc") @RequestParam(required = false) String sort) {
        Page<UserResponse> users = userService.listUsers(page, size, search, status, kycTier, kycStatus, sort);
        return ResponseEntity.ok(ApiResponse.<Page<UserResponse>>builder()
                .success(true)
                .data(users)
                .message("Users retrieved successfully")
                .build());
    }

    @Operation(summary = "Suspend user", description = "Suspend a user account (admin only)")
    @PutMapping("/{id}/suspend")
    @PreAuthorize("hasPermission(null, 'user:suspend')")
    public ResponseEntity<ApiResponse<UserResponse>> suspendUser(
            @Parameter(description = "User ID") @PathVariable Long id,
            @Parameter(description = "Suspension reason") @RequestParam(required = false) String reason) {
        UserResponse user = userService.suspendUser(id, reason);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true)
                .data(user)
                .message("User suspended successfully")
                .build());
    }

    @Operation(summary = "Activate user", description = "Activate a suspended user account (admin only)")
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasPermission(null, 'user:suspend')")
    public ResponseEntity<ApiResponse<UserResponse>> activateUser(
            @Parameter(description = "User ID") @PathVariable Long id) {
        UserResponse user = userService.activateUser(id);
        return ResponseEntity.ok(ApiResponse.<UserResponse>builder()
                .success(true)
                .data(user)
                .message("User activated successfully")
                .build());
    }
}

package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.modules.transaction.dto.CreateRecurringTransferRequest;
import com.remitz.modules.transaction.dto.RecurringTransferResponse;
import com.remitz.modules.transaction.dto.UpdateRecurringTransferRequest;
import com.remitz.modules.transaction.service.RecurringTransferService;
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
@RequestMapping("/api/recurring-transfers")
@RequiredArgsConstructor
@Tag(name = "Recurring Transfers", description = "Recurring transfer schedule management")
public class RecurringTransferController {

    private final RecurringTransferService recurringTransferService;

    @PostMapping
    @Operation(summary = "Create recurring transfer schedule", description = "Creates a new recurring transfer schedule")
    public ResponseEntity<ApiResponse<RecurringTransferResponse>> createSchedule(
            @Valid @RequestBody CreateRecurringTransferRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        RecurringTransferResponse response = recurringTransferService.createSchedule(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<RecurringTransferResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Recurring transfer schedule created successfully")
                        .build());
    }

    @GetMapping
    @Operation(summary = "List recurring transfer schedules", description = "Lists all recurring transfer schedules for the authenticated user")
    public ResponseEntity<ApiResponse<List<RecurringTransferResponse>>> listSchedules(
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<RecurringTransferResponse> responses = recurringTransferService.getSchedules(userId);
        return ResponseEntity.ok(ApiResponse.<List<RecurringTransferResponse>>builder()
                .success(true)
                .data(responses)
                .build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update recurring transfer schedule", description = "Updates an existing recurring transfer schedule")
    public ResponseEntity<ApiResponse<RecurringTransferResponse>> updateSchedule(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRecurringTransferRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        RecurringTransferResponse response = recurringTransferService.updateSchedule(userId, id, request);
        return ResponseEntity.ok(ApiResponse.<RecurringTransferResponse>builder()
                .success(true)
                .data(response)
                .message("Recurring transfer schedule updated successfully")
                .build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Cancel recurring transfer schedule", description = "Cancels a recurring transfer schedule")
    public ResponseEntity<ApiResponse<Void>> cancelSchedule(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        recurringTransferService.cancelSchedule(userId, id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Recurring transfer schedule cancelled successfully")
                .build());
    }

    private Long extractUserId(Authentication authentication) {
        String principal = authentication.getName();
        try {
            return Long.parseLong(principal);
        } catch (NumberFormatException e) {
            return (long) principal.hashCode();
        }
    }
}

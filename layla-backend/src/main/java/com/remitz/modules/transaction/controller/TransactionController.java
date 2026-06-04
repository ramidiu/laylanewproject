package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.*;
import com.remitz.common.enums.ActorType;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.transaction.service.TransactionReceiptService;
import com.remitz.modules.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Tag(name = "Transactions", description = "Transaction lifecycle management")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionReceiptService transactionReceiptService;
    private final UserRepository userRepository;

    @GetMapping(value = "/{id}/receipt.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download PDF receipt",
            description = "Returns a branded PDF receipt for the given transaction")
    public ResponseEntity<byte[]> getReceipt(@PathVariable Long id) {
        byte[] pdf = transactionReceiptService.generatePdfForTransaction(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition
                        .inline()
                        .filename("receipt-" + id + ".pdf")
                        .build());
        headers.setContentLength(pdf.length);
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @PostMapping
    @PreAuthorize("hasPermission(null, 'transaction:create')")
    @Operation(summary = "Create a new transaction", description = "Creates a new remittance transaction from a validated quote")
    public ResponseEntity<ApiResponse<TransactionResponse>> createTransaction(
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(value = "X-User-Email", required = false) String userEmail,
            @RequestHeader(value = "X-User-UUID", required = false) String userUuid,
            @RequestHeader(value = "X-Visitor-Id", required = false) String visitorId,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        TransactionResponse response = transactionService.createTransaction(
                request, userId, userEmail, userUuid, visitorId, forwardedFor);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<TransactionResponse>builder()
                        .success(true)
                        .data(response)
                        .message("Transaction created successfully")
                        .build());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by ID", description = "Retrieves transaction details by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(@PathVariable Long id) {
        TransactionResponse response = transactionService.getTransaction(id);
        return ResponseEntity.ok(ApiResponse.<TransactionResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "Lists transactions with optional filters for the authenticated user")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> listTransactions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(required = false) Long corridorId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size,
            @RequestParam(required = false, defaultValue = "createdAt") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(required = false) Long filterUserId,
            Authentication authentication) {

        // If user has view_all permission, don't filter by userId (unless filterUserId is specified)
        boolean canViewAll = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("transaction:view_all"));
        Long userId = canViewAll ? filterUserId : extractUserId(authentication);

        TransactionListRequest request = TransactionListRequest.builder()
                .status(status != null ? com.remitz.common.enums.TransactionStatus.valueOf(status) : null)
                .startDate(startDate != null ? java.time.LocalDate.parse(startDate) : null)
                .endDate(endDate != null ? java.time.LocalDate.parse(endDate) : null)
                .corridorId(corridorId)
                .search(search)
                .page(page)
                .size(size)
                .sortBy(sortBy)
                .sortDir(sortDir)
                .build();

        Page<TransactionResponse> responses = transactionService.listTransactions(userId, request);
        return ResponseEntity.ok(ApiResponse.<Page<TransactionResponse>>builder()
                .success(true)
                .data(responses)
                .build());
    }

    @PutMapping("/{id}/cancel")
    @Operation(summary = "Cancel transaction", description = "Cancels a transaction that has not yet reached PROCESSING status")
    public ResponseEntity<ApiResponse<TransactionResponse>> cancelTransaction(
            @PathVariable Long id,
            Authentication authentication) {
        Long userId = extractUserId(authentication);
        TransactionResponse response = transactionService.cancelTransaction(id, userId);
        return ResponseEntity.ok(ApiResponse.<TransactionResponse>builder()
                .success(true)
                .data(response)
                .message("Transaction cancelled successfully")
                .build());
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasPermission(null, 'transaction:view_all')")
    @Operation(summary = "Update transaction status (Admin)", description = "Updates a transaction status. Admin-only operation.")
    public ResponseEntity<ApiResponse<TransactionResponse>> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody TransactionStatusUpdateRequest request,
            Authentication authentication) {
        Long actorId = extractUserId(authentication);
        TransactionResponse response = transactionService.updateStatus(id, request, actorId, ActorType.ADMIN);
        return ResponseEntity.ok(ApiResponse.<TransactionResponse>builder()
                .success(true)
                .data(response)
                .message("Transaction status updated successfully")
                .build());
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "Get transaction status history", description = "Returns the full status transition history for a transaction")
    public ResponseEntity<ApiResponse<List<StatusHistoryResponse>>> getStatusHistory(@PathVariable Long id) {
        List<StatusHistoryResponse> history = transactionService.getStatusHistory(id);
        return ResponseEntity.ok(ApiResponse.<List<StatusHistoryResponse>>builder()
                .success(true)
                .data(history)
                .build());
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasPermission(null, 'transaction:refund')")
    @Operation(summary = "Initiate refund", description = "Initiates a refund for a transaction. Requires transaction:refund permission.")
    public ResponseEntity<ApiResponse<TransactionResponse>> initiateRefund(
            @PathVariable Long id,
            Authentication authentication) {
        Long actorId = extractUserId(authentication);
        TransactionResponse response = transactionService.initiateRefund(id, actorId);
        return ResponseEntity.ok(ApiResponse.<TransactionResponse>builder()
                .success(true)
                .data(response)
                .message("Refund initiated successfully")
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

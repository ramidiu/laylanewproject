package com.remitz.modules.payin.transaction.controller;

import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionRequest;
import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionResponse;
import com.remitz.modules.payin.transaction.dto.PayinTransactionDto;
import com.remitz.modules.payin.transaction.service.PayinTransactionService;
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
@RequestMapping("/api/payin/transaction")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PayIn Transaction", description = "Transaction creation for PayIn partners")
public class PayinTransactionController {

    private final PayinTransactionService service;

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Create PayIn transaction", description = "Creates a transaction for a PayIn customer in a single step")
    public ResponseEntity<CreatePayinTransactionResponse> createTransaction(
            @Valid @RequestBody CreatePayinTransactionRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            String message = bindingResult.getFieldErrors().get(0).getDefaultMessage();
            log.warn("PayIn transaction validation failed: {}", message);
            return ResponseEntity.badRequest().body(CreatePayinTransactionResponse.failure(message));
        }

        return ResponseEntity.ok(service.createTransaction(request));
    }

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List PayIn transactions", description = "Returns all PayIn transactions")
    public ResponseEntity<List<PayinTransactionDto>> listTransactions() {
        return ResponseEntity.ok(service.listTransactions());
    }

    @GetMapping("/processing")
    @PreAuthorize("hasAnyRole('PAYOUT_PARTNER', 'PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List processing PayIn transactions", description = "Returns PayIn transactions with PROCESSING status — for payout partner to fulfil")
    public ResponseEntity<List<PayinTransactionDto>> listProcessing() {
        return ResponseEntity.ok(service.listProcessingTransactions());
    }

    @PutMapping("/{transactionId}/mark-paid")
    @PreAuthorize("hasAnyRole('PAYOUT_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Mark PayIn transaction as paid", description = "Payout partner confirms payment — sets status to SUCCESS")
    public ResponseEntity<PayinTransactionDto> markPaid(@PathVariable String transactionId) {
        return ResponseEntity.ok(service.markPaid(transactionId));
    }

    @GetMapping(value = "/{transactionId}/receipt.pdf", produces = org.springframework.http.MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'PAYOUT_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Download PayIn transaction receipt", description = "Branded PDF receipt for a PayIn transaction")
    public ResponseEntity<byte[]> getReceipt(@PathVariable String transactionId) {
        byte[] pdf = service.generateReceiptPdf(transactionId);
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition
                .inline().filename("receipt-" + transactionId + ".pdf").build());
        headers.setContentLength(pdf.length);
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }
}

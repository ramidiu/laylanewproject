package com.remitz.modules.remitone.controller;

import com.remitz.modules.remitone.entity.RemitOneTransactionEntity;
import com.remitz.modules.remitone.repository.RemitOneTransactionRepository;
import com.remitz.modules.remitone.service.RemitOneService;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth/admin/remitone")
@RequiredArgsConstructor
public class RemitOneAdminController {

    private final RemitOneService remitOneService;
    private final RemitOneTransactionRepository remitOneRepository;
    private final TransactionRepository transactionRepository;

    @PostMapping("/retry/{referenceNumber}")
    @PreAuthorize("hasAuthority('config:manage_system') or hasAuthority('report:view_operational')")
    public ResponseEntity<Map<String, Object>> retry(@PathVariable String referenceNumber) {
        TransactionEntity tx = transactionRepository.findByReferenceNumber(referenceNumber).orElse(null);
        if (tx == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Transaction not found: " + referenceNumber));
        }

        // Clear existing record so idempotency check allows a fresh attempt
        Optional<RemitOneTransactionEntity> existing = remitOneRepository.findByTransactionId(referenceNumber);
        existing.ifPresent(remitOneRepository::delete);

        remitOneService.triggerCompliance(tx.getId());

        return ResponseEntity.ok(Map.of("success", true, "message", "RemitOne compliance triggered for " + referenceNumber + " — check remit_one_transactions in a moment"));
    }

    @GetMapping("/status/{referenceNumber}")
    @PreAuthorize("hasAuthority('config:manage_system') or hasAuthority('report:view_operational')")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String referenceNumber) {
        Optional<RemitOneTransactionEntity> record = remitOneRepository.findByTransactionId(referenceNumber);
        if (record.isEmpty()) {
            return ResponseEntity.ok(Map.of("found", false, "message", "No RemitOne record for " + referenceNumber));
        }
        RemitOneTransactionEntity r = record.get();
        return ResponseEntity.ok(Map.of(
                "found", true,
                "transactionId", r.getTransactionId(),
                "paymentStatus", r.getPaymentStatus() != null ? r.getPaymentStatus() : "",
                "message", r.getMessage() != null ? r.getMessage() : "",
                "transSessionId", r.getTransSessionId() != null ? r.getTransSessionId() : "",
                "createdOn", r.getCreatedOn() != null ? r.getCreatedOn().toString() : ""
        ));
    }
}

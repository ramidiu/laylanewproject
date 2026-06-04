package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.TransactionLimit;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.user.repository.TransactionLimitRepository;
import com.remitz.modules.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/transaction-limits")
@RequiredArgsConstructor
@Tag(name = "Transaction Limits", description = "APIs for transaction limit queries based on user risk level")
public class TransactionLimitController {

    private final TransactionLimitRepository transactionLimitRepository;
    private final UserRepository userRepository;

    @Operation(summary = "Get transaction limits", description = "Get transaction limits for the current user's risk level")
    @GetMapping
    public ResponseEntity<ApiResponse<List<TransactionLimit>>> getTransactionLimits(
            Authentication authentication) {
        String userUuid = authentication.getName();
        UserEntity user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new ResourceNotFoundException("User", "uuid", userUuid));

        String riskLevel = user.getRiskScore() != null ? user.getRiskScore() : "MEDIUM";
        List<TransactionLimit> limits = transactionLimitRepository.findByRiskLevel(riskLevel);

        return ResponseEntity.ok(ApiResponse.<List<TransactionLimit>>builder()
                .success(true)
                .data(limits)
                .message("Transaction limits retrieved successfully")
                .build());
    }
}

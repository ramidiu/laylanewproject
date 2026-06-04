package com.remitz.modules.payin.transaction.controller;

import com.remitz.modules.payin.transaction.entity.PayinBeneficiaryEntity;
import com.remitz.modules.payin.transaction.repository.PayinBeneficiaryRepository;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/payin/beneficiary")
@RequiredArgsConstructor
@Tag(name = "PayIn Beneficiary", description = "Beneficiary lookup for PayIn partners")
public class PayinBeneficiaryController {

    private final PayinBeneficiaryRepository repository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;

    @GetMapping("/list/{customerId}")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List beneficiaries for a customer")
    public ResponseEntity<List<Map<String, Object>>> listForCustomer(@PathVariable String customerId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // PayIn-specific beneficiaries
        repository.findByCustomerId(customerId).forEach(b -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", "p_" + b.getId());
            m.put("name", b.getName());
            m.put("bankName", b.getBankName());
            m.put("accountNumber", b.getAccountNumber());
            m.put("ifscCode", b.getIfscCode() != null ? b.getIfscCode() : "");
            // Delivery method: from the linked regular beneficiary, else inferred from fields.
            String dm = null;
            if (b.getLinkedRegularBeneficiaryId() != null) {
                dm = beneficiaryRepository.findById(b.getLinkedRegularBeneficiaryId())
                        .map(r -> r.getDeliveryMethod() != null ? r.getDeliveryMethod().name() : null)
                        .orElse(null);
            }
            if (dm == null) {
                dm = (b.getBankName() != null && !b.getBankName().isBlank()) ? "BANK_DEPOSIT" : "CASH_PICKUP";
            }
            m.put("deliveryMethod", dm);
            result.add(m);
        });

        // Regular beneficiaries from the customer's account
        userRepository.findByUuid(customerId).ifPresent(user -> {
            List<BeneficiaryEntity> regular = beneficiaryRepository.findByUserIdAndIsBlockedFalse(user.getId());
            regular.forEach(b -> {
                String accountNum = b.getAccountNumber() != null ? b.getAccountNumber()
                        : (b.getIban() != null ? b.getIban()
                        : (b.getMobileNumber() != null ? b.getMobileNumber() : ""));
                String bankOrProvider = b.getBankName() != null ? b.getBankName()
                        : (b.getMobileProvider() != null ? b.getMobileProvider() : "");
                Map<String, Object> m = new HashMap<>();
                m.put("id", "r_" + b.getId());
                m.put("name", b.getFullName());
                m.put("bankName", bankOrProvider);
                m.put("accountNumber", accountNum);
                m.put("ifscCode", b.getSwiftBic() != null ? b.getSwiftBic()
                        : (b.getSortCode() != null ? b.getSortCode() : ""));
                m.put("deliveryMethod", b.getDeliveryMethod() != null ? b.getDeliveryMethod().name() : null);
                result.add(m);
            });
        });

        return ResponseEntity.ok(result);
    }
}

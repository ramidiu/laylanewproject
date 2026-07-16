package com.remitz.modules.payin.transaction.controller;

import com.remitz.common.dto.UpdateBeneficiaryRequest;
import com.remitz.modules.payin.transaction.entity.PayinBeneficiaryEntity;
import com.remitz.modules.payin.transaction.repository.PayinBeneficiaryRepository;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.service.BeneficiaryService;
import com.remitz.modules.auth.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/payin/beneficiary")
@RequiredArgsConstructor
@Tag(name = "PayIn Beneficiary", description = "Beneficiary lookup + edit for PayIn partners")
public class PayinBeneficiaryController {

    private final PayinBeneficiaryRepository repository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final BeneficiaryService beneficiaryService;

    @GetMapping("/list/{customerId}")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "List beneficiaries for a customer")
    public ResponseEntity<List<Map<String, Object>>> listForCustomer(@PathVariable String customerId) {
        List<Map<String, Object>> result = new ArrayList<>();

        // PayIn-specific beneficiaries — enrich from the linked regular beneficiary (the USI mirror).
        repository.findByCustomerId(customerId).forEach(b -> {
            BeneficiaryEntity reg = b.getLinkedRegularBeneficiaryId() != null
                    ? beneficiaryRepository.findById(b.getLinkedRegularBeneficiaryId()).orElse(null)
                    : null;
            Map<String, Object> m = new HashMap<>();
            m.put("id", "p_" + b.getId());
            m.put("name", b.getName());
            m.put("bankName", b.getBankName());
            m.put("accountNumber", b.getAccountNumber());
            m.put("ifscCode", b.getIfscCode() != null ? b.getIfscCode() : "");
            String dm = reg != null && reg.getDeliveryMethod() != null ? reg.getDeliveryMethod().name() : null;
            if (dm == null) {
                dm = (b.getBankName() != null && !b.getBankName().isBlank()) ? "BANK_DEPOSIT" : "CASH_PICKUP";
            }
            m.put("deliveryMethod", dm);
            enrichFromRegular(m, reg);
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
                enrichFromRegular(m, b);
                result.add(m);
            });
        });

        return ResponseEntity.ok(result);
    }

    /** Add the full editable field set (for the edit form pre-fill) from the regular beneficiary. */
    private void enrichFromRegular(Map<String, Object> m, BeneficiaryEntity reg) {
        m.put("address",        reg != null ? reg.getAddress() : null);
        m.put("city",           reg != null ? reg.getCity() : null);
        m.put("branchState",    reg != null ? reg.getBranchState() : null);
        m.put("branchCity",     reg != null ? reg.getBranchCity() : null);
        m.put("rawAccountNumber", reg != null ? reg.getAccountNumber() : null);
        m.put("iban",           reg != null ? reg.getIban() : null);
        m.put("sortCode",       reg != null ? reg.getSortCode() : null);
        m.put("swiftBic",       reg != null ? reg.getSwiftBic() : null);
        m.put("mobileNumber",   reg != null ? reg.getMobileNumber() : null);
        m.put("mobileProvider", reg != null ? reg.getMobileProvider() : null);
        m.put("country",        reg != null ? reg.getCountry() : null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PAYIN_PARTNER', 'ADMIN', 'SUPER_ADMIN')")
    @Operation(summary = "Update a beneficiary and push the change to USI Money",
            description = "Accepts a p_<id> (pay-in) or r_<id> (regular) beneficiary id. Updates the regular "
                    + "mirror (which carries the USI fields) and re-syncs it to USI Money via updateBeneficiary.")
    public ResponseEntity<Map<String, Object>> update(@PathVariable String id,
                                                      @RequestBody UpdateBeneficiaryRequest req) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean isPayin = id.startsWith("p_");
        boolean isRegular = id.startsWith("r_");
        Long nid = Long.parseLong(isPayin || isRegular ? id.substring(2) : id);

        Long regularId;
        if (isPayin) {
            PayinBeneficiaryEntity pb = repository.findById(nid)
                    .orElseThrow(() -> new RuntimeException("Pay-in beneficiary not found: " + nid));
            // Keep the lightweight pay-in mirror in step so the partner's list shows the new values.
            if (req.getFullName() != null) pb.setName(req.getFullName());
            if (req.getBankName() != null) pb.setBankName(req.getBankName());
            String acct = req.getAccountNumber() != null ? req.getAccountNumber()
                    : (req.getIban() != null ? req.getIban() : null);
            if (acct != null) pb.setAccountNumber(acct);
            repository.save(pb);
            regularId = pb.getLinkedRegularBeneficiaryId();
        } else {
            regularId = nid;
        }

        if (regularId != null) {
            // Updates the regular (USI-mirror) beneficiary and fires the USI updateBeneficiary sync.
            beneficiaryService.adminUpdateBeneficiary(regularId, req);
            out.put("usiSynced", true);
        } else {
            out.put("usiSynced", false);
            out.put("message", "Updated. This beneficiary has no USI-linked record yet; it will be pushed at transaction time.");
        }
        out.put("success", true);
        out.put("id", id);
        return ResponseEntity.ok(out);
    }
}

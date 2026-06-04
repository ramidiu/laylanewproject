package com.remitz.modules.transaction.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.BankDatabase;
import com.remitz.modules.transaction.entity.CountryBankConfig;
import com.remitz.modules.transaction.repository.BankDatabaseRepository;
import com.remitz.modules.transaction.repository.CountryBankConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transactions/banks")
@RequiredArgsConstructor
@Tag(name = "Bank Database", description = "Bank database lookup and management")
public class BankDatabaseController {

    private final BankDatabaseRepository bankDatabaseRepository;
    private final CountryBankConfigRepository countryBankConfigRepository;

    @GetMapping("/config/{countryCode}")
    @Operation(summary = "Get country bank config")
    public ResponseEntity<ApiResponse<CountryBankConfig>> getConfig(@PathVariable String countryCode) {
        CountryBankConfig config = countryBankConfigRepository.findByCountryCode(countryCode)
                .orElseThrow(() -> new ResourceNotFoundException("CountryBankConfig", "countryCode", countryCode));
        return ResponseEntity.ok(ApiResponse.<CountryBankConfig>builder()
                .success(true)
                .data(config)
                .build());
    }

    @GetMapping("/config/currency/{currency}")
    @Operation(summary = "Get config by currency")
    public ResponseEntity<ApiResponse<CountryBankConfig>> getConfigByCurrency(@PathVariable String currency) {
        CountryBankConfig config = countryBankConfigRepository.findByCurrency(currency)
                .orElseThrow(() -> new ResourceNotFoundException("CountryBankConfig", "currency", currency));
        return ResponseEntity.ok(ApiResponse.<CountryBankConfig>builder()
                .success(true)
                .data(config)
                .build());
    }

    @GetMapping("/lookup")
    @Operation(summary = "Exact bank match by identifier and country")
    public ResponseEntity<ApiResponse<BankDatabase>> lookupBank(
            @RequestParam String identifier, @RequestParam String country) {
        BankDatabase bank = bankDatabaseRepository.findByCountryCodeAndBankIdentifier(country, identifier)
                .orElseThrow(() -> new ResourceNotFoundException("Bank", "identifier", identifier));
        return ResponseEntity.ok(ApiResponse.<BankDatabase>builder()
                .success(true)
                .data(bank)
                .build());
    }

    @GetMapping("/search-identifier")
    @Operation(summary = "Auto-complete bank by identifier prefix")
    public ResponseEntity<ApiResponse<List<BankDatabase>>> searchByIdentifier(
            @RequestParam String prefix, @RequestParam String country) {
        List<BankDatabase> banks = bankDatabaseRepository
                .findByCountryCodeAndBankIdentifierStartingWith(country, prefix);
        // Limit results to 10
        if (banks.size() > 10) {
            banks = banks.subList(0, 10);
        }
        return ResponseEntity.ok(ApiResponse.<List<BankDatabase>>builder()
                .success(true)
                .data(banks)
                .build());
    }

    @GetMapping("/search")
    @Operation(summary = "Search bank by name")
    public ResponseEntity<ApiResponse<List<BankDatabase>>> searchByName(
            @RequestParam String name, @RequestParam String country) {
        List<BankDatabase> banks = bankDatabaseRepository
                .findByCountryCodeAndBankNameContainingIgnoreCase(country, name);
        // Limit results to 20
        if (banks.size() > 20) {
            banks = banks.subList(0, 20);
        }
        return ResponseEntity.ok(ApiResponse.<List<BankDatabase>>builder()
                .success(true)
                .data(banks)
                .build());
    }

    @GetMapping("/list/{countryCode}")
    @Operation(summary = "Get distinct bank names for a country")
    public ResponseEntity<ApiResponse<List<String>>> listBankNames(@PathVariable String countryCode) {
        List<String> bankNames = bankDatabaseRepository.findDistinctBankNameByCountryCode(countryCode);
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .success(true)
                .data(bankNames)
                .build());
    }

    @GetMapping("/admin/all")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Get all configs and banks (admin)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAllAdminData() {
        Map<String, Object> data = new HashMap<>();
        data.put("configs", countryBankConfigRepository.findAll());
        data.put("banks", bankDatabaseRepository.findAll());
        return ResponseEntity.ok(ApiResponse.<Map<String, Object>>builder()
                .success(true)
                .data(data)
                .build());
    }

    @PostMapping("/admin/add")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Add a bank entry")
    public ResponseEntity<ApiResponse<BankDatabase>> addBank(@RequestBody BankDatabase bank) {
        BankDatabase saved = bankDatabaseRepository.save(bank);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<BankDatabase>builder()
                        .success(true)
                        .data(saved)
                        .message("Bank added successfully")
                        .build());
    }

    @PutMapping("/admin/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Update a bank entry")
    public ResponseEntity<ApiResponse<BankDatabase>> updateBank(@PathVariable Long id,
                                                                 @RequestBody BankDatabase request) {
        BankDatabase bank = bankDatabaseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BankDatabase", "id", id));

        if (request.getBankName() != null) bank.setBankName(request.getBankName());
        if (request.getBankIdentifier() != null) bank.setBankIdentifier(request.getBankIdentifier());
        if (request.getBankAddress() != null) bank.setBankAddress(request.getBankAddress());
        if (request.getBranchName() != null) bank.setBranchName(request.getBranchName());
        if (request.getCity() != null) bank.setCity(request.getCity());
        if (request.getIsActive() != null) bank.setIsActive(request.getIsActive());

        BankDatabase saved = bankDatabaseRepository.save(bank);
        return ResponseEntity.ok(ApiResponse.<BankDatabase>builder()
                .success(true)
                .data(saved)
                .message("Bank updated successfully")
                .build());
    }

    @PutMapping("/admin/config/{countryCode}")
    @PreAuthorize("hasPermission(null, 'config:manage_transfer')")
    @Operation(summary = "Update country bank config")
    public ResponseEntity<ApiResponse<CountryBankConfig>> updateConfig(
            @PathVariable String countryCode, @RequestBody CountryBankConfig request) {
        CountryBankConfig config = countryBankConfigRepository.findByCountryCode(countryCode)
                .orElseThrow(() -> new ResourceNotFoundException("CountryBankConfig", "countryCode", countryCode));

        if (request.getCountryName() != null) config.setCountryName(request.getCountryName());
        if (request.getCurrency() != null) config.setCurrency(request.getCurrency());
        if (request.getIdentifierName() != null) config.setIdentifierName(request.getIdentifierName());
        if (request.getIdentifierLabel() != null) config.setIdentifierLabel(request.getIdentifierLabel());
        if (request.getIdentifierFormat() != null) config.setIdentifierFormat(request.getIdentifierFormat());
        if (request.getIdentifierLength() != null) config.setIdentifierLength(request.getIdentifierLength());
        if (request.getAutoLookup() != null) config.setAutoLookup(request.getAutoLookup());
        if (request.getIsActive() != null) config.setIsActive(request.getIsActive());

        CountryBankConfig saved = countryBankConfigRepository.save(config);
        return ResponseEntity.ok(ApiResponse.<CountryBankConfig>builder()
                .success(true)
                .data(saved)
                .message("Country bank config updated successfully")
                .build());
    }
}

package com.remitz.modules.user.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.user.entity.KycDocumentTypeConfig;
import com.remitz.modules.user.entity.Relation;
import com.remitz.modules.user.repository.KycDocumentTypeConfigRepository;
import com.remitz.modules.user.repository.RelationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController("userLookupController")
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Lookup Data", description = "APIs for lookup data - relations, KYC document types")
public class LookupController {

    private final RelationRepository relationRepository;
    private final KycDocumentTypeConfigRepository kycDocumentTypeConfigRepository;

    // ==================== KYC Document Types ====================

    @Operation(summary = "Get KYC document types", description = "Get active KYC document type configurations by country and category")
    @GetMapping("/kyc/document-types")
    public ResponseEntity<ApiResponse<List<KycDocumentTypeConfig>>> getDocumentTypes(
            @Parameter(description = "Country code (e.g., GB, IN)") @RequestParam(required = false) String country,
            @Parameter(description = "Document category (e.g., IDENTITY, ADDRESS)") @RequestParam String category) {

        List<KycDocumentTypeConfig> docs;

        if (country != null && !country.isBlank()) {
            docs = kycDocumentTypeConfigRepository
                    .findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder(country, category, true);
            // If country not found, fall back to universal docs
            if (docs.isEmpty()) {
                docs = kycDocumentTypeConfigRepository
                        .findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder("ALL", category, true);
            }
        } else {
            docs = kycDocumentTypeConfigRepository
                    .findByCategoryAndIsActiveOrderByDisplayOrder(category, true);
        }

        return ResponseEntity.ok(ApiResponse.<List<KycDocumentTypeConfig>>builder()
                .success(true)
                .data(docs)
                .message("Document types retrieved successfully")
                .build());
    }

    // ==================== Relations ====================

    @Operation(summary = "List active relations", description = "List active beneficiary relation types (customer)")
    @GetMapping("/lookup/relations")
    public ResponseEntity<ApiResponse<List<Relation>>> getActiveRelations() {
        List<Relation> relations = relationRepository.findByIsActive(true);
        return ResponseEntity.ok(ApiResponse.<List<Relation>>builder()
                .success(true)
                .data(relations)
                .message("Relations retrieved successfully")
                .build());
    }

    @Operation(summary = "List all relations", description = "List all beneficiary relation types (admin)")
    @GetMapping("/lookup/admin/relations")
    @PreAuthorize("hasPermission(null, 'user:view')")
    public ResponseEntity<ApiResponse<List<Relation>>> getAllRelations() {
        List<Relation> relations = relationRepository.findAll();
        return ResponseEntity.ok(ApiResponse.<List<Relation>>builder()
                .success(true)
                .data(relations)
                .message("All relations retrieved successfully")
                .build());
    }

    @Operation(summary = "Add relation", description = "Add a new beneficiary relation type (super admin)")
    @PostMapping("/lookup/admin/relations")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    public ResponseEntity<ApiResponse<Relation>> addRelation(@RequestBody Map<String, String> body) {
        String relationName = body.get("relationName");
        if (relationName == null || relationName.isBlank()) {
            throw new RemitzException("relationName is required", HttpStatus.BAD_REQUEST);
        }

        Relation relation = Relation.builder()
                .relationName(relationName)
                .isActive(true)
                .build();
        Relation saved = relationRepository.save(relation);

        return ResponseEntity.ok(ApiResponse.<Relation>builder()
                .success(true)
                .data(saved)
                .message("Relation added successfully")
                .build());
    }

    @Operation(summary = "Edit relation", description = "Edit a beneficiary relation type (super admin)")
    @PutMapping("/lookup/admin/relations/{id}")
    @PreAuthorize("hasPermission(null, 'config:manage_system')")
    public ResponseEntity<ApiResponse<Relation>> editRelation(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        Relation relation = relationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Relation", "id", id));

        if (body.containsKey("relationName")) {
            relation.setRelationName((String) body.get("relationName"));
        }
        if (body.containsKey("isActive")) {
            relation.setIsActive((Boolean) body.get("isActive"));
        }

        Relation saved = relationRepository.save(relation);

        return ResponseEntity.ok(ApiResponse.<Relation>builder()
                .success(true)
                .data(saved)
                .message("Relation updated successfully")
                .build());
    }
}

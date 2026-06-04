package com.remitz.common.dto;

import com.remitz.common.enums.KycDocumentStatus;
import com.remitz.common.enums.KycDocumentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycDocumentResponse {

    private Long id;
    private KycDocumentType documentType;
    private String documentNumber;
    private String filePath;
    private KycDocumentStatus status;
    private String verifiedBy;
    private LocalDateTime verifiedAt;
    private String rejectionReason;
    private LocalDate expiryDate;
    private LocalDate issueDate;
    private String fileName;
    private String fileUrl;
    private LocalDateTime createdAt;
}

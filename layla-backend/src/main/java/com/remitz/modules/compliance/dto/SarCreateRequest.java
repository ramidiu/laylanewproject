package com.remitz.modules.compliance.dto;

import com.remitz.common.enums.SarReportType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SarCreateRequest {

    @NotNull(message = "Report type is required")
    private SarReportType reportType;

    @NotNull(message = "Filed by is required")
    private Long filedBy;

    private String reportContent;
}

package com.remitz.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplatePreviewResponse {

    private Long templateId;
    private String templateCode;
    private String renderedSubject;
    private String renderedBody;
}

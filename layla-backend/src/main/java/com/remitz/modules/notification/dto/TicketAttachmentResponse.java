package com.remitz.modules.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketAttachmentResponse {

    private Long id;
    private String fileName;
    private Long fileSize;
    private String contentType;
    private String url;
    private LocalDateTime uploadedAt;
}

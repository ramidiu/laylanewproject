package com.remitz.modules.notification.dto;

import com.remitz.modules.notification.entity.SupportTicketEntity.SenderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketMessageResponse {

    private Long id;
    private SenderType senderType;
    private String senderId;
    private String senderName;
    private String message;
    private LocalDateTime createdAt;
    private List<TicketAttachmentResponse> attachments;
}

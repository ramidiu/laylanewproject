package com.remitz.modules.notification.dto;

import com.remitz.modules.notification.entity.SupportTicketEntity.IssueType;
import com.remitz.modules.notification.entity.SupportTicketEntity.Priority;
import com.remitz.modules.notification.entity.SupportTicketEntity.TicketStatus;
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
public class TicketResponse {

    private Long id;
    private String ticketNumber;
    private String userId;
    private String userEmail;
    private String userName;
    private String subject;
    private IssueType issueType;
    private Priority priority;
    private TicketStatus status;
    private Long assignedTo;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
    private String latestMessage;
    private long messageCount;
    private List<TicketMessageResponse> messages;
    private List<TicketAttachmentResponse> attachments;
}

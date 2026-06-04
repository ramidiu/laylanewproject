package com.remitz.modules.notification.dto;

import com.remitz.modules.notification.entity.SupportTicketEntity.IssueType;
import com.remitz.modules.notification.entity.SupportTicketEntity.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTicketRequest {

    @NotBlank(message = "Subject is required")
    private String subject;

    @NotNull(message = "Issue type is required")
    private IssueType issueType;

    private Priority priority;

    @NotBlank(message = "Message is required")
    private String message;
}

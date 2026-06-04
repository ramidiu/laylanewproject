package com.remitz.modules.notification.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.modules.notification.dto.*;
import com.remitz.modules.notification.entity.SupportTicketAttachmentEntity;
import com.remitz.modules.notification.service.SupportTicketService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.List;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Support Tickets", description = "Customer support ticket management APIs")
public class SupportTicketController {

    private final SupportTicketService supportTicketService;

    // ==================== Customer Endpoints ====================

    @PostMapping("/tickets")
    @Operation(summary = "Create ticket", description = "Create a new support ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> createTicket(
            HttpServletRequest request,
            @Valid @ModelAttribute CreateTicketRequest ticketRequest,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        String userId = extractUserUuid(request);
        String email = request.getHeader("X-User-Email");
        String name = request.getHeader("X-User-Name");

        TicketResponse response = supportTicketService.createTicket(userId, email, name, ticketRequest, files);
        return ResponseEntity.ok(ApiResponse.<TicketResponse>builder()
                .success(true)
                .data(response)
                .message("Support ticket created successfully")
                .build());
    }

    @GetMapping("/tickets")
    @Operation(summary = "Get my tickets", description = "Get all support tickets for the authenticated user")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getMyTickets(HttpServletRequest request) {
        String userId = extractUserUuid(request);
        List<TicketResponse> tickets = supportTicketService.getMyTickets(userId);
        return ResponseEntity.ok(ApiResponse.<List<TicketResponse>>builder()
                .success(true)
                .data(tickets)
                .build());
    }

    @GetMapping("/tickets/{ticketId}")
    @Operation(summary = "Get ticket detail", description = "Get a specific support ticket with all messages")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketDetail(
            HttpServletRequest request,
            @PathVariable Long ticketId) {

        String userId = extractUserUuid(request);
        TicketResponse response = supportTicketService.getTicketDetail(ticketId, userId);
        return ResponseEntity.ok(ApiResponse.<TicketResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/tickets/{ticketId}/reply")
    @Operation(summary = "Reply to ticket", description = "Customer reply to a support ticket")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> replyToTicket(
            HttpServletRequest request,
            @PathVariable Long ticketId,
            @Valid @ModelAttribute TicketReplyRequest replyRequest,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        String userId = extractUserUuid(request);
        String name = request.getHeader("X-User-Name");

        TicketMessageResponse response = supportTicketService.replyToTicket(
                ticketId, userId, name, "CUSTOMER", replyRequest.getMessage(), files);
        return ResponseEntity.ok(ApiResponse.<TicketMessageResponse>builder()
                .success(true)
                .data(response)
                .message("Reply sent successfully")
                .build());
    }

    // ==================== Admin Endpoints ====================

    @GetMapping("/admin/tickets")
    @Operation(summary = "Get all tickets (admin)", description = "Admin: list all support tickets with optional filters")
    public ResponseEntity<ApiResponse<List<TicketResponse>>> getAllTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority) {

        List<TicketResponse> tickets = supportTicketService.getAllTickets(status, priority);
        return ResponseEntity.ok(ApiResponse.<List<TicketResponse>>builder()
                .success(true)
                .data(tickets)
                .build());
    }

    @GetMapping("/admin/tickets/{ticketId}")
    @Operation(summary = "Get ticket detail (admin)", description = "Admin: view any support ticket with all messages")
    public ResponseEntity<ApiResponse<TicketResponse>> getTicketDetailAdmin(@PathVariable Long ticketId) {
        TicketResponse response = supportTicketService.getTicketDetailAdmin(ticketId);
        return ResponseEntity.ok(ApiResponse.<TicketResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @PostMapping("/admin/tickets/{ticketId}/reply")
    @Operation(summary = "Admin reply to ticket", description = "Admin: reply to a support ticket as an agent")
    public ResponseEntity<ApiResponse<TicketMessageResponse>> adminReplyToTicket(
            HttpServletRequest request,
            @PathVariable Long ticketId,
            @Valid @ModelAttribute TicketReplyRequest replyRequest,
            @RequestParam(value = "files", required = false) List<MultipartFile> files) {

        String agentId = extractUserUuid(request);
        String agentName = request.getHeader("X-User-Name");
        if (agentName == null || agentName.isBlank()) {
            agentName = "Support Agent";
        }

        TicketMessageResponse response = supportTicketService.replyToTicket(
                ticketId, agentId, agentName, "AGENT", replyRequest.getMessage(), files);
        return ResponseEntity.ok(ApiResponse.<TicketMessageResponse>builder()
                .success(true)
                .data(response)
                .message("Admin reply sent successfully")
                .build());
    }

    @PutMapping("/admin/tickets/{ticketId}/status")
    @Operation(summary = "Update ticket status (admin)", description = "Admin: update ticket status and assignment")
    public ResponseEntity<ApiResponse<TicketResponse>> updateTicketStatus(
            @PathVariable Long ticketId,
            @Valid @RequestBody UpdateTicketStatusRequest statusRequest) {

        TicketResponse response = supportTicketService.updateTicketStatus(ticketId, statusRequest);
        return ResponseEntity.ok(ApiResponse.<TicketResponse>builder()
                .success(true)
                .data(response)
                .message("Ticket status updated successfully")
                .build());
    }

    // ==================== File Serving ====================

    @GetMapping("/attachments/{attachmentId}/file")
    @Operation(summary = "Get attachment file", description = "Download a support ticket attachment")
    public ResponseEntity<Resource> getAttachmentFile(@PathVariable Long attachmentId) {
        SupportTicketAttachmentEntity attachment = supportTicketService.getAttachment(attachmentId);
        File file = new File(attachment.getFilePath());

        if (!file.exists()) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + attachment.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        attachment.getContentType() != null ? attachment.getContentType() : "application/octet-stream"))
                .body(resource);
    }

    // ==================== Helper ====================

    private String extractUserUuid(HttpServletRequest request) {
        String userUuid = request.getHeader("X-User-UUID");
        if (userUuid != null && !userUuid.isBlank()) {
            return userUuid;
        }
        throw new RuntimeException("User UUID not found in request headers");
    }
}

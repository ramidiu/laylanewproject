package com.remitz.modules.notification.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.dto.PageResponse;
import com.remitz.modules.notification.dto.AdminInAppNotificationRow;
import com.remitz.modules.notification.dto.AdminSendNotificationRequest;
import com.remitz.modules.notification.dto.AdminSendNotificationResponse;
import com.remitz.modules.notification.dto.NotificationLogResponse;
import com.remitz.modules.notification.dto.NotificationTemplateCreateRequest;
import com.remitz.modules.notification.dto.NotificationTemplateResponse;
import com.remitz.modules.notification.dto.NotificationTemplateUpdateRequest;
import com.remitz.modules.notification.dto.TemplatePreviewRequest;
import com.remitz.modules.notification.dto.TemplatePreviewResponse;
import com.remitz.modules.notification.entity.NotificationLogEntity;
import com.remitz.modules.notification.repository.NotificationLogRepository;
import com.remitz.modules.notification.service.InAppNotificationService;
import com.remitz.modules.notification.service.TemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications", description = "Admin notification management APIs")
public class AdminNotificationController {

    private final TemplateService templateService;
    private final NotificationLogRepository notificationLogRepository;
    private final InAppNotificationService inAppNotificationService;

    @PostMapping("/send")
    @Operation(summary = "Send notification", description = "Send an in-app notification to specific users or broadcast to all active users")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<AdminSendNotificationResponse>> sendNotification(
            @Valid @RequestBody AdminSendNotificationRequest request) {

        AdminSendNotificationRequest.TargetType target =
                request.getTargetType() != null ? request.getTargetType() : AdminSendNotificationRequest.TargetType.USERS;

        int delivered;
        if (target == AdminSendNotificationRequest.TargetType.ALL) {
            delivered = inAppNotificationService.broadcastToAllUsers(
                    request.getTitle(), request.getMessage(), request.getType());
        } else {
            List<Long> userIds = request.getUserIds();
            if (userIds == null || userIds.isEmpty()) {
                return ResponseEntity.badRequest().body(ApiResponse.<AdminSendNotificationResponse>builder()
                        .success(false)
                        .message("userIds is required when targetType=USERS")
                        .build());
            }
            delivered = inAppNotificationService.sendToUsers(
                    userIds, request.getTitle(), request.getMessage(), request.getType());
        }

        return ResponseEntity.ok(ApiResponse.<AdminSendNotificationResponse>builder()
                .success(true)
                .data(AdminSendNotificationResponse.builder()
                        .delivered(delivered)
                        .targetType(target.name())
                        .build())
                .message("Notification sent to " + delivered + " user(s)")
                .build());
    }

    @GetMapping("/templates")
    @Operation(summary = "List templates", description = "List all active notification templates")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<List<NotificationTemplateResponse>>> listTemplates() {
        List<NotificationTemplateResponse> templates = templateService.listTemplates();
        return ResponseEntity.ok(ApiResponse.<List<NotificationTemplateResponse>>builder()
                .success(true)
                .data(templates)
                .build());
    }

    @PostMapping("/templates")
    @Operation(summary = "Create template", description = "Create a new notification template")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<NotificationTemplateResponse>> createTemplate(
            @Valid @RequestBody NotificationTemplateCreateRequest request) {

        NotificationTemplateResponse response = templateService.createTemplate(request);
        return ResponseEntity.ok(ApiResponse.<NotificationTemplateResponse>builder()
                .success(true)
                .data(response)
                .message("Template created successfully")
                .build());
    }

    @PutMapping("/templates/{id}")
    @Operation(summary = "Update template", description = "Update a notification template")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<NotificationTemplateResponse>> updateTemplate(
            @PathVariable Long id,
            @RequestBody NotificationTemplateUpdateRequest request) {

        NotificationTemplateResponse response = templateService.updateTemplate(id, request);
        return ResponseEntity.ok(ApiResponse.<NotificationTemplateResponse>builder()
                .success(true)
                .data(response)
                .message("Template updated successfully")
                .build());
    }

    @PostMapping("/templates/preview")
    @Operation(summary = "Preview template", description = "Preview a notification template with sample data")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<TemplatePreviewResponse>> previewTemplate(
            @RequestBody TemplatePreviewRequest request) {

        TemplatePreviewResponse response = templateService.previewTemplate(request);
        return ResponseEntity.ok(ApiResponse.<TemplatePreviewResponse>builder()
                .success(true)
                .data(response)
                .build());
    }

    @GetMapping("/in-app")
    @Operation(summary = "Recent in-app notifications", description = "Paginated recent in-app notifications across all users")
    @PreAuthorize("hasPermission(null, 'config:manage_corridors')")
    public ResponseEntity<ApiResponse<PageResponse<AdminInAppNotificationRow>>> getRecentInAppNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<AdminInAppNotificationRow> result = inAppNotificationService.getRecentNotifications(pageable);

        PageResponse<AdminInAppNotificationRow> pageResponse = PageResponse.<AdminInAppNotificationRow>builder()
                .content(result.getContent())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.<PageResponse<AdminInAppNotificationRow>>builder()
                .success(true)
                .data(pageResponse)
                .build());
    }

    @GetMapping("/log")
    @Operation(summary = "Get notification log", description = "Get paginated notification delivery log")
    @PreAuthorize("hasPermission(null, 'report:view_operational')")
    public ResponseEntity<ApiResponse<PageResponse<NotificationLogResponse>>> getNotificationLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationLogEntity> logPage = notificationLogRepository.findAllByOrderByCreatedAtDesc(pageable);

        Page<NotificationLogResponse> responsePage = logPage.map(this::toLogResponse);

        PageResponse<NotificationLogResponse> pageResponse = PageResponse.<NotificationLogResponse>builder()
                .content(responsePage.getContent())
                .page(responsePage.getNumber())
                .size(responsePage.getSize())
                .totalElements(responsePage.getTotalElements())
                .totalPages(responsePage.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.<PageResponse<NotificationLogResponse>>builder()
                .success(true)
                .data(pageResponse)
                .build());
    }

    private NotificationLogResponse toLogResponse(NotificationLogEntity entity) {
        return NotificationLogResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .transactionId(entity.getTransactionId())
                .templateCode(entity.getTemplateCode())
                .channel(entity.getChannel())
                .recipient(entity.getRecipient())
                .status(entity.getStatus())
                .retryCount(entity.getRetryCount())
                .sentAt(entity.getSentAt())
                .deliveredAt(entity.getDeliveredAt())
                .errorMessage(entity.getErrorMessage())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

package com.remitz.modules.notification.controller;

import com.remitz.common.dto.ApiResponse;
import com.remitz.common.dto.DeviceRegistrationRequest;
import com.remitz.common.dto.InAppNotificationResponse;
import com.remitz.common.dto.PageResponse;
import com.remitz.security.JwtService;
import com.remitz.modules.notification.dto.DeviceResponse;
import com.remitz.modules.notification.service.DeviceService;
import com.remitz.modules.notification.service.InAppNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management APIs")
public class NotificationController {

    private final InAppNotificationService inAppNotificationService;
    private final DeviceService deviceService;
    private final JwtService jwtService;
    private final com.remitz.modules.notification.service.NotificationPreferenceService preferenceService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @GetMapping
    @Operation(summary = "Get notifications", description = "Get paginated in-app notifications for the authenticated user")
    public ResponseEntity<ApiResponse<PageResponse<InAppNotificationResponse>>> getNotifications(
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId = extractUserId(request);
        Pageable pageable = PageRequest.of(page, size);
        Page<InAppNotificationResponse> notifications = inAppNotificationService.getNotifications(userId, pageable);

        PageResponse<InAppNotificationResponse> pageResponse = PageResponse.<InAppNotificationResponse>builder()
                .content(notifications.getContent())
                .page(notifications.getNumber())
                .size(notifications.getSize())
                .totalElements(notifications.getTotalElements())
                .totalPages(notifications.getTotalPages())
                .build();

        return ResponseEntity.ok(ApiResponse.<PageResponse<InAppNotificationResponse>>builder()
                .success(true)
                .data(pageResponse)
                .build());
    }

    @PutMapping("/{id}/read")
    @Operation(summary = "Mark as read", description = "Mark a specific notification as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable Long id) {
        inAppNotificationService.markAsRead(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Notification marked as read")
                .build());
    }

    @PutMapping("/read-all")
    @Operation(summary = "Mark all as read", description = "Mark all notifications as read for the authenticated user")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(HttpServletRequest request) {
        Long userId = extractUserId(request);
        inAppNotificationService.markAllAsRead(userId);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("All notifications marked as read")
                .build());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread count", description = "Get the count of unread notifications for the authenticated user")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(HttpServletRequest request) {
        Long userId = extractUserId(request);
        long count = inAppNotificationService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.<Long>builder()
                .success(true)
                .data(count)
                .build());
    }

    @GetMapping("/preferences")
    @Operation(summary = "Get notification preferences for the authenticated user")
    public ResponseEntity<ApiResponse<com.remitz.modules.notification.dto.NotificationPreferenceResponse>> getPreferences(
            HttpServletRequest request) {
        Long userId = extractUserId(request);
        return ResponseEntity.ok(ApiResponse.<com.remitz.modules.notification.dto.NotificationPreferenceResponse>builder()
                .success(true)
                .data(preferenceService.getPreferences(userId))
                .build());
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update notification preferences for the authenticated user")
    public ResponseEntity<ApiResponse<com.remitz.modules.notification.dto.NotificationPreferenceResponse>> updatePreferences(
            HttpServletRequest request,
            @RequestBody com.remitz.modules.notification.dto.NotificationPreferenceRequest body) {
        Long userId = extractUserId(request);
        return ResponseEntity.ok(ApiResponse.<com.remitz.modules.notification.dto.NotificationPreferenceResponse>builder()
                .success(true)
                .data(preferenceService.updatePreferences(userId, body))
                .message("Preferences updated")
                .build());
    }

    @PostMapping("/devices")
    @Operation(summary = "Register device", description = "Register a device for push notifications")
    public ResponseEntity<ApiResponse<DeviceResponse>> registerDevice(
            HttpServletRequest request,
            @Valid @RequestBody DeviceRegistrationRequest deviceRequest) {

        Long userId = extractUserId(request);
        DeviceResponse response = deviceService.registerDevice(userId, deviceRequest);
        return ResponseEntity.ok(ApiResponse.<DeviceResponse>builder()
                .success(true)
                .data(response)
                .message("Device registered successfully")
                .build());
    }

    @DeleteMapping("/devices/{id}")
    @Operation(summary = "Unregister device", description = "Unregister a device from push notifications")
    public ResponseEntity<ApiResponse<Void>> unregisterDevice(@PathVariable Long id) {
        deviceService.unregisterDevice(id);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Device unregistered successfully")
                .build());
    }

    private Long extractUserId(HttpServletRequest request) {
        try {
            String token = request.getHeader("Authorization");
            String uuid = null;
            if (token != null && token.startsWith("Bearer ")) {
                uuid = jwtService.getUserUuidFromToken(token.substring(7));
            }
            if (uuid == null) {
                var auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null) uuid = auth.getName();
            }
            if (uuid != null) {
                Long id = jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE uuid = ?", Long.class, uuid);
                if (id != null) return id;
            }
        } catch (Exception ignore) {}
        return 0L;
    }
}

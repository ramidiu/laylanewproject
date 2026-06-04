package com.remitz.modules.notification.service;

import com.remitz.common.dto.InAppNotificationResponse;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.notification.dto.AdminInAppNotificationRow;
import com.remitz.modules.notification.entity.InAppNotificationEntity;
import com.remitz.modules.notification.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationService {

    private final InAppNotificationRepository inAppNotificationRepository;
    private final NotificationPreferenceService preferenceService;

    @Transactional
    public InAppNotificationResponse create(Long userId, String title, String message,
                                             String type, String referenceType, Long referenceId) {
        InAppNotificationEntity entity = InAppNotificationEntity.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .referenceType(referenceType)
                .referenceId(referenceId)
                .isRead(false)
                .build();

        entity = inAppNotificationRepository.save(entity);
        log.debug("Created in-app notification for user: {}, title: '{}'", userId, title);
        return toResponse(entity);
    }

    @Transactional(readOnly = true)
    public Page<InAppNotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return inAppNotificationRepository
                .findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public void markAsRead(Long id) {
        InAppNotificationEntity entity = inAppNotificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with id: " + id));

        if (!entity.getIsRead()) {
            entity.setIsRead(true);
            entity.setReadAt(LocalDateTime.now());
            inAppNotificationRepository.save(entity);
            log.debug("Marked notification {} as read", id);
        }
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        int updated = inAppNotificationRepository.markAllAsReadByUserId(userId);
        log.debug("Marked {} notifications as read for user: {}", updated, userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return inAppNotificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public int sendToUsers(List<Long> userIds, String title, String message, String type) {
        String resolvedType = (type == null || type.isBlank()) ? "SYSTEM" : type;

        List<Long> filtered = userIds.stream()
                .filter(java.util.Objects::nonNull)
                .toList();
        if (filtered.isEmpty()) return 0;

        List<Long> eligible = preferenceService.filterUsersByType(filtered, resolvedType);
        int skipped = filtered.size() - eligible.size();

        int count = 0;
        for (Long userId : eligible) {
            InAppNotificationEntity entity = InAppNotificationEntity.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .type(resolvedType)
                    .isRead(false)
                    .build();
            inAppNotificationRepository.save(entity);
            count++;
        }
        log.info("Admin broadcast created {} in-app notifications ('{}', type={}), skipped {} opted-out users",
                count, title, resolvedType, skipped);
        return count;
    }

    @Transactional
    public int broadcastToAllUsers(String title, String message, String type) {
        List<Long> userIds = inAppNotificationRepository.findAllActiveUserIds();
        return sendToUsers(userIds, title, message, type);
    }

    @Transactional(readOnly = true)
    public Page<AdminInAppNotificationRow> getRecentNotifications(Pageable pageable) {
        return inAppNotificationRepository
                .findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toAdminRow);
    }

    private AdminInAppNotificationRow toAdminRow(InAppNotificationEntity entity) {
        return AdminInAppNotificationRow.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .isRead(entity.getIsRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private InAppNotificationResponse toResponse(InAppNotificationEntity entity) {
        return InAppNotificationResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .type(entity.getType())
                .referenceType(entity.getReferenceType())
                .referenceId(entity.getReferenceId())
                .isRead(entity.getIsRead())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

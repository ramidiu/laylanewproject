package com.remitz.modules.notification.service;

import com.remitz.common.dto.DeviceRegistrationRequest;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.notification.dto.DeviceResponse;
import com.remitz.modules.notification.entity.UserDeviceEntity;
import com.remitz.modules.notification.repository.UserDeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {

    private final UserDeviceRepository userDeviceRepository;

    @Transactional
    public DeviceResponse registerDevice(Long userId, DeviceRegistrationRequest request) {
        // Check if device token already exists
        Optional<UserDeviceEntity> existing = userDeviceRepository.findByDeviceToken(request.getDeviceToken());

        UserDeviceEntity device;
        if (existing.isPresent()) {
            // Update existing device registration
            device = existing.get();
            device.setUserId(userId);
            device.setPlatform(request.getPlatform());
            device.setIsActive(true);
            log.info("Updated device registration for user: {}, token: {}", userId,
                    com.remitz.common.util.PiiMasker.maskToken(request.getDeviceToken()));
        } else {
            // Create new device registration
            device = UserDeviceEntity.builder()
                    .userId(userId)
                    .deviceToken(request.getDeviceToken())
                    .platform(request.getPlatform())
                    .isActive(true)
                    .build();
            log.info("Registered new device for user: {}, platform: {}", userId, request.getPlatform());
        }

        device = userDeviceRepository.save(device);
        return toResponse(device);
    }

    @Transactional
    public void unregisterDevice(Long deviceId, Long callerUserId) {
        UserDeviceEntity device = userDeviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found with id: " + deviceId));

        // SECURITY (M-F): a user may only unregister their OWN device (was IDOR by raw id).
        if (device.getUserId() == null || !device.getUserId().equals(callerUserId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not authorized to unregister this device");
        }

        device.setIsActive(false);
        userDeviceRepository.save(device);
        log.info("Unregistered device: {}", deviceId);
    }

    private DeviceResponse toResponse(UserDeviceEntity entity) {
        return DeviceResponse.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .deviceToken(entity.getDeviceToken())
                .platform(entity.getPlatform())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

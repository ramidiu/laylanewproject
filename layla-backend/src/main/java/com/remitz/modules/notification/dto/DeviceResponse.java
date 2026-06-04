package com.remitz.modules.notification.dto;

import com.remitz.common.enums.DevicePlatform;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {

    private Long id;
    private Long userId;
    private String deviceToken;
    private DevicePlatform platform;
    private Boolean isActive;
    private LocalDateTime createdAt;
}

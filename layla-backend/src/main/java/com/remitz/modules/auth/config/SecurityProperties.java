package com.remitz.modules.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class SecurityProperties {
    private int maxLoginAttempts;
    private int lockoutDurationMinutes;
    private int maxConcurrentSessions;
    private int passwordHistoryCount;
}

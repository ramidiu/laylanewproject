package com.remitz.modules.compliance.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.compliance")
@Getter
@Setter
public class ComplianceProperties {

    private int screeningThreshold = 85;
    private boolean autoHoldOnMatch = true;
}

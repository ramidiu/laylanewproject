package com.remitz.modules.payin.kuber.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Code added by Naresh: Phase 1 foundation for Kuber Financial PayTo pay-in integration.
 * Configuration bag only — no Spring beans, no HTTP client wiring yet.
 * See application.yml for the kuber.* block.
 */
@Configuration
@ConfigurationProperties(prefix = "kuber")
@Getter
@Setter
public class KuberProperties {

    private boolean enabled = false;
    private String baseUrl = "https://backend.kuberfinancial.com.au/api/payments";
    private String merchantId = "";
    private String deviceId = "";
    private int connectTimeout = 10000;
    private int readTimeout = 15000;
}

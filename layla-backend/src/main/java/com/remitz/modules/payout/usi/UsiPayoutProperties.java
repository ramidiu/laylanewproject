package com.remitz.modules.payout.usi;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Credentials + endpoint for the USI Money XML API.
 * Configure via application.yml or env vars:
 *   USI_URL, USI_USERNAME, USI_PASSWORD, USI_PIN, USI_AGENT_NAME
 *
 * If usi.enabled is false, the service is loaded but all HTTP calls
 * fail-fast with a clear error — useful in dev/CI before creds are wired.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "usi")
public class UsiPayoutProperties {
    private boolean enabled = false;
    private String url = "";
    private String username = "";
    private String password = "";
    private String pin = "";
    private String agentName = "Layla London";
}

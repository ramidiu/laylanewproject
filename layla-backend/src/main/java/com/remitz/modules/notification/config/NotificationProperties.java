package com.remitz.modules.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.notification")
@Getter
@Setter
public class NotificationProperties {

    private EmailConfig email = new EmailConfig();
    private ResendConfig resend = new ResendConfig();
    private TwilioConfig twilio = new TwilioConfig();
    private FcmConfig fcm = new FcmConfig();
    private RetryConfig retry = new RetryConfig();

    @Getter
    @Setter
    public static class EmailConfig {
        private String from;
        private String provider;
    }

    @Getter
    @Setter
    public static class ResendConfig {
        private String apiKey;
        private String baseUrl;
    }

    @Getter
    @Setter
    public static class TwilioConfig {
        private String accountSid;
        private String authToken;
        private String fromNumber;
    }

    @Getter
    @Setter
    public static class FcmConfig {
        private String credentialsPath;
    }

    @Getter
    @Setter
    public static class RetryConfig {
        private int maxAttempts = 3;
        private long delayMs = 5000;
    }
}

package com.remitz.modules.payin.volume.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "volume")
public class VolumeProperties {
    private String applicationId;
    private String paymentUrl = "https://api.volumepay.io/api/paymentintents/link";
    private String paymentStatusUrl = "https://api.volumepay.io/api/paymentintents/";
    private String pemUrl = "https://api.volumepay.io/.well-known/signature/pem";
    private boolean enabled = true;
    private String environment = "SANDBOX";
    private String jsUrl = "https://js.volumepay.io";
}

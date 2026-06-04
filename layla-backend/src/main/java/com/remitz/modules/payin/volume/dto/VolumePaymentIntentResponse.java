package com.remitz.modules.payin.volume.dto;

import lombok.Data;

@Data
public class VolumePaymentIntentResponse {
    private boolean success;
    private String merchantPaymentId;
    private String applicationId;
    private String environment;
    private String jsUrl;
    private String message;
}

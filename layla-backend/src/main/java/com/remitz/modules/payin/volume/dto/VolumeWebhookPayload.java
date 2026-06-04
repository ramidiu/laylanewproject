package com.remitz.modules.payin.volume.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolumeWebhookPayload {
    private String paymentId;
    private String merchantPaymentId;
    private String paymentStatus;
    private Boolean isExternal;
    private PaymentRequest paymentRequest;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PaymentRequest {
        private BigDecimal amount;
        private String currency;
        private String reference;
    }
}

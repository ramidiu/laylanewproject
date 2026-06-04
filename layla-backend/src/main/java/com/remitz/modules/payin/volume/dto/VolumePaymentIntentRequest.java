package com.remitz.modules.payin.volume.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class VolumePaymentIntentRequest {
    /** Internal Layla transaction ID (from send-money flow) */
    private String transactionId;
    private String merchantPaymentId;
    private BigDecimal amount;
    private String currency;
}

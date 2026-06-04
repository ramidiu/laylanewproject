package com.remitz.modules.payin.volume.service;

import com.remitz.modules.payin.volume.dto.VolumePaymentIntentRequest;
import com.remitz.modules.payin.volume.dto.VolumePaymentIntentResponse;

public interface VolumePaymentService {
    VolumePaymentIntentResponse createPaymentIntent(VolumePaymentIntentRequest request);
    String getPaymentStatus(String paymentId);
    boolean processWebhook(String json, String signature);
}

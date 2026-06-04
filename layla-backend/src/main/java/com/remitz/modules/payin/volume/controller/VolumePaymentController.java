package com.remitz.modules.payin.volume.controller;

import com.remitz.modules.payin.volume.dto.VolumePaymentIntentRequest;
import com.remitz.modules.payin.volume.dto.VolumePaymentIntentResponse;
import com.remitz.modules.payin.volume.service.VolumePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/volume")
@RequiredArgsConstructor
@Slf4j
public class VolumePaymentController {

    private final VolumePaymentService volumePaymentService;

    @PostMapping("/payment-intent")
    public ResponseEntity<VolumePaymentIntentResponse> createPaymentIntent(
            @RequestBody VolumePaymentIntentRequest request) {
        return ResponseEntity.ok(volumePaymentService.createPaymentIntent(request));
    }

    @GetMapping("/status/{paymentId}")
    public ResponseEntity<String> getPaymentStatus(@PathVariable String paymentId) {
        return ResponseEntity.ok(volumePaymentService.getPaymentStatus(paymentId));
    }
}

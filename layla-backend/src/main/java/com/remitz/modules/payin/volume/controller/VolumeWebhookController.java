package com.remitz.modules.payin.volume.controller;

import com.remitz.modules.payin.volume.service.VolumePaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class VolumeWebhookController {

    private final VolumePaymentService volumePaymentService;

    private static final String VOLUME_CONTENT_TYPE = "application/vnd.volume.v0.7+json";

    @PutMapping(value = "/volume",
            consumes = {VOLUME_CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Void> handleVolumeWebhook(
            @RequestBody String json,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String signature) {
        log.info("Volume webhook received");
        try {
            boolean success = volumePaymentService.processWebhook(json, signature);
            return success
                    ? ResponseEntity.ok().build()
                    : ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        } catch (Exception e) {
            log.error("Volume webhook processing error: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

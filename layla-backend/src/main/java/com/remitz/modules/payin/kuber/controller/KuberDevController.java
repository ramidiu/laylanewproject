package com.remitz.modules.payin.kuber.controller;

import com.remitz.modules.payin.kuber.config.KuberProperties;
import com.remitz.modules.payin.kuber.dto.KuberAddItemResponse;
import com.remitz.modules.payin.kuber.dto.KuberCheckoutTestRequest;
import com.remitz.modules.payin.kuber.service.KuberCheckoutService;
import com.remitz.modules.payin.kuber.service.KuberTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code added by Naresh: Phase 2 + Phase 3 DEV-only controller used to smoke-test the
 * Kuber integration end-to-end. Never returns raw tokens or raw upstream payloads in
 * full — only a curated beginner-friendly summary. Remove (or gate behind a profile)
 * before production.
 */
@RestController
@RequestMapping("/api/dev/kuber")
@RequiredArgsConstructor
@Slf4j
public class KuberDevController {

    private final KuberProperties properties;
    private final KuberTokenService tokenService;
    private final KuberCheckoutService checkoutService;

    @GetMapping("/token-test")
    public ResponseEntity<Map<String, Object>> tokenTest() {
        Map<String, Object> body = new LinkedHashMap<>();

        if (!properties.isEnabled()) {
            body.put("success", false);
            body.put("tokenPresent", false);
            body.put("message", "Kuber integration is disabled (kuber.enabled=false).");
            return ResponseEntity.ok(body);
        }

        try {
            String token = tokenService.getValidToken();
            boolean present = token != null && !token.isBlank();
            body.put("success", present);
            body.put("tokenPresent", present);
            body.put("tokenLength", present ? token.length() : 0);
            body.put("message", present
                    ? "Kuber token obtained successfully."
                    : "Kuber responded without a usable token.");
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.warn("Kuber token-test failed: {}", e.getMessage());
            body.put("success", false);
            body.put("tokenPresent", false);
            body.put("message", "Kuber token generation failed: " + e.getMessage());
            return ResponseEntity.ok(body);
        }
    }

    @PostMapping("/checkout-test")
    public ResponseEntity<Map<String, Object>> checkoutTest(@RequestBody KuberCheckoutTestRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();

        if (!properties.isEnabled()) {
            body.put("success", false);
            body.put("orderId", null);
            body.put("redirectUrl", null);
            body.put("message", "Kuber integration is disabled (kuber.enabled=false).");
            return ResponseEntity.ok(body);
        }

        try {
            KuberAddItemResponse resp = checkoutService.createCheckoutFromDevInput(request);
            body.put("success", true);
            body.put("orderId", resp.data().orderID());
            body.put("redirectUrl", resp.data().redirectURL());
            body.put("message", "Kuber checkout created successfully.");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            log.warn("Kuber checkout-test bad input: {}", e.getMessage());
            body.put("success", false);
            body.put("orderId", null);
            body.put("redirectUrl", null);
            body.put("message", "Invalid checkout input: " + e.getMessage());
            return ResponseEntity.badRequest().body(body);
        } catch (Exception e) {
            log.warn("Kuber checkout-test failed: {}", e.getMessage());
            body.put("success", false);
            body.put("orderId", null);
            body.put("redirectUrl", null);
            body.put("message", "Kuber checkout creation failed: " + e.getMessage());
            return ResponseEntity.ok(body);
        }
    }
}

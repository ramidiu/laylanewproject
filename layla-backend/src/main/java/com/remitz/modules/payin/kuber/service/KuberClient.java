package com.remitz.modules.payin.kuber.service;

import com.remitz.modules.payin.kuber.config.KuberProperties;
import com.remitz.modules.payin.kuber.dto.KuberAddItemRequest;
import com.remitz.modules.payin.kuber.dto.KuberAddItemResponse;
import com.remitz.modules.payin.kuber.dto.KuberGenerateTokenRequest;
import com.remitz.modules.payin.kuber.dto.KuberGenerateTokenResponse;
import com.remitz.modules.user.service.SystemConfigService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Code added by Naresh: Phase 2 + Phase 3 HTTP client for Kuber Financial Payment API.
 * - Phase 2: POST /generateToken
 * - Phase 3: POST /addItem (createCheckout) with one-shot 401 refresh-and-retry.
 * PayTo and getPaymentStatus remain to be added in later phases.
 */
@Component
@Slf4j
public class KuberClient {

    private final KuberProperties properties;
    private final KuberTokenService tokenService;
    // Code added by Naresh: System Controls Phase 5 — runtime routing gate.
    private final SystemConfigService systemConfigService;
    private RestClient restClient;

    public KuberClient(KuberProperties properties,
                       @Lazy KuberTokenService tokenService,
                       SystemConfigService systemConfigService) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.systemConfigService = systemConfigService;
    }

    /**
     * Code added by Naresh: Read runtime control from system_config with safe fallback.
     * Default TRUE so missing/invalid config never silently disables Kuber when infra
     * is configured. {@code kuber.enabled} (infra) AND this runtime toggle must both
     * be true for Kuber calls to proceed.
     */
    private boolean routingEnabled() {
        return systemConfigService.getBoolean("routing.payin.kuber.enabled", true);
    }

    @PostConstruct
    void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeout());
        factory.setReadTimeout(properties.getReadTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Calls POST /generateToken and returns the raw JWT from the response's data.token field.
     * Throws IllegalStateException when kuber.enabled=false or credentials are blank,
     * RuntimeException on transport / parsing failure.
     */
    public String generateToken() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Kuber integration is disabled (kuber.enabled=false)");
        }
        if (!routingEnabled()) {
            throw new IllegalStateException("Kuber routing is disabled (routing.payin.kuber.enabled=false)");
        }
        String merchantId = properties.getMerchantId();
        String deviceId = properties.getDeviceId();
        if (merchantId == null || merchantId.isBlank()) {
            throw new IllegalStateException("Kuber merchantId is not configured (KUBER_MERCHANT_ID)");
        }
        if (deviceId == null || deviceId.isBlank()) {
            throw new IllegalStateException("Kuber deviceId is not configured (KUBER_DEVICE_ID)");
        }

        log.info("Kuber token request started (merchant={} device={})",
                mask(merchantId), mask(deviceId));

        KuberGenerateTokenRequest body = new KuberGenerateTokenRequest(merchantId, deviceId);
        try {
            KuberGenerateTokenResponse resp = restClient.post()
                    .uri("/generateToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(KuberGenerateTokenResponse.class);

            if (resp == null || resp.data() == null
                    || resp.data().token() == null || resp.data().token().isBlank()) {
                throw new IllegalStateException("Kuber response did not contain a token");
            }

            log.info("Kuber token response received (tokenLength={})", resp.data().token().length());
            return resp.data().token();
        } catch (RestClientException e) {
            log.warn("Kuber token request failed: {}", e.getMessage());
            throw new RuntimeException("Kuber token request failed: " + e.getMessage(), e);
        }
    }

    /**
     * Calls POST /addItem to create a Kuber checkout and return orderID + redirectURL.
     * Acquires a token via KuberTokenService; on HTTP 401 the cached token is invalidated
     * and the call is retried once before surfacing the failure.
     */
    public KuberAddItemResponse createCheckout(KuberAddItemRequest request) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Kuber integration is disabled (kuber.enabled=false)");
        }
        if (!routingEnabled()) {
            throw new IllegalStateException("Kuber routing is disabled (routing.payin.kuber.enabled=false)");
        }
        log.info("Kuber checkout request started (eventName={}, amount={}, items={})",
                request.eventName(), request.amount(),
                request.menuList() == null ? 0 : request.menuList().size());

        try {
            return postAddItem(tokenService.getValidToken(), request);
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Kuber addItem returned 401 — invalidating cached token and retrying once");
            tokenService.invalidate();
            try {
                return postAddItem(tokenService.getValidToken(), request);
            } catch (RestClientException retryEx) {
                log.warn("Kuber addItem retry failed: {}", retryEx.getMessage());
                throw new RuntimeException("Kuber addItem retry failed: " + retryEx.getMessage(), retryEx);
            }
        } catch (RestClientException e) {
            log.warn("Kuber addItem request failed: {}", e.getMessage());
            throw new RuntimeException("Kuber addItem request failed: " + e.getMessage(), e);
        }
    }

    private KuberAddItemResponse postAddItem(String token, KuberAddItemRequest request) {
        KuberAddItemResponse resp = restClient.post()
                .uri("/addItem")
                .header("access_token", token)
                .header("deviceid", properties.getDeviceId())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KuberAddItemResponse.class);

        if (resp == null || resp.data() == null) {
            throw new IllegalStateException("Kuber addItem response missing data");
        }
        if (resp.data().orderID() == null || resp.data().orderID().isBlank()) {
            throw new IllegalStateException("Kuber addItem response missing orderID");
        }
        if (resp.data().redirectURL() == null || resp.data().redirectURL().isBlank()) {
            throw new IllegalStateException("Kuber addItem response missing redirectURL");
        }
        log.info("Kuber addItem response received (orderId={})", resp.data().orderID());
        return resp;
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 6) return "***";
        return value.substring(0, 4) + "***" + value.substring(value.length() - 2);
    }
}

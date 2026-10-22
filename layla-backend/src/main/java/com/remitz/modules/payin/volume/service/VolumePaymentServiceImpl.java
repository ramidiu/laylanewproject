package com.remitz.modules.payin.volume.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.modules.payin.volume.config.VolumeProperties;
import com.remitz.modules.payin.volume.dto.VolumePaymentIntentRequest;
import com.remitz.modules.payin.volume.dto.VolumePaymentIntentResponse;
import com.remitz.modules.payin.volume.dto.VolumeWebhookPayload;
import com.remitz.modules.payin.volume.entity.VolumePaymentEntity;
import com.remitz.modules.payin.volume.repository.VolumePaymentRepository;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.user.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
@RequiredArgsConstructor
public class VolumePaymentServiceImpl implements VolumePaymentService {

    private final VolumeProperties volumeProperties;
    private final VolumePaymentRepository volumePaymentRepository;
    private final TransactionRepository transactionRepository;
    private final VolumeSignatureService signatureService;
    private final ObjectMapper objectMapper;
    // Runtime routing gate — super admin controls this in System Controls
    // (system_config key routing.payin.volume.enabled, seeded disabled).
    private final SystemConfigService systemConfigService;

    @Override
    @Transactional
    public VolumePaymentIntentResponse createPaymentIntent(VolumePaymentIntentRequest request) {
        // Default FALSE: if the config row is missing/invalid, Volume stays off (product decision).
        if (!systemConfigService.getBoolean("routing.payin.volume.enabled", false)) {
            throw new IllegalStateException(
                    "Volume (Open Banking) pay-in is disabled (routing.payin.volume.enabled=false)");
        }
        // SECURITY: bind the pay-in amount/currency to the SERVER-SIDE transaction, never to the
        // client-supplied request. Otherwise a caller could declare a £1 intent for a £500 transfer
        // and the webhook amount-check (which validates against the stored amount) would pass.
        TransactionEntity tx = transactionRepository.findByReferenceNumber(request.getTransactionId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown transaction reference: " + request.getTransactionId()));
        final java.math.BigDecimal expectedAmount = tx.getTotalDebitAmount();
        final String expectedCurrency = tx.getSendCurrency();

        VolumePaymentEntity entity = volumePaymentRepository
                .findByMerchantPaymentId(request.getMerchantPaymentId())
                .orElseGet(() -> VolumePaymentEntity.builder()
                        .merchantPaymentId(request.getMerchantPaymentId())
                        .transactionId(request.getTransactionId())
                        .transactionReference(request.getTransactionId())
                        .currencyIso(expectedCurrency)
                        .amount(expectedAmount)
                        .paymentStatus("INITIATED")
                        .build());
        volumePaymentRepository.save(entity);

        VolumePaymentIntentResponse response = new VolumePaymentIntentResponse();
        response.setSuccess(true);
        response.setMerchantPaymentId(request.getMerchantPaymentId());
        response.setApplicationId(volumeProperties.getApplicationId());
        response.setEnvironment(volumeProperties.getEnvironment());
        response.setJsUrl(volumeProperties.getJsUrl());
        return response;
    }

    @Override
    public String getPaymentStatus(String paymentId) {
        try {
            String url = volumeProperties.getPaymentStatusUrl() + paymentId + "/status";
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-application-id", volumeProperties.getApplicationId());
            ResponseEntity<String> resp = new RestTemplate().exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return resp.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch Volume payment status for {}: {}", paymentId, e.getMessage());
            throw new RuntimeException("Failed to get payment status", e);
        }
    }

    @Override
    @Transactional
    public boolean processWebhook(String json, String signature) {
        if (!signatureService.verify(json, signature)) {
            log.warn("Volume webhook signature verification failed");
            return false;
        }

        try {
            VolumeWebhookPayload payload = objectMapper.readValue(json, VolumeWebhookPayload.class);
            log.info("Volume webhook: paymentId={}, merchantPaymentId={}, status={}",
                    payload.getPaymentId(), payload.getMerchantPaymentId(), payload.getPaymentStatus());

            // Match to the payment record we created at intent time. If there is no record we
            // cannot verify the webhook against anything, so we MUST NOT fulfil it.
            VolumePaymentEntity entity = volumePaymentRepository
                    .findByMerchantPaymentId(payload.getMerchantPaymentId())
                    .orElse(null);
            if (entity == null) {
                log.error("SECURITY: Volume webhook for unknown merchantPaymentId={} — no local payment record. Not fulfilling.",
                        payload.getMerchantPaymentId());
                return true; // ack (200) so Volume stops retrying; nothing to fulfil — flagged for investigation
            }

            // Idempotency — already finalised? acknowledge and skip (webhooks can arrive multiple times).
            if ("COMPLETED".equalsIgnoreCase(entity.getPaymentStatus()) ||
                    "SETTLED".equalsIgnoreCase(entity.getPaymentStatus())) {
                log.info("Volume payment {} already finalised ({}), skipping duplicate webhook",
                        payload.getMerchantPaymentId(), entity.getPaymentStatus());
                return true;
            }

            // CRITICAL: verify the webhook data against the stored payment (amount + currency)
            // before acting on it — prevents tampered / mismatched fulfilment (per Volume docs).
            if (payload.getPaymentRequest() != null) {
                java.math.BigDecimal whAmount = payload.getPaymentRequest().getAmount();
                String whCurrency = payload.getPaymentRequest().getCurrency();
                boolean amountOk = whAmount != null && entity.getAmount() != null
                        && whAmount.compareTo(entity.getAmount()) == 0;
                boolean currencyOk = whCurrency != null && whCurrency.equalsIgnoreCase(entity.getCurrencyIso());
                if (!amountOk || !currencyOk) {
                    log.error("SECURITY: Volume webhook MISMATCH for {} — expected {} {} but webhook sent {} {}. NOT fulfilling.",
                            payload.getMerchantPaymentId(), entity.getAmount(), entity.getCurrencyIso(), whAmount, whCurrency);
                    entity.setPaymentStatus("MISMATCH");
                    entity.setSettleStatus("mismatch");
                    volumePaymentRepository.save(entity);
                    return true; // ack to stop retries; flagged for manual investigation
                }
                entity.setTransactionReference(payload.getPaymentRequest().getReference());
            }

            // Verified — record the final status (do NOT overwrite the stored amount/currency).
            entity.setMerchantPaymentId(payload.getMerchantPaymentId());
            entity.setPaymentId(payload.getPaymentId());
            entity.setPaymentStatus(payload.getPaymentStatus());
            entity.setIsExternal(payload.getIsExternal());
            entity.setSettleStatus(payload.getPaymentStatus());

            if ("COMPLETED".equalsIgnoreCase(payload.getPaymentStatus())) {
                entity.setSettleStatus("success");
                // ONLY a successful payment advances the customer transaction (-> PROCESSING, ready for payout).
                updateTransactionStatus(entity, TransactionStatus.PROCESSING);
            } else if ("FAILED".equalsIgnoreCase(payload.getPaymentStatus())) {
                // IMPORTANT: a Volume FAILED webhook must NOT flip the customer transaction to FAILED.
                // Open-banking can report FAILED for a payment that is still in flight or was actually
                // received, so hard-failing here wrongly shows customers a failed transfer (past incident:
                // TXN5286/5287/5302). We RECORD the failed attempt on the volume_payments row for audit,
                // but leave the transaction untouched (stays PENDING). If it never completes, the
                // pending-transaction archive scheduler retires it after its window. Only a later
                // COMPLETED webhook advances it.
                entity.setSettleStatus("failed");
                log.warn("Volume webhook FAILED for {} (txn {}) — recorded for audit, transaction left "
                        + "unchanged (PENDING). Raw payload: {}",
                        payload.getMerchantPaymentId(), entity.getTransactionId(), json);
            }
            // SETTLED — virtual-account/internal only; recorded above, no customer-facing action.

            volumePaymentRepository.save(entity);
            return true;

        } catch (Exception e) {
            log.error("Error processing Volume webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing Volume webhook", e);
        }
    }

    private void updateTransactionStatus(VolumePaymentEntity entity, TransactionStatus status) {
        String ref = entity.getTransactionId() != null ? entity.getTransactionId() : entity.getTransactionReference();
        if (ref == null) return;
        transactionRepository.findByReferenceNumber(ref).ifPresent(txn -> {
            txn.setStatus(status);
            transactionRepository.save(txn);
            log.info("Transaction {} set to {} after Volume webhook", ref, status);
        });
    }
}

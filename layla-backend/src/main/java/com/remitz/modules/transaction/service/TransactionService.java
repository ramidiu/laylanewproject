package com.remitz.modules.transaction.service;

import com.remitz.common.dto.*;
import com.remitz.common.enums.ActorType;
import com.remitz.common.enums.TransactionStatus;
import com.remitz.common.enums.UserStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.common.util.ReferenceNumberGenerator;
import com.remitz.modules.transaction.config.RedisPublisher;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.entity.CorridorFeeConfig;
import com.remitz.modules.transaction.entity.CorridorPartnerMapping;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.entity.TransactionStatusHistoryEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.repository.CorridorFeeConfigRepository;
import com.remitz.modules.transaction.repository.CorridorPartnerMappingRepository;
import com.remitz.modules.transaction.repository.PaymentMethodRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.transaction.repository.TransactionStatusHistoryRepository;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.compliance.entity.ScreeningResultEntity;
import com.remitz.modules.compliance.service.ComplianceAlertService;
import com.remitz.modules.compliance.service.SanctionsScreeningService;
import com.remitz.modules.user.service.KycService;
import com.remitz.modules.user.service.ReferralService;
import com.remitz.modules.user.service.SystemConfigService;
import com.remitz.modules.user.service.WalletService;
import com.remitz.modules.notification.service.NotificationDispatcher;
import com.remitz.common.enums.AlertSeverity;
import com.remitz.common.enums.EntityType;
import com.remitz.common.dto.KycDocumentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Core transaction orchestration service. Owns the entire end-to-end flow of
 * creating a transfer, from quote validation to compliance screening to ledger
 * writes and state-machine transitions.
 *
 * <h2>Creation flow (see {@link #createTransaction(CreateTransactionRequest, Long, String, String, String, String)})</h2>
 * <ol>
 *   <li><b>Beneficiary validation</b> — ensure the beneficiary belongs to the
 *       sender and is not blocked.</li>
 *   <li><b>Limit check</b> — enforce KYC-tier daily and per-transaction caps
 *       via {@code validateTransactionLimits}.</li>
 *   <li><b>Quote retrieval</b> — pull the locked quote from Redis (keyed as
 *       {@code fx:quote:{quoteId}}, written by fx-service). Reject if expired.</li>
 *   <li><b>Referral rate boost</b> — on the customer's first transaction only,
 *       apply any first-send rate boost from a referral code.</li>
 *   <li><b>Wallet debit</b> — if {@code useWallet=true}, call user-service to
 *       debit the sender's wallet before proceeding.</li>
 *   <li><b>Payout partner routing</b> — look up
 *       {@code corridor_partner_mappings} to auto-assign the downstream payout
 *       partner.</li>
 *   <li><b>Persist + transition to PENDING</b> via
 *       {@link TransactionStateMachine}.</li>
 *   <li><b>Compliance screening</b> — synchronous calls to
 *       {@code /internal/compliance/screen} for both sender and beneficiary.
 *       A hit on either places the transaction on COMPLIANCE_HOLD.</li>
 *   <li><b>High-risk corridor check</b> — a blocking rule that holds sends
 *       to jurisdictions in {@link #HIGH_RISK_COUNTRIES}.</li>
 *   <li><b>Non-blocking rules</b> — new-beneficiary + high-amount, baseline
 *       anomaly, round-number, rapid-succession. Each posts an alert via
 *       {@code /internal/compliance/alerts} but does not hold the transaction.</li>
 *   <li><b>Risk score</b> — compute a 0-100 score from device fingerprint,
 *       amount, corridor, beneficiary age, and compliance hits. Stored with a
 *       JSON {@code risk_factors} breakdown.</li>
 *   <li><b>Ledger entries</b> — double-entry records for transfer, FX margin,
 *       and fee.</li>
 *   <li><b>Publish event</b> to Redis for downstream consumers.</li>
 * </ol>
 *
 * <h2>Direct module dependencies</h2>
 * <ul>
 *   <li>{@code UserRepository}, {@code WalletService} — wallet debit, UUID→id lookup</li>
 *   <li>{@code ReferralService} — referral code validation and reward processing</li>
 *   <li>{@code SanctionsScreeningService}, {@code ComplianceAlertService} — compliance checks</li>
 *   <li>{@code KycService} — KYC document verification</li>
 * </ul>
 *
 * <h2>Adding a new compliance rule</h2>
 * Add a private {@code evaluateXxxRule} helper alongside the existing ones,
 * then wire the call into the rule pipeline in {@code createTransaction} under
 * the "Non-blocking rules" block. For blocking rules, call
 * {@code stateMachine.transition(saved, COMPLIANCE_HOLD, …)} instead of
 * {@code postAlert(…)}.
 *
 * <h2>Important invariants</h2>
 * <ul>
 *   <li>Money never moves without passing through the state machine.</li>
 *   <li>Every status change writes a row to {@code transaction_status_history}.</li>
 *   <li>Compliance screening failures fall back to "allow" (soft-pass) with a
 *       warning log — prevents outages in compliance-service from blocking
 *       customers, but means monitoring should alarm on screening error rate.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final TransactionStatusHistoryRepository statusHistoryRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final CorridorPartnerMappingRepository corridorPartnerMappingRepository;
    private final CorridorFeeConfigRepository corridorFeeConfigRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final TransactionStateMachine stateMachine;
    private final RedisPublisher redisPublisher;
    private final RedisTemplate<String, Object> redisTemplate;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;
    private final WalletService walletService;
    private final ReferralService referralService;
    private final KycService kycService;
    private final SanctionsScreeningService sanctionsScreeningService;
    private final ComplianceAlertService complianceAlertService;
    // Code added by Naresh: System Controls Phase 5 — runtime min/max send-amount guardrails.
    private final SystemConfigService systemConfigService;
    private final NotificationDispatcher notificationDispatcher;

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request, Long senderUserId, String senderEmail) {
        return createTransaction(request, senderUserId, senderEmail, null);
    }

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request, Long senderUserId, String senderEmail, String senderUuid) {
        return createTransaction(request, senderUserId, senderEmail, senderUuid, null, null);
    }

    @Transactional
    public TransactionResponse createTransaction(CreateTransactionRequest request, Long senderUserId, String senderEmail,
                                                  String senderUuid, String visitorId, String forwardedFor) {
        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        if (!systemConfigService.getBoolean("transactions.enabled", true)) {
            throw new RemitzException(
                    "New transactions are temporarily disabled. Please try again later.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        // Block transactions for users whose profile is pending admin review
        userRepository.findById(senderUserId).ifPresent(sender -> {
            if (sender.getStatus() == UserStatus.PENDING_VERIFICATION) {
                throw new RemitzException(
                        "Your profile is currently under review. You will be able to send money once an admin approves your account.",
                        HttpStatus.FORBIDDEN);
            }
        });

        // Idempotency check — return existing transaction if key already used
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()) {
            Optional<TransactionEntity> existing = transactionRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                TransactionEntity existingTxn = existing.get();
                String benefName = getBeneficiaryName(existingTxn.getBeneficiaryId());
                log.info("Idempotent request: returning existing transaction {} for key {}",
                        existingTxn.getReferenceNumber(), request.getIdempotencyKey());
                return mapToResponse(existingTxn, benefName);
            }
        }

        // Validate beneficiary belongs to user
        BeneficiaryEntity beneficiary = beneficiaryRepository.findByUserIdAndId(senderUserId, request.getBeneficiaryId())
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary", "id", request.getBeneficiaryId()));

        if (beneficiary.getIsBlocked()) {
            throw new RemitzException("Beneficiary is blocked", HttpStatus.BAD_REQUEST);
        }

        // Enforce KYC verification: all submitted documents must be APPROVED
        validateKycDocumentsVerified(senderUuid);

        // Server-side gate: the sender's currency must have at least one ACTIVE payment
        // method configured in Transfer Config. Prevents users from disabled sender
        // countries from initiating transactions even if they bypass the UI.
        if (request.getSendCurrency() != null) {
            boolean sendCurrencyActive = !paymentMethodRepository
                    .findByCurrencyAndIsActive(request.getSendCurrency(), true).isEmpty();
            if (!sendCurrencyActive) {
                throw new RemitzException(
                        "Sending from " + request.getSendCurrency() + " is currently unavailable. Please contact support.",
                        HttpStatus.BAD_REQUEST);
            }
        }

        // Enforce transaction limits based on user's risk level
        validateTransactionLimits(senderUserId, request.getSendAmount());

        // Try to retrieve locked quote from Redis (key matches FX service: "fx:quote:{quoteId}")
        String quoteKey = "fx:quote:" + request.getQuoteId();
        QuoteResponse quote = null;
        try {
            // Use StringRedisTemplate to read raw JSON, then parse manually
            org.springframework.data.redis.core.StringRedisTemplate stringRedis =
                    new org.springframework.data.redis.core.StringRedisTemplate(redisTemplate.getConnectionFactory());
            String raw = stringRedis.opsForValue().get(quoteKey);
            if (raw != null) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                // The FX service stores with type metadata; strip it by parsing as tree
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(raw);
                // If it's an array (Jackson default typing wraps as [className, {data}]), get the second element
                if (node.isArray() && node.size() == 2) {
                    node = node.get(1);
                }
                quote = QuoteResponse.builder()
                        .quoteId(node.path("quoteId").asText())
                        .sendAmount(node.has("sendAmount") ? parseBd(node.get("sendAmount")) : null)
                        .receiveAmount(node.has("receiveAmount") ? parseBd(node.get("receiveAmount")) : null)
                        .exchangeRate(node.has("exchangeRate") ? parseBd(node.get("exchangeRate")) : null)
                        .appliedRate(node.has("appliedRate") ? parseBd(node.get("appliedRate")) : null)
                        .marginApplied(node.has("marginApplied") ? parseBd(node.get("marginApplied")) : null)
                        .fee(node.has("fee") ? parseBd(node.get("fee")) : null)
                        .totalCost(node.has("totalCost") ? parseBd(node.get("totalCost")) : null)
                        .expiresInSeconds(60L)
                        .build();
                log.info("Retrieved quote from Redis: {}", request.getQuoteId());
            }
        } catch (Exception e) {
            log.warn("Could not retrieve quote from Redis for quoteId={}: {}", request.getQuoteId(), e.getMessage());
        }

        if (quote == null) {
            throw new RemitzException("Quote not found or expired. Please get a new quote.", HttpStatus.BAD_REQUEST);
        }

        // Validate quote amounts match request (allow small rounding differences)
        if (quote.getSendAmount() != null && request.getSendAmount() != null
                && quote.getSendAmount().subtract(request.getSendAmount()).abs().compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new RemitzException("Send amount does not match the quoted amount", HttpStatus.BAD_REQUEST);
        }

        // ── Referral Code (rate boost only on first transaction) ──────────────
        BigDecimal rateBoostPct = BigDecimal.ZERO;
        String validatedReferralCode = null;
        long existingTxnCount = transactionRepository.countBySenderId(senderUserId);
        if (request.getReferralCode() != null && !request.getReferralCode().isBlank() && existingTxnCount == 0) {
            try {
                Map<String, Object> refResp = referralService.validateCode(request.getReferralCode(), request.getCorridorId());
                if (Boolean.TRUE.equals(refResp.get("valid"))) {
                    validatedReferralCode = request.getReferralCode();
                    Object boost = refResp.get("rateBoostPercentage");
                    if (boost != null) {
                        rateBoostPct = new BigDecimal(boost.toString());
                    }
                    log.info("Referral code {} applied, rate boost {}%", validatedReferralCode, rateBoostPct);
                }
            } catch (Exception e) {
                log.warn("Could not validate referral code {}: {}", request.getReferralCode(), e.getMessage());
            }
        }

        // Apply rate boost to the applied rate
        BigDecimal boostedAppliedRate = quote.getAppliedRate();
        BigDecimal rateBoostAmount = BigDecimal.ZERO;
        if (rateBoostPct.compareTo(BigDecimal.ZERO) > 0 && boostedAppliedRate != null) {
            rateBoostAmount = boostedAppliedRate.multiply(rateBoostPct)
                    .divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP);
            boostedAppliedRate = boostedAppliedRate.add(rateBoostAmount);
            // Recalculate receiveAmount with boosted rate
            if (request.getSendAmount() != null) {
                BigDecimal boostedReceive = request.getSendAmount().multiply(boostedAppliedRate)
                        .setScale(4, java.math.RoundingMode.HALF_UP);
                quote = QuoteResponse.builder()
                        .quoteId(quote.getQuoteId())
                        .sendAmount(quote.getSendAmount())
                        .receiveAmount(boostedReceive)
                        .exchangeRate(quote.getExchangeRate())
                        .appliedRate(boostedAppliedRate)
                        .marginApplied(quote.getMarginApplied())
                        .fee(quote.getFee())
                        .totalCost(quote.getTotalCost())
                        .expiresInSeconds(60L)
                        .build();
                log.info("Rate boosted: base={} boost={}% boosted={} receiveAmount={}",
                        quote.getExchangeRate(), rateBoostPct, boostedAppliedRate, boostedReceive);
            }
        }

        // ── Wallet debit is deferred until after compliance screening (see below) ──
        BigDecimal walletAmountUsed = BigDecimal.ZERO;
        final BigDecimal walletAmountRequested;
        final boolean walletRequested = Boolean.TRUE.equals(request.getUseWallet())
                && request.getWalletAmountToUse() != null
                && request.getWalletAmountToUse().compareTo(BigDecimal.ZERO) > 0;
        if (walletRequested) {
            BigDecimal totalCost = quote.getTotalCost() != null ? quote.getTotalCost() : request.getSendAmount();
            walletAmountRequested = request.getWalletAmountToUse().min(totalCost);
        } else {
            walletAmountRequested = BigDecimal.ZERO;
        }

        // Generate reference number
        String referenceNumber = ReferenceNumberGenerator.generate();

        // Auto-assign payout partner from corridor-partner mapping
        Long payoutPartnerId = null;
        String sendCurrency = request.getSendCurrency();
        // Determine receive currency from quote or corridor
        String receiveCurrency = null;
        if (quote.getReceiveAmount() != null) {
            // Try to get from corridor
            receiveCurrency = beneficiary.getCountry() != null ? getCurrencyForCountry(beneficiary.getCountry()) : null;
        }
        if (receiveCurrency == null && request.getCorridorId() != null) {
            receiveCurrency = sendCurrency; // fallback, will be overridden
        }

        // Lookup corridor-partner mapping
        if (sendCurrency != null && receiveCurrency != null) {
            List<CorridorPartnerMapping> mappings = corridorPartnerMappingRepository
                    .findByFromCurrencyAndToCurrency(sendCurrency, receiveCurrency);
            if (!mappings.isEmpty()) {
                CorridorPartnerMapping activeMapping = mappings.stream()
                        .filter(m -> Boolean.TRUE.equals(m.getIsActive()))
                        .findFirst()
                        .orElse(null);
                if (activeMapping != null) {
                    payoutPartnerId = activeMapping.getPartnerId();
                    log.info("Auto-assigned payout partner {} for corridor {}->{}", payoutPartnerId, sendCurrency, receiveCurrency);
                }
            }
        }

        // Auto-assign pay-in partner from CorridorFeeConfig (same config the admin sets
        // per corridor in the "Corridor Management" screen). Null = no pay-in partner
        // configured → admin handles pay-in.
        Long payinPartnerId = null;
        if (sendCurrency != null && receiveCurrency != null) {
            List<CorridorFeeConfig> feeConfigs = corridorFeeConfigRepository
                    .findByFromCurrencyAndToCurrency(sendCurrency, receiveCurrency);
            payinPartnerId = feeConfigs.stream()
                    .map(CorridorFeeConfig::getPayinPartnerId)
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (payinPartnerId != null) {
                log.info("Auto-assigned payin partner {} for corridor {}->{}", payinPartnerId, sendCurrency, receiveCurrency);
            }
        }

        // Create the transaction entity
        TransactionEntity transaction = TransactionEntity.builder()
                .referenceNumber(referenceNumber)
                .senderId(senderUserId)
                .senderName(senderEmail != null ? senderEmail.split("@")[0] : "Customer")
                .senderEmail(senderEmail)
                .beneficiaryId(request.getBeneficiaryId())
                .corridorId(request.getCorridorId())
                .status(TransactionStatus.CREATED)
                .deliveryMethod(request.getDeliveryMethod())
                .sendAmount(request.getSendAmount())
                .sendCurrency(request.getSendCurrency())
                .receiveAmount(quote.getReceiveAmount())
                .receiveCurrency(receiveCurrency != null ? receiveCurrency : request.getSendCurrency())
                .exchangeRate(quote.getExchangeRate())
                .appliedRate(quote.getAppliedRate())
                .lockedRate(quote.getExchangeRate())
                .rateLockedAt(LocalDateTime.now())
                .rateLockExpiresAt(quote.getRateLockedUntil())
                .feeAmount(quote.getFee() != null ? quote.getFee() : BigDecimal.ZERO)
                .feeCurrency(request.getSendCurrency())
                .fxMarginAmount(quote.getMarginApplied() != null ? quote.getMarginApplied() : BigDecimal.ZERO)
                .totalDebitAmount(quote.getTotalCost() != null ? quote.getTotalCost() : request.getSendAmount())
                .paymentMethodType(request.getPaymentMethodType())
                .payoutPartnerId(payoutPartnerId)
                .payinPartnerId(payinPartnerId)
                .isRecurring(false)
                .notes(request.getNotes())
                .walletAmountUsed(walletAmountUsed)
                .referralCodeUsed(validatedReferralCode)
                .rateBoostApplied(rateBoostPct.compareTo(BigDecimal.ZERO) > 0 ? rateBoostPct : null)
                .rateBoostAmount(rateBoostAmount.compareTo(BigDecimal.ZERO) > 0 ? rateBoostAmount : null)
                .visitorId(visitorId != null && !visitorId.isBlank() ? visitorId : null)
                .idempotencyKey(request.getIdempotencyKey())
                .build();

        // Override receiveCurrency from quote if available
        if (quote.getReceiveAmount() != null) {
            transaction.setReceiveAmount(quote.getReceiveAmount());
        }

        TransactionEntity saved = transactionRepository.save(transaction);

        // Create initial status history
        TransactionStatusHistoryEntity initialHistory = TransactionStatusHistoryEntity.builder()
                .transaction(saved)
                .fromStatus(null)
                .toStatus(TransactionStatus.CREATED)
                .actorId(senderUserId)
                .actorType(ActorType.USER)
                .reason("Transaction created")
                .build();
        statusHistoryRepository.save(initialHistory);

        // Transition to PENDING
        saved = stateMachine.transition(saved, TransactionStatus.PENDING, senderUserId, ActorType.USER, "Transaction submitted");

        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        // Master switch + per-layer switch for compliance screening.
        boolean complianceEnabled = systemConfigService.getBoolean("compliance.enabled", true);
        boolean txScreeningEnabled = systemConfigService.getBoolean("compliance.transaction_screening.enabled", true);
        boolean skipRecheck = systemConfigService.getBoolean("compliance.skip_recheck_for_approved_customers", true);
        boolean screeningBypassed = !complianceEnabled || !txScreeningEnabled;
        if (screeningBypassed) {
            log.warn("Transaction screening bypassed for txn {}: compliance.enabled={}, compliance.transaction_screening.enabled={}",
                    saved.getReferenceNumber(), complianceEnabled, txScreeningEnabled);
        }

        // Skip re-screening for senders/beneficiaries that have already been cleared on a
        // prior transaction that reached PROCESSING or beyond. KYC doc approval is enforced
        // upstream in validateKycDocumentsVerified(); combined with a prior cleared run,
        // this means admin has signed off on both KYC and compliance already.
        boolean senderAlreadyCleared = skipRecheck
                && transactionRepository.existsSenderComplianceCleared(senderUserId);
        boolean beneficiaryAlreadyCleared = skipRecheck
                && transactionRepository.existsBeneficiaryComplianceCleared(senderUserId, beneficiary.getId());

        // Synchronous sanction/PEP screening on sender. On hit -> COMPLIANCE_HOLD.
        if (!screeningBypassed && !senderAlreadyCleared && isSenderScreeningHit(senderUuid, saved.getId())) {
            saved = stateMachine.transition(saved, TransactionStatus.COMPLIANCE_HOLD, null,
                    ActorType.SYSTEM, "Sender matched sanction/PEP list");
            log.warn("Transaction {} placed on COMPLIANCE_HOLD: sender hit",
                    saved.getReferenceNumber());
        } else if (senderAlreadyCleared) {
            log.info("Skipping sender screening for txn {} — sender {} previously cleared",
                    saved.getReferenceNumber(), senderUserId);
        }

        // Synchronous sanction/PEP screening on beneficiary. On hit -> COMPLIANCE_HOLD.
        if (saved.getStatus() != TransactionStatus.COMPLIANCE_HOLD
                && !screeningBypassed
                && !beneficiaryAlreadyCleared
                && isBeneficiaryScreeningHit(beneficiary, saved.getId())) {
            saved = stateMachine.transition(saved, TransactionStatus.COMPLIANCE_HOLD, null,
                    ActorType.SYSTEM, "Beneficiary matched sanction/PEP list");
            log.warn("Transaction {} placed on COMPLIANCE_HOLD: beneficiary '{}' hit",
                    saved.getReferenceNumber(), beneficiary.getFullName());
        } else if (beneficiaryAlreadyCleared) {
            log.info("Skipping beneficiary screening for txn {} — beneficiary {} previously cleared",
                    saved.getReferenceNumber(), beneficiary.getId());
        }

        // Blocking: high-risk corridor check. Sends to sanctioned jurisdictions
        // land on COMPLIANCE_HOLD immediately.
        if (saved.getStatus() != TransactionStatus.COMPLIANCE_HOLD
                && isHighRiskCorridor(beneficiary.getCountry())) {
            saved = stateMachine.transition(saved, TransactionStatus.COMPLIANCE_HOLD, null,
                    ActorType.SYSTEM, "High-risk corridor: " + beneficiary.getCountry());
            log.warn("Transaction {} placed on COMPLIANCE_HOLD: high-risk corridor {}",
                    saved.getReferenceNumber(), beneficiary.getCountry());
        }

        // Non-blocking rules: surface alerts but do not hold the transaction.
        evaluateNewBeneficiaryHighAmountRule(beneficiary, saved, senderUserId);
        evaluateBaselineAnomalyRule(saved, senderUserId);
        evaluateRoundNumberRule(saved, senderUserId);
        evaluateRapidSuccessionRule(saved, senderUserId);

        // Compute and persist a 0-100 risk score based on device fingerprint +
        // prior rule hits + amount-vs-baseline signals. Stored on the txn row.
        saved = computeAndStoreRiskScore(saved, beneficiary, senderUserId, visitorId);

        // ── Wallet Debit (AFTER screening) ──────────────────────────────────
        // Only debit wallet if the transaction was NOT placed on COMPLIANCE_HOLD.
        // If held, wallet debit happens when compliance releases the transaction.
        if (walletRequested && walletAmountRequested.compareTo(BigDecimal.ZERO) > 0
                && saved.getStatus() != TransactionStatus.COMPLIANCE_HOLD) {
            walletAmountUsed = performWalletDebit(senderUuid, walletAmountRequested, saved.getReferenceNumber());
            saved.setWalletAmountUsed(walletAmountUsed);
            saved = transactionRepository.save(saved);
        }

        // Publish event for compliance check (async)
        redisPublisher.publishTransactionEvent("TRANSACTION_CREATED", Map.of(
                "transactionId", saved.getId(),
                "referenceNumber", saved.getReferenceNumber(),
                "senderId", senderUserId,
                "beneficiaryId", request.getBeneficiaryId(),
                "sendAmount", saved.getSendAmount().toString(),
                "sendCurrency", saved.getSendCurrency()
        ));

        // Create ledger entries for the transaction — failures propagate and roll back the transaction
        ledgerService.createEntry(saved.getId(), "CUSTOMER:" + senderUserId, "PLATFORM:HOLDING",
                saved.getSendAmount(), saved.getSendCurrency(), "TRANSFER",
                "Transfer from customer " + senderUserId + " ref " + saved.getReferenceNumber());

        if (saved.getFxMarginAmount() != null && saved.getFxMarginAmount().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.createEntry(saved.getId(), "PLATFORM:HOLDING", "PLATFORM:FX_MARGIN",
                    saved.getFxMarginAmount(), saved.getSendCurrency(), "FX_CONVERSION",
                    "FX margin for ref " + saved.getReferenceNumber());
        }

        if (saved.getFeeAmount() != null && saved.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            ledgerService.createEntry(saved.getId(), "PLATFORM:HOLDING", "PLATFORM:REVENUE",
                    saved.getFeeAmount(), saved.getFeeCurrency(), "FEE",
                    "Fee for ref " + saved.getReferenceNumber());
        }

        return mapToResponse(saved, beneficiary.getFullName());
    }

    private static final int NEW_BENEFICIARY_WINDOW_DAYS = 7;
    private static final BigDecimal NEW_BENEFICIARY_AMOUNT_THRESHOLD = new BigDecimal("1000");
    private static final int BASELINE_WINDOW_DAYS = 30;
    private static final int BASELINE_MIN_TXN_COUNT = 3;
    private static final BigDecimal BASELINE_MULTIPLIER = new BigDecimal("5");
    private static final int RAPID_SUCCESSION_WINDOW_SECONDS = 60;
    private static final java.util.List<BigDecimal> ROUND_NUMBER_DENOMINATIONS =
            java.util.List.of(new BigDecimal("1000"), new BigDecimal("5000"), new BigDecimal("10000"));
    private static final BigDecimal ROUND_NUMBER_MIN_AMOUNT = new BigDecimal("1000");
    // NOTE: Sudan (SD/SDN) intentionally REMOVED — it is Layla's licensed core corridor,
    // so transactions to Sudan must NOT auto-hold on COMPLIANCE_HOLD. Re-add only if the
    // licence/compliance position changes.
    private static final java.util.Set<String> HIGH_RISK_COUNTRIES = java.util.Set.of(
            "IRN", "IR", "PRK", "KP", "SYR", "SY", "AFG", "AF", "YEM", "YE", "CUB", "CU",
            "RUS", "RU", "BLR", "BY", "MMR", "MM", "VEN", "VE");

    private boolean isHighRiskCorridor(String country) {
        if (country == null || country.isBlank()) return false;
        return HIGH_RISK_COUNTRIES.contains(country.trim().toUpperCase());
    }

    private void evaluateBaselineAnomalyRule(TransactionEntity txn, Long senderUserId) {
        try {
            if (txn.getSendAmount() == null) return;

            LocalDateTime since = LocalDateTime.now().minusDays(BASELINE_WINDOW_DAYS);
            long priorCount = transactionRepository.countBySenderIdSince(senderUserId, since);
            if (priorCount <= BASELINE_MIN_TXN_COUNT) return; // not enough history

            BigDecimal priorSum = transactionRepository.sumSendAmountByUserSince(senderUserId, since);
            if (priorSum == null || priorSum.compareTo(BigDecimal.ZERO) <= 0) return;

            // Subtract this txn since it was just saved and counted
            BigDecimal historySum = priorSum.subtract(txn.getSendAmount());
            long historyCount = priorCount - 1;
            if (historyCount < BASELINE_MIN_TXN_COUNT || historySum.compareTo(BigDecimal.ZERO) <= 0) return;

            BigDecimal avg = historySum.divide(BigDecimal.valueOf(historyCount), 2, java.math.RoundingMode.HALF_UP);
            BigDecimal anomalyFloor = avg.multiply(BASELINE_MULTIPLIER);

            if (txn.getSendAmount().compareTo(anomalyFloor) < 0) return;

            String description = String.format(
                    "Baseline anomaly: send of %s %s is >= %sx the customer's %d-day avg of %s",
                    txn.getSendAmount(), txn.getSendCurrency(), BASELINE_MULTIPLIER,
                    BASELINE_WINDOW_DAYS, avg);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rule", "BASELINE_ANOMALY");
            details.put("windowDays", BASELINE_WINDOW_DAYS);
            details.put("historyTxnCount", historyCount);
            details.put("historyAvgAmount", avg.toString());
            details.put("currentAmount", txn.getSendAmount().toString());
            details.put("multiplierTriggered", BASELINE_MULTIPLIER.toString());

            postAlert(senderUserId, txn.getId(), "HIGH", description, details);
        } catch (Exception e) {
            log.warn("Baseline anomaly rule failed for txn {}: {}", txn.getReferenceNumber(), e.getMessage());
        }
    }

    private void evaluateRoundNumberRule(TransactionEntity txn, Long senderUserId) {
        try {
            BigDecimal amount = txn.getSendAmount();
            if (amount == null || amount.compareTo(ROUND_NUMBER_MIN_AMOUNT) < 0) return;

            BigDecimal matchedDenom = null;
            for (BigDecimal denom : ROUND_NUMBER_DENOMINATIONS) {
                if (amount.remainder(denom).compareTo(BigDecimal.ZERO) == 0) {
                    matchedDenom = denom;
                }
            }
            if (matchedDenom == null) return;

            String description = String.format(
                    "Round-number send: %s %s (exact multiple of %s)",
                    amount, txn.getSendCurrency(), matchedDenom);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rule", "ROUND_NUMBER");
            details.put("amount", amount.toString());
            details.put("currency", txn.getSendCurrency());
            details.put("matchedDenomination", matchedDenom.toString());

            postAlert(senderUserId, txn.getId(), "LOW", description, details);
        } catch (Exception e) {
            log.warn("Round-number rule failed for txn {}: {}", txn.getReferenceNumber(), e.getMessage());
        }
    }

    private void evaluateRapidSuccessionRule(TransactionEntity txn, Long senderUserId) {
        try {
            LocalDateTime windowStart = LocalDateTime.now().minusSeconds(RAPID_SUCCESSION_WINDOW_SECONDS);
            long recentCount = transactionRepository.countBySenderIdSince(senderUserId, windowStart);
            // recentCount includes the just-saved txn; need at least 2 total
            if (recentCount < 2) return;

            String description = String.format(
                    "Rapid succession: %d sends from customer within %d seconds",
                    recentCount, RAPID_SUCCESSION_WINDOW_SECONDS);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rule", "RAPID_SUCCESSION");
            details.put("windowSeconds", RAPID_SUCCESSION_WINDOW_SECONDS);
            details.put("recentSendCount", recentCount);
            details.put("latestAmount", txn.getSendAmount().toString());

            postAlert(senderUserId, txn.getId(), "MEDIUM", description, details);
        } catch (Exception e) {
            log.warn("Rapid-succession rule failed for txn {}: {}", txn.getReferenceNumber(), e.getMessage());
        }
    }

    private TransactionEntity computeAndStoreRiskScore(TransactionEntity txn, BeneficiaryEntity beneficiary,
                                                        Long senderUserId, String visitorId) {
        try {
            int score = 0;
            Map<String, Object> factors = new LinkedHashMap<>();

            // 1) Device signals
            if (visitorId == null || visitorId.isBlank()) {
                score += 15;
                factors.put("deviceMissing", "No visitor_id captured — request came without fingerprint");
            } else {
                long prior = transactionRepository.countBySenderIdSince(senderUserId, LocalDateTime.now().minusDays(90));
                if (prior <= 1) {
                    score += 20;
                    factors.put("newDevice", "First transaction for this visitor_id on this account");
                }
            }

            // 2) Amount-vs-baseline signal (fresh customer or high-value send)
            if (txn.getSendAmount() != null) {
                if (txn.getSendAmount().compareTo(new BigDecimal("5000")) >= 0) {
                    score += 20;
                    factors.put("highAmount", "Send amount >= 5,000 " + txn.getSendCurrency());
                } else if (txn.getSendAmount().compareTo(new BigDecimal("1000")) >= 0) {
                    score += 10;
                    factors.put("elevatedAmount", "Send amount >= 1,000 " + txn.getSendCurrency());
                }
            }

            // 3) High-risk corridor
            if (isHighRiskCorridor(beneficiary.getCountry())) {
                score += 30;
                factors.put("highRiskCorridor", "Destination " + beneficiary.getCountry() + " is on the high-risk list");
            }

            // 4) New beneficiary age
            if (beneficiary.getCreatedAt() != null) {
                long beneficiaryAgeHours = Duration.between(beneficiary.getCreatedAt(), LocalDateTime.now()).toHours();
                if (beneficiaryAgeHours < 24) {
                    score += 15;
                    factors.put("freshBeneficiary", "Beneficiary created < 24h ago");
                } else if (beneficiaryAgeHours < 168) {
                    score += 8;
                    factors.put("newBeneficiary", "Beneficiary created < 7 days ago");
                }
            }

            // 5) Compliance hold escalates score to near-max
            if (txn.getStatus() == TransactionStatus.COMPLIANCE_HOLD) {
                score = Math.max(score, 90);
                factors.put("complianceHold", "Transaction is already on compliance hold");
            }

            score = Math.min(100, Math.max(0, score));
            txn.setRiskScore(score);
            try {
                txn.setRiskFactors(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(factors));
            } catch (Exception e) {
                txn.setRiskFactors(null);
            }
            return transactionRepository.save(txn);
        } catch (Exception e) {
            log.warn("Risk score computation failed for txn {}: {}", txn.getReferenceNumber(), e.getMessage());
            return txn;
        }
    }

    private void postAlert(Long userId, Long transactionId, String severity,
                           String description, Map<String, Object> details) {
        try {
            String detailsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(details);
            complianceAlertService.createAlert(userId, transactionId,
                    AlertSeverity.valueOf(severity), description, detailsJson);
            log.warn("Compliance alert raised: txn={} severity={} desc={}", transactionId, severity, description);
        } catch (Exception e) {
            log.warn("postAlert failed for txn {}: {}", transactionId, e.getMessage());
        }
    }

    private boolean isSenderScreeningHit(String senderUuid, Long transactionId) {
        if (senderUuid == null || senderUuid.isBlank()) return false;
        try {
            Optional<UserEntity> userOpt = userRepository.findByUuid(senderUuid);
            if (userOpt.isEmpty()) return false;
            UserEntity user = userOpt.get();

            String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                    + (user.getLastName() != null ? user.getLastName() : "")).trim();
            if (fullName.isBlank()) return false;

            String country = user.getCountry();
            String dob = user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : null;

            List<ScreeningResultEntity> results = sanctionsScreeningService.screen(
                    fullName, country, dob, EntityType.TRANSACTION, transactionId);

            if (results == null || results.isEmpty()) return false;
            for (ScreeningResultEntity row : results) {
                String status = row.getStatus() != null ? row.getStatus().name() : null;
                if ("POTENTIAL_MATCH".equals(status) || "CONFIRMED_MATCH".equals(status)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // Code added by Naresh: System Controls Phase 7 — vendor-fallback behavior.
            boolean allowOnFail = systemConfigService.getBoolean(
                    "compliance.vendor_fallback.allow_transactions", false);
            if (allowOnFail) {
                log.warn("Sender screening vendor call failed for txn {}: {} — compliance.vendor_fallback.allow_transactions=true, allowing transaction to proceed with warning",
                        transactionId, e.getMessage());
                return false;
            }
            log.error("Sender screening call failed for txn {}: {} — placing on COMPLIANCE_HOLD",
                    transactionId, e.getMessage());
            return true;
        }
    }

    private void evaluateNewBeneficiaryHighAmountRule(BeneficiaryEntity beneficiary,
                                                       TransactionEntity txn,
                                                       Long senderUserId) {
        try {
            if (beneficiary.getCreatedAt() == null || txn.getSendAmount() == null) return;

            long ageDays = Duration.between(beneficiary.getCreatedAt(), LocalDateTime.now()).toDays();
            boolean isNew = ageDays <= NEW_BENEFICIARY_WINDOW_DAYS;
            boolean isHighAmount = txn.getSendAmount().compareTo(NEW_BENEFICIARY_AMOUNT_THRESHOLD) >= 0;

            if (!isNew || !isHighAmount) return;

            String description = String.format(
                    "New beneficiary (%d days old) + high-amount send: %s %s to '%s' (%s)",
                    ageDays, txn.getSendAmount(), txn.getSendCurrency(),
                    beneficiary.getFullName(), beneficiary.getCountry());

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("rule", "NEW_BENEFICIARY_HIGH_AMOUNT");
            details.put("beneficiaryId", beneficiary.getId());
            details.put("beneficiaryAgeDays", ageDays);
            details.put("windowDays", NEW_BENEFICIARY_WINDOW_DAYS);
            details.put("sendAmount", txn.getSendAmount().toString());
            details.put("sendCurrency", txn.getSendCurrency());
            details.put("threshold", NEW_BENEFICIARY_AMOUNT_THRESHOLD.toString());
            details.put("beneficiaryCountry", beneficiary.getCountry());
            String detailsJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(details);

            complianceAlertService.createAlert(senderUserId, txn.getId(),
                    AlertSeverity.HIGH, description, detailsJson);

            log.warn("New-beneficiary-high-amount alert raised for txn {}: {}",
                    txn.getReferenceNumber(), description);
        } catch (Exception e) {
            log.warn("Failed to raise new-beneficiary alert for txn {}: {}",
                    txn.getReferenceNumber(), e.getMessage());
        }
    }

    private boolean isBeneficiaryScreeningHit(BeneficiaryEntity beneficiary, Long transactionId) {
        try {
            String fullName = beneficiary.getFullName();
            if (fullName == null || fullName.isBlank()) return false;

            List<ScreeningResultEntity> results = sanctionsScreeningService.screen(
                    fullName.trim(), beneficiary.getCountry(), null, EntityType.TRANSACTION, transactionId);

            if (results == null || results.isEmpty()) return false;
            for (ScreeningResultEntity row : results) {
                String status = row.getStatus() != null ? row.getStatus().name() : null;
                if ("POTENTIAL_MATCH".equals(status) || "CONFIRMED_MATCH".equals(status)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            // Code added by Naresh: System Controls Phase 7 — vendor-fallback behavior.
            boolean allowOnFail = systemConfigService.getBoolean(
                    "compliance.vendor_fallback.allow_transactions", false);
            if (allowOnFail) {
                log.warn("Beneficiary screening vendor call failed for txn {}: {} — compliance.vendor_fallback.allow_transactions=true, allowing transaction to proceed with warning",
                        transactionId, e.getMessage());
                return false;
            }
            log.error("Beneficiary screening call failed for txn {}: {} — placing on COMPLIANCE_HOLD",
                    transactionId, e.getMessage());
            return true;
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(Long id) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
        String beneficiaryName = getBeneficiaryName(tx.getBeneficiaryId());
        return mapToResponse(tx, beneficiaryName);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByReference(String referenceNumber) {
        TransactionEntity tx = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "referenceNumber", referenceNumber));
        String beneficiaryName = getBeneficiaryName(tx.getBeneficiaryId());
        return mapToResponse(tx, beneficiaryName);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(Long userId, TransactionListRequest request) {
        Pageable pageable = buildPageable(request);

        LocalDateTime startDateTime = request.getStartDate() != null
                ? request.getStartDate().atStartOfDay() : null;
        LocalDateTime endDateTime = request.getEndDate() != null
                ? request.getEndDate().atTime(LocalTime.MAX) : null;

        Page<TransactionEntity> page = transactionRepository.searchTransactions(
                userId,
                request.getStatus(),
                request.getCorridorId(),
                startDateTime,
                endDateTime,
                request.getSearch(),
                pageable
        );

        return page.map(tx -> mapToResponse(tx, getBeneficiaryName(tx.getBeneficiaryId())));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listAllTransactions(TransactionListRequest request) {
        Pageable pageable = buildPageable(request);

        LocalDateTime startDateTime = request.getStartDate() != null
                ? request.getStartDate().atStartOfDay() : null;
        LocalDateTime endDateTime = request.getEndDate() != null
                ? request.getEndDate().atTime(LocalTime.MAX) : null;

        Page<TransactionEntity> page = transactionRepository.searchTransactions(
                null,
                request.getStatus(),
                request.getCorridorId(),
                startDateTime,
                endDateTime,
                request.getSearch(),
                pageable
        );

        return page.map(tx -> mapToResponse(tx, getBeneficiaryName(tx.getBeneficiaryId())));
    }

    @Transactional
    public TransactionResponse cancelTransaction(Long id, Long userId) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Verify ownership
        if (!tx.getSenderId().equals(userId)) {
            throw new RemitzException("Transaction does not belong to user", HttpStatus.FORBIDDEN);
        }

        // Can only cancel before PROCESSING
        if (tx.getStatus() == TransactionStatus.PROCESSING ||
                tx.getStatus() == TransactionStatus.FUNDS_RECEIVED ||
                tx.getStatus() == TransactionStatus.SENT_TO_PAYOUT ||
                tx.getStatus() == TransactionStatus.PAID ||
                tx.getStatus() == TransactionStatus.FAILED ||
                tx.getStatus() == TransactionStatus.REFUNDED) {
            throw new RemitzException("Transaction cannot be cancelled in status: " + tx.getStatus(), HttpStatus.BAD_REQUEST);
        }

        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.CANCELLED, userId, ActorType.USER, "Cancelled by user");

        // Create REVERSAL ledger entry — failures propagate and roll back
        ledgerService.createEntry(updated.getId(), "PLATFORM:HOLDING", "CUSTOMER:" + userId,
                updated.getSendAmount(), updated.getSendCurrency(), "REVERSAL",
                "Reversal for cancelled transaction ref " + updated.getReferenceNumber());

        // Credit wallet back if wallet was used
        creditWalletBack(updated);

        String beneficiaryName = getBeneficiaryName(updated.getBeneficiaryId());
        return mapToResponse(updated, beneficiaryName);
    }

    @Transactional
    public TransactionResponse updateStatus(Long id, TransactionStatusUpdateRequest request,
                                            Long actorId, ActorType actorType) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        if (request.getStatus() == TransactionStatus.COMPLIANCE_HOLD && request.getReason() != null) {
            tx.setComplianceHoldReason(request.getReason());
        }

        TransactionEntity updated = stateMachine.transition(tx, request.getStatus(), actorId, actorType, request.getReason());

        // When transaction completes, credit the referrer's wallet
        if ((request.getStatus() == TransactionStatus.COMPLETED || request.getStatus() == TransactionStatus.PAID)
                && updated.getReferralCodeUsed() != null) {
            triggerReferralReward(updated);
        }

        String beneficiaryName = getBeneficiaryName(updated.getBeneficiaryId());
        return mapToResponse(updated, beneficiaryName);
    }

    /**
     * Final settlement step: transition PAID → COMPLETED and publish an event the admin
     * panel subscribes to. Called from both the payout-partner "Mark Paid" flow and the
     * admin fallback "Mark Paid" flow (when no payout partner is configured).
     *
     * Idempotent: if already COMPLETED, returns as-is.
     */
    @Transactional
    public TransactionResponse completeTransaction(Long id, Long actorId, ActorType actorType) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        // Admin-visible event (admin dashboard / notification bell subscribes to this).
        redisPublisher.publishTransactionEvent("TRANSACTION_COMPLETED", Map.of(
                "transactionId", tx.getId(),
                "referenceNumber", tx.getReferenceNumber(),
                "senderId", tx.getSenderId(),
                "beneficiaryId", tx.getBeneficiaryId(),
                "receiveAmount", tx.getReceiveAmount() != null ? tx.getReceiveAmount().toString() : "0",
                "receiveCurrency", tx.getReceiveCurrency() != null ? tx.getReceiveCurrency() : ""
        ));

        // Send TX_PAID email to the customer
        try {
            final TransactionEntity completedTx = tx;
            String beneficiaryNameForEmail = getBeneficiaryName(completedTx.getBeneficiaryId());
            final String finalBeneficiaryName = beneficiaryNameForEmail != null ? beneficiaryNameForEmail : "";
            userRepository.findById(completedTx.getSenderId()).ifPresent(user -> {
                Map<String, String> vars = new java.util.LinkedHashMap<>();
                vars.put("firstName", user.getFirstName() != null ? user.getFirstName() : "Customer");
                vars.put("referenceNumber", completedTx.getReferenceNumber());
                vars.put("receiveAmount", completedTx.getReceiveAmount() != null ? completedTx.getReceiveAmount().toPlainString() : "0");
                vars.put("receiveCurrency", completedTx.getReceiveCurrency() != null ? completedTx.getReceiveCurrency() : "");
                vars.put("beneficiaryName", finalBeneficiaryName);
                notificationDispatcher.dispatch(
                        "TX_PAID", user.getId(), user.getEmail(), user.getPhone(),
                        user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "en",
                        vars, completedTx.getId());
            });
        } catch (Exception e) {
            log.warn("Failed to send TX_PAID notification for {}: {}", tx.getReferenceNumber(), e.getMessage());
        }

        String beneficiaryName = getBeneficiaryName(tx.getBeneficiaryId());
        return mapToResponse(tx, beneficiaryName);
    }

    @Transactional(readOnly = true)
    public List<StatusHistoryResponse> getStatusHistory(Long transactionId) {
        // Verify transaction exists
        if (!transactionRepository.existsById(transactionId)) {
            throw new ResourceNotFoundException("Transaction", "id", transactionId);
        }

        List<TransactionStatusHistoryEntity> history =
                statusHistoryRepository.findByTransactionIdOrderByCreatedAtAsc(transactionId);

        return history.stream()
                .map(h -> StatusHistoryResponse.builder()
                        .fromStatus(h.getFromStatus())
                        .toStatus(h.getToStatus())
                        .actorType(h.getActorType())
                        .reason(h.getReason())
                        .createdAt(h.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public TransactionResponse initiateRefund(Long id, Long actorId) {
        TransactionEntity tx = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        TransactionEntity updated = stateMachine.transition(tx, TransactionStatus.REFUNDED, actorId, ActorType.ADMIN, "Refund initiated");

        // Credit wallet back if wallet was used
        creditWalletBack(updated);

        // Publish refund event
        redisPublisher.publishTransactionEvent("REFUND_INITIATED", Map.of(
                "transactionId", updated.getId(),
                "referenceNumber", updated.getReferenceNumber(),
                "senderId", updated.getSenderId(),
                "amount", updated.getSendAmount().toString(),
                "currency", updated.getSendCurrency()
        ));

        String beneficiaryName = getBeneficiaryName(updated.getBeneficiaryId());
        return mapToResponse(updated, beneficiaryName);
    }

    private void triggerReferralReward(TransactionEntity tx) {
        try {
            referralService.processReferralReward(tx.getReferralCodeUsed(), tx.getCorridorId(), tx.getReferenceNumber());
            log.info("Referral reward triggered for code={} txn={}", tx.getReferralCodeUsed(), tx.getReferenceNumber());
        } catch (Exception e) {
            log.warn("Could not trigger referral reward for {}: {}", tx.getReferenceNumber(), e.getMessage());
        }
    }

    private String getBeneficiaryName(Long beneficiaryId) {
        return beneficiaryRepository.findById(beneficiaryId)
                .map(BeneficiaryEntity::getFullName)
                .orElse("Unknown");
    }

    private Pageable buildPageable(TransactionListRequest request) {
        int page = request.getPage() != null ? request.getPage() : 0;
        int size = request.getSize() != null ? request.getSize() : 20;
        String sortBy = request.getSortBy() != null ? request.getSortBy() : "createdAt";
        Sort.Direction direction = "asc".equalsIgnoreCase(request.getSortDir())
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private TransactionResponse mapToResponse(TransactionEntity tx, String beneficiaryName) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .referenceNumber(tx.getReferenceNumber())
                .payoutReference(tx.getPayoutReference())
                .senderName(null) // Would require user-service call; populated by gateway/aggregation layer
                .beneficiaryName(beneficiaryName)
                .status(tx.getStatus())
                .deliveryMethod(tx.getDeliveryMethod())
                .sendAmount(tx.getSendAmount())
                .sendCurrency(tx.getSendCurrency())
                .receiveAmount(tx.getReceiveAmount())
                .receiveCurrency(tx.getReceiveCurrency())
                .exchangeRate(tx.getExchangeRate())
                .appliedRate(tx.getAppliedRate())
                .feeAmount(tx.getFeeAmount())
                .totalDebitAmount(tx.getTotalDebitAmount())
                .paymentMethodType(tx.getPaymentMethodType())
                .createdAt(tx.getCreatedAt())
                .updatedAt(tx.getUpdatedAt())
                .walletAmountUsed(tx.getWalletAmountUsed())
                .referralCodeUsed(tx.getReferralCodeUsed())
                .rateBoostApplied(tx.getRateBoostApplied())
                .riskScore(tx.getRiskScore())
                .riskFactors(tx.getRiskFactors())
                .visitorId(tx.getVisitorId())
                .build();
    }

    /**
     * Performs the wallet debit via direct service call.
     * Returns the amount actually debited (zero if debit failed or was skipped).
     */
    private BigDecimal performWalletDebit(String senderUuid, BigDecimal amount, String referenceNumber) {
        if (senderUuid == null || senderUuid.isBlank()) return BigDecimal.ZERO;
        try {
            Optional<UserEntity> userOpt = userRepository.findByUuid(senderUuid);
            if (userOpt.isEmpty()) return BigDecimal.ZERO;
            Long walletUserId = userOpt.get().getId();

            walletService.debit(walletUserId, amount,
                    "Wallet payment for transaction " + referenceNumber,
                    referenceNumber, "TRANSACTION");
            log.info("Wallet debit: userId={} amount={} ref={}", walletUserId, amount, referenceNumber);
            return amount;
        } catch (Exception e) {
            log.warn("Wallet debit failed for UUID={} ref={}: {}. Proceeding without wallet.",
                    senderUuid, referenceNumber, e.getMessage());
        }
        return BigDecimal.ZERO;
    }

    /**
     * Credits wallet back when a transaction is cancelled or refunded.
     * Only acts if walletAmountUsed > 0. Failure is logged but does not block the refund.
     */
    private void creditWalletBack(TransactionEntity tx) {
        if (tx.getWalletAmountUsed() == null || tx.getWalletAmountUsed().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        try {
            walletService.credit(tx.getSenderId(), tx.getWalletAmountUsed(),
                    "Wallet refund for " + tx.getStatus().name().toLowerCase() + " transaction " + tx.getReferenceNumber(),
                    tx.getReferenceNumber(), "REFUND");
            log.info("Wallet credited back: userId={} amount={} ref={}",
                    tx.getSenderId(), tx.getWalletAmountUsed(), tx.getReferenceNumber());
        } catch (Exception e) {
            log.error("CRITICAL: Wallet credit-back failed for refund/cancel txn={} amount={}: {}. Manual reconciliation required.",
                    tx.getReferenceNumber(), tx.getWalletAmountUsed(), e.getMessage());
        }
    }

    private String getCurrencyForCountry(String countryCode) {
        if (countryCode == null) return null;
        Map<String, String> map = Map.ofEntries(
                // 2-letter ISO
                Map.entry("IN", "INR"), Map.entry("PK", "PKR"), Map.entry("NG", "NGN"),
                Map.entry("GH", "GHS"), Map.entry("PH", "PHP"), Map.entry("KE", "KES"),
                Map.entry("BD", "BDT"), Map.entry("ZA", "ZAR"), Map.entry("LK", "LKR"),
                Map.entry("NP", "NPR"), Map.entry("GB", "GBP"), Map.entry("US", "USD"),
                Map.entry("AU", "AUD"), Map.entry("AE", "AED"), Map.entry("DE", "EUR"),
                Map.entry("SD", "SDG"), Map.entry("TR", "TRY"), Map.entry("EG", "EGP"),
                Map.entry("SA", "SAR"), Map.entry("QA", "QAR"), Map.entry("UG", "UGX"),
                // 3-letter ISO
                Map.entry("IND", "INR"), Map.entry("PAK", "PKR"), Map.entry("NGA", "NGN"),
                Map.entry("GHA", "GHS"), Map.entry("PHL", "PHP"), Map.entry("KEN", "KES"),
                Map.entry("BGD", "BDT"), Map.entry("ZAF", "ZAR"), Map.entry("LKA", "LKR"),
                Map.entry("NPL", "NPR"), Map.entry("GBR", "GBP"), Map.entry("USA", "USD"),
                Map.entry("AUS", "AUD"), Map.entry("ARE", "AED"), Map.entry("DEU", "EUR"),
                Map.entry("SDN", "SDG"), Map.entry("TUR", "TRY"), Map.entry("EGY", "EGP"),
                Map.entry("SAU", "SAR"), Map.entry("QAT", "QAR"), Map.entry("UGA", "UGX")
        );
        return map.get(countryCode.toUpperCase());
    }

    private void validateTransactionLimits(Long senderUserId, BigDecimal sendAmount) {
        if (sendAmount == null) return;

        // Code added by Naresh: Read runtime control from system_config with safe fallback.
        // Defaults are intentionally permissive (0 min, huge max) so a missing or unparseable
        // row never tightens the existing risk-based limits below.
        BigDecimal minSend = systemConfigService.getDecimal(
                "transaction.min_send_amount", BigDecimal.ZERO);
        BigDecimal maxSend = systemConfigService.getDecimal(
                "transaction.max_send_amount", new BigDecimal("999999999"));
        if (sendAmount.compareTo(minSend) < 0) {
            throw new RemitzException(
                    String.format("Transaction amount %.2f is below the minimum of %.2f.",
                            sendAmount, minSend),
                    HttpStatus.BAD_REQUEST);
        }
        if (sendAmount.compareTo(maxSend) > 0) {
            throw new RemitzException(
                    String.format("Transaction amount %.2f exceeds the maximum of %.2f.",
                            sendAmount, maxSend),
                    HttpStatus.BAD_REQUEST);
        }

        // Map KYC tier to risk level: TIER_2/TIER_3 = LOW, TIER_1 = MEDIUM, TIER_0 = HIGH
        String riskLevel = resolveRiskLevelForUser(senderUserId);

        // Per-transaction limit check based on risk level
        Map<String, BigDecimal> perTxnLimits = Map.of(
                "LOW", BigDecimal.valueOf(5000),
                "MEDIUM", BigDecimal.valueOf(1000),
                "HIGH", BigDecimal.valueOf(200)
        );

        Map<String, BigDecimal> dailyLimits = Map.of(
                "LOW", BigDecimal.valueOf(10000),
                "MEDIUM", BigDecimal.valueOf(2000),
                "HIGH", BigDecimal.valueOf(500)
        );

        BigDecimal perTxnLimit = perTxnLimits.getOrDefault(riskLevel, BigDecimal.valueOf(1000));
        BigDecimal dailyLimit = dailyLimits.getOrDefault(riskLevel, BigDecimal.valueOf(2000));

        // Check per-transaction limit
        if (sendAmount.compareTo(perTxnLimit) > 0) {
            throw new RemitzException(
                    String.format("Transaction amount %.2f exceeds your per-transaction limit of %.2f (risk level: %s). Please complete KYC to increase limits.",
                            sendAmount, perTxnLimit, riskLevel),
                    HttpStatus.BAD_REQUEST);
        }

        // Check daily limit
        try {
            BigDecimal dailyTotal = transactionRepository.sumSendAmountByUserToday(senderUserId);
            if (dailyTotal != null && dailyTotal.add(sendAmount).compareTo(dailyLimit) > 0) {
                throw new RemitzException(
                        String.format("This transaction would exceed your daily limit of %.2f (already used: %.2f, risk level: %s)",
                                dailyLimit, dailyTotal, riskLevel),
                        HttpStatus.BAD_REQUEST);
            }
        } catch (RemitzException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Daily limit check skipped: {}", e.getMessage());
        }
    }

    /**
     * Enforces full KYC verification before allowing a transaction:
     *   1. All KYC documents must be APPROVED (no pending/rejected/expired).
     */
    private void validateKycDocumentsVerified(String senderUuid) {
        if (senderUuid == null || senderUuid.isBlank()) return;
        try {
            Optional<UserEntity> userOpt = userRepository.findByUuid(senderUuid);
            if (userOpt.isEmpty()) return;
            Long userId = userOpt.get().getId();

            // Gate 1: KYC documents
            List<KycDocumentResponse> docs = kycService.getDocuments(userId);
            int total = docs.size();
            int approved = 0, pending = 0, rejected = 0, expired = 0;
            java.time.LocalDate today = java.time.LocalDate.now();
            for (KycDocumentResponse d : docs) {
                if (d.getStatus() == null) continue;
                String s = d.getStatus().name();
                if ("APPROVED".equals(s)) {
                    if (d.getExpiryDate() != null && d.getExpiryDate().isBefore(today)) expired++;
                    else approved++;
                } else if ("PENDING".equals(s)) pending++;
                else if ("REJECTED".equals(s)) rejected++;
            }

            if (total == 0) {
                throw new RemitzException(
                        "Please complete your KYC verification before sending money. Upload your identity documents first.",
                        HttpStatus.BAD_REQUEST);
            }
            if (expired > 0) {
                throw new RemitzException(
                        "Your verified documents have expired. Please upload renewed documents for admin verification.",
                        HttpStatus.BAD_REQUEST);
            }
            if (rejected > 0) {
                throw new RemitzException(
                        "One or more of your KYC documents were rejected. Please upload new documents before sending money.",
                        HttpStatus.BAD_REQUEST);
            }
            if (pending > 0) {
                throw new RemitzException(
                        "Your KYC documents are still under review. You can send money once all documents are verified.",
                        HttpStatus.BAD_REQUEST);
            }
            if (approved == 0) {
                throw new RemitzException(
                        "Please complete your KYC verification before sending money.",
                        HttpStatus.BAD_REQUEST);
            }
        } catch (RemitzException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Could not verify KYC status for UUID {}: {}. Blocking transaction to be safe.",
                    senderUuid, e.getMessage());
            throw new RemitzException(
                    "Unable to verify KYC status. Please try again or contact support.",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private String resolveRiskLevelForUser(Long userId) {
        try {
            Optional<UserEntity> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent() && userOpt.get().getKycTier() != null) {
                String kycTier = userOpt.get().getKycTier().name();
                if ("TIER_3".equals(kycTier) || "TIER_2".equals(kycTier)) return "LOW";
                if ("TIER_1".equals(kycTier)) return "MEDIUM";
            }
        } catch (Exception e) {
            log.warn("Could not resolve KYC tier for userId {}, defaulting to HIGH: {}", userId, e.getMessage());
        }
        return "HIGH";
    }

    private java.math.BigDecimal parseBd(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull()) return null;
        // Jackson default typing wraps BigDecimal as ["java.math.BigDecimal", value]
        if (node.isArray() && node.size() == 2) {
            return new java.math.BigDecimal(node.get(1).asText());
        }
        return new java.math.BigDecimal(node.asText());
    }
}

package com.remitz.modules.payout.usi.service;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.payout.usi.UsiPayoutProperties;
import com.remitz.modules.payout.usi.dto.CollectionPointResponse;
import com.remitz.modules.payout.usi.dto.RemitterVerifyResponse;
import com.remitz.modules.payout.usi.dto.UsiAdminTransactionDto;
import com.remitz.modules.payout.usi.entity.UsiBeneficiaryEntity;
import com.remitz.modules.payout.usi.entity.UsiRemitterEntity;
import com.remitz.modules.payout.usi.entity.UsiTestAccountEntity;
import com.remitz.modules.payout.usi.entity.UsiTransactionEntity;
import com.remitz.modules.payout.usi.repository.UsiBeneficiaryRepository;
import com.remitz.modules.payout.usi.repository.UsiRemitterRepository;
import com.remitz.modules.payout.usi.repository.UsiTestAccountRepository;
import com.remitz.modules.payout.usi.repository.UsiTransactionRepository;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.user.entity.KycDocumentEntity;
import com.remitz.modules.user.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Port of the legacy USImoneyServiceImpl into the new com.remitz.* backend.
 *
 * Talks USI Money's XML/x-www-form-urlencoded API to:
 *   - create / verify remitters from Layla users
 *   - create / search beneficiaries from Layla beneficiaries
 *   - create / confirm / poll-status of transactions
 *   - fetch collection points for cash-pickup countries
 *
 * Falls back to the per-country sandbox test accounts in usi_test_accounts
 * when a beneficiary is missing bank / mobile / cash details.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UsiPayoutServiceImpl implements UsiPayoutService {

    private final UsiPayoutProperties props;
    private final UserRepository userRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final TransactionRepository transactionRepository;
    private final UsiRemitterRepository usiRemitterRepository;
    private final UsiBeneficiaryRepository usiBeneficiaryRepository;
    private final UsiTransactionRepository usiTransactionRepository;
    private final UsiTestAccountRepository usiTestAccountRepository;
    private final KycDocumentRepository kycDocumentRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    // ── Admin list (does NOT require usi.enabled — read-only joins) ────

    @Override
    public List<UsiAdminTransactionDto> listAdminTransactions(String statusFilter, int limit) {
        int cap = limit <= 0 || limit > 500 ? 200 : limit;
        var page = org.springframework.data.domain.PageRequest.of(0, cap);
        List<TransactionEntity> txns = transactionRepository.findUsiEligibleTransactions(page);

        List<UsiAdminTransactionDto> out = new ArrayList<>(txns.size());
        for (TransactionEntity t : txns) {
            UsiTransactionEntity usi = usiTransactionRepository.findByTransactionId(t.getReferenceNumber()).orElse(null);

            // Status filter (matches usi_status when set, else "NEW")
            if (statusFilter != null && !statusFilter.isBlank() && !"all".equalsIgnoreCase(statusFilter)) {
                String current = usi != null && usi.getStatus() != null ? usi.getStatus() : "new";
                if (!current.equalsIgnoreCase(statusFilter)) continue;
            }

            BeneficiaryEntity bnf = beneficiaryRepository.findById(t.getBeneficiaryId()).orElse(null);

            out.add(UsiAdminTransactionDto.builder()
                    .referenceNumber(t.getReferenceNumber())
                    .senderId(t.getSenderId())
                    .senderName(t.getSenderName())
                    .senderEmail(t.getSenderEmail())
                    .beneficiaryId(t.getBeneficiaryId())
                    .beneficiaryName(bnf != null ? bnf.getFullName() : null)
                    .destinationCountry(bnf != null ? bnf.getCountry() : null)
                    .sendAmount(t.getSendAmount())
                    .sendCurrency(t.getSendCurrency())
                    .receiveAmount(t.getReceiveAmount())
                    .receiveCurrency(t.getReceiveCurrency())
                    .deliveryMethod(t.getDeliveryMethod() != null ? t.getDeliveryMethod().name() : null)
                    .laylaStatus(t.getStatus() != null ? t.getStatus().name() : null)
                    .createdAt(t.getCreatedAt())
                    .usiTransSessionId(usi != null ? usi.getTransSessionId() : null)
                    .usiReferenceNumber(usi != null ? usi.getReferenceNumber() : null)
                    .usiPaymentToken(usi != null ? usi.getPaymentToken() : null)
                    .usiStatus(usi != null ? usi.getStatus() : null)
                    .usiPaymentStatus(usi != null ? usi.getPaymentStatus() : null)
                    .usiErrorMessage(usi != null ? usi.getErrorMessage() : null)
                    .usiUpdatedAt(usi != null ? usi.getUpdatedAt() : null)
                    .build());
        }
        return out;
    }

    // ── Remitter ─────────────────────────────────────────────────────────

    @Override
    public Map<String, Object> createRemitter(Long userId) {
        guardEnabled();
        Map<String, Object> json = new HashMap<>();

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found for userId: " + userId));

        UsiRemitterEntity existing = usiRemitterRepository.findByUserId(userId).orElse(null);
        if (existing != null) {
            json.put("status", "EXISTS");
            json.put("remitterId", existing.getRemitterId());
            json.put("message", "Remitter already exists");
            return json;
        }

        String iso3 = countryIso3(user);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("group", "remitter");
        body.add("method", "createRemitter");
        body.add("firstname", capitalize(user.getFirstName()));
        body.add("lastname", capitalize(user.getLastName()));
        body.add("type", "registered");
        body.add("agent_name", props.getAgentName());
        body.add("nationality", iso3);
        body.add("status", "valid");
        body.add("gender", user.getGender() != null ? user.getGender() : "Male");
        body.add("address1", user.getAddressLine1() != null ? user.getAddressLine1() : "");
        body.add("city", user.getCity() != null ? user.getCity() : "");
        body.add("postcode", user.getPostcode() != null ? user.getPostcode() : "");
        body.add("mobile", normalisePhone(user.getPhone(), iso3));
        // USI requires a date-of-birth — fall back to a placeholder 18+ DOB if the user
        // hasn't completed their profile so the create call doesn't bounce. This will
        // be replaced when KYC capture wires DOB into users.date_of_birth.
        body.add("dob", user.getDateOfBirth() != null ? user.getDateOfBirth().toString() : "1990-01-01");
        // Pull the user's primary identity document — prefer PASSPORT, then DRIVING_LICENCE, then NATIONAL_ID.
        KycDocumentEntity primaryId = pickPrimaryId(userId);
        body.add("id_type",          primaryId != null && primaryId.getDocumentType() != null
                ? mapKycToUsiIdType(primaryId.getDocumentType().name()) : "Other");
        body.add("id_details",       primaryId != null && primaryId.getDocumentNumber() != null
                ? primaryId.getDocumentNumber() : "NA");
        body.add("id_issue_country", iso3);
        body.add("id_expiry",        primaryId != null && primaryId.getExpiryDate() != null
                ? primaryId.getExpiryDate().toString() : "");
        body.add("orgtype", "Individual");
        addCreds(body);

        String responseBody = postMultipart("/remitter/createRemitter", body);
        try {
            Document doc = parse(responseBody);
            String status = tag(doc, "status", 0);
            json.put("status", status);

            if ("SUCCESS".equalsIgnoreCase(status)) {
                String remitterId = tag(doc, "new_remitter_id", 0);
                json.put("remitterId", remitterId);
                json.put("message", "Remitter created successfully");

                usiRemitterRepository.save(UsiRemitterEntity.builder()
                        .userId(userId)
                        .remitterId(remitterId)
                        .status("ACTIVE")
                        .verified(false)
                        .build());
            } else {
                String message = firstNonBlank(tag(doc, "error", 0), tag(doc, "message", 0));
                json.put("message", message);

                // USI returns "This member already exists with Membership number 453021" on duplicates.
                // Extract the id and persist locally so subsequent calls work.
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("Membership number\\s+(\\d+)").matcher(message == null ? "" : message);
                if (m.find()) {
                    String existingId = m.group(1);
                    usiRemitterRepository.save(UsiRemitterEntity.builder()
                            .userId(userId)
                            .remitterId(existingId)
                            .status("ACTIVE")
                            .verified(false)
                            .build());
                    json.put("status", "EXISTS");
                    json.put("remitterId", existingId);
                    json.put("message", "Existing USI remitter " + existingId + " linked.");
                    log.info("USI remitter already existed — linked id {} to user {}", existingId, userId);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI createRemitter response: " + e.getMessage(), e);
        }
        return json;
    }

    @Override
    public RemitterVerifyResponse verifyRemitter(Long userId) {
        guardEnabled();
        UsiRemitterEntity existing = usiRemitterRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("USI remitter not found for userId: " + userId));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("remitter_id", existing.getRemitterId());
        body.add("agent_name", props.getAgentName());
        addCreds(body);

        String responseBody = postMultipart("/remitter/verifyRemitter", body);
        try {
            Document doc = parse(responseBody);
            String apiStatus = tag(doc, "status", 0);
            if (!"SUCCESS".equalsIgnoreCase(apiStatus)) {
                throw new RuntimeException("Verification failed: " + apiStatus);
            }

            RemitterVerifyResponse dto = new RemitterVerifyResponse();
            dto.setRemitterId(tag(doc, "remitter_id", 0));
            dto.setFirstName(tag(doc, "firstname", 0));
            dto.setLastName(tag(doc, "lastname", 0));
            dto.setVerified("t".equalsIgnoreCase(tag(doc, "verified", 0)));
            dto.setStatus(tag(doc, "status", 1));
            dto.setValid("valid".equalsIgnoreCase(dto.getStatus()));

            existing.setVerified(dto.isVerified());
            existing.setStatus(dto.isValid() ? "ACTIVE" : dto.getStatus());
            usiRemitterRepository.save(existing);

            return dto;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI verifyRemitter response: " + e.getMessage(), e);
        }
    }

    // ── Beneficiary ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> createBeneficiary(Long beneficiaryId) {
        guardEnabled();
        Map<String, Object> json = new HashMap<>();

        UsiBeneficiaryEntity existing = usiBeneficiaryRepository
                .findByLocalBeneficiaryId(String.valueOf(beneficiaryId)).orElse(null);
        if (existing != null) {
            json.put("status", "EXISTS");
            json.put("usiBeneficiaryId", existing.getUsiBeneficiaryId());
            return json;
        }

        BeneficiaryEntity bnf = beneficiaryRepository.findById(beneficiaryId)
                .orElseThrow(() -> new RuntimeException("Local beneficiary not found: " + beneficiaryId));

        UsiRemitterEntity remitter = usiRemitterRepository.findByUserId(bnf.getUserId())
                .orElseThrow(() -> new RuntimeException("Remitter not found for user " + bnf.getUserId()));

        // Pull a sandbox fixture as fallback if beneficiary has no bank/mobile data yet
        String countryCode = iso2OrThree(bnf.getCountry()).substring(0, Math.min(2, bnf.getCountry().length()));
        String payoutType = mapDeliveryToPayoutType(bnf.getDeliveryMethod());
        UsiTestAccountEntity fallback = usiTestAccountRepository
                .findByCountryCodeAndPayoutTypeAndIsActiveTrue(countryCode.toUpperCase(), payoutType)
                .stream().findFirst().orElse(null);

        // USI demands at least 2 name parts. Normalise + pad with "." if needed.
        String cleaned = ((bnf.getFullName() == null ? "" : bnf.getFullName()).replaceAll("[^a-zA-Z ]", "")).trim().replaceAll("\\s+", " ");
        String fullName = cleaned.contains(" ") ? cleaned : (cleaned.isEmpty() ? "Customer User" : cleaned + " Recipient");

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("group", "beneficiary");
        body.add("method", "createBeneficiary");
        body.add("name", fullName);
        body.add("organisation_type", "INDIVIDUAL");
        // USI requires non-empty address1 + city — fall back to the country capital when blank.
        String address = (bnf.getAddress() != null && !bnf.getAddress().isBlank())
                ? bnf.getAddress() : defaultCityFor(bnf.getCountry());
        body.add("address1", address);
        body.add("city", address);
        body.add("country", expandCountryName(bnf.getCountry()));
        body.add("mobile", normalisePhone(bnf.getMobileNumber(), iso2OrThree(bnf.getCountry())));

        if (bnf.getDeliveryMethod() == DeliveryMethod.BANK_DEPOSIT) {
            // USI Money's sandbox only routes to specific test banks per corridor
            // (Saudi National Bank for SA, First Abu Dhabi for AE, Garanti for TR, etc.).
            // If we have a sandbox fixture for this country, FORCE the bank name + branch
            // to it — partner's choice is preserved in our DB, just translated for USI's call.
            // Account number / IBAN remain whatever the partner entered.
            boolean forceSandboxBank = fallback != null;
            body.add("bank",             forceSandboxBank ? fallback.getBankName()
                                                          : value(bnf.getBankName(), ""));
            body.add("bank_branch",      forceSandboxBank ? fallback.getBankBranch()
                                                          : value(bnf.getSortCode(), bnf.getBankName()));
            body.add("bank_branch_city", value(bnf.getBranchCity(), fallback != null ? fallback.getBankBranchCity() : "Any Branch"));
            body.add("bank_branch_state",value(bnf.getBranchState(), fallback != null ? fallback.getBankBranchState() : "Any Branch"));
            body.add("account_number",   value(bnf.getAccountNumber(), fallback != null ? fallback.getAccountNumber() : ""));

            // IBAN required for non-Uganda/non-Egypt corridors
            String c = bnf.getCountry() != null ? bnf.getCountry().toUpperCase() : "";
            if (!c.contains("UGANDA") && !c.contains("EGYPT")) {
                body.add("benef_bank_iban", value(bnf.getIban(), fallback != null ? fallback.getIban() : ""));
            }
        }

        body.add("linked_member_id", remitter.getRemitterId());
        addCreds(body);

        String responseBody = postMultipart("/beneficiary/createBeneficiary", body);
        try {
            Document doc = parse(responseBody);
            String status = tag(doc, "status", 0);
            json.put("status", status);
            if ("SUCCESS".equalsIgnoreCase(status)) {
                String usiBnfId = tag(doc, "new_beneficiary_id", 0);
                usiBeneficiaryRepository.save(UsiBeneficiaryEntity.builder()
                        .localBeneficiaryId(String.valueOf(beneficiaryId))
                        .usiBeneficiaryId(usiBnfId)
                        .status("ACTIVE")
                        .build());
                json.put("usiBeneficiaryId", usiBnfId);
            } else {
                String msg = firstNonBlank(tag(doc, "error", 0), tag(doc, "message", 0));
                json.put("message", msg);

                // USI returns <existing_beneficiary_id>NNN</existing_beneficiary_id> on duplicates.
                // Link the existing one locally so subsequent calls work.
                String existingBnfId = tag(doc, "existing_beneficiary_id", 0);
                if (existingBnfId != null && !existingBnfId.isBlank()) {
                    usiBeneficiaryRepository.save(UsiBeneficiaryEntity.builder()
                            .localBeneficiaryId(String.valueOf(beneficiaryId))
                            .usiBeneficiaryId(existingBnfId)
                            .status("ACTIVE")
                            .build());
                    json.put("status", "EXISTS");
                    json.put("usiBeneficiaryId", existingBnfId);
                    json.put("message", "Existing USI beneficiary " + existingBnfId + " linked.");
                    log.info("USI beneficiary already existed — linked id {} to local beneficiary {}", existingBnfId, beneficiaryId);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI createBeneficiary response: " + e.getMessage(), e);
        }
        return json;
    }

    @Override
    public String searchBeneficiary(String usiBeneficiaryId) {
        guardEnabled();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("group", "beneficiary");
        body.add("method", "searchBeneficiary");
        body.add("beneficiary_id", usiBeneficiaryId);
        addCreds(body);
        return postMultipart("/beneficiary/searchBeneficiary", body);
    }

    // ── Transaction ──────────────────────────────────────────────────────

    @Override
    public Map<String, Object> createTransaction(String referenceNumber) {
        guardEnabled();
        Map<String, Object> json = new LinkedHashMap<>();

        TransactionEntity tx = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new RuntimeException("Local transaction not found: " + referenceNumber));

        // Auto-chain: ensure remitter + beneficiary exist on USI side before creating txn.
        // Matches the legacy 3-step flow but in one server call so the admin "Create on USI"
        // button does the right thing even when the user hasn't been pushed yet.
        UsiRemitterEntity remitter = usiRemitterRepository.findByUserId(tx.getSenderId()).orElse(null);
        if (remitter == null) {
            log.info("USI createTransaction({}) — auto-creating remitter for user {}", referenceNumber, tx.getSenderId());
            Map<String, Object> r = createRemitter(tx.getSenderId());
            if (!"SUCCESS".equalsIgnoreCase(String.valueOf(r.get("status"))) && !"EXISTS".equalsIgnoreCase(String.valueOf(r.get("status")))) {
                json.put("status", "FAILED");
                json.put("step", "createRemitter");
                json.put("message", String.valueOf(r.getOrDefault("message", "Remitter creation failed")));
                return json;
            }
            remitter = usiRemitterRepository.findByUserId(tx.getSenderId())
                    .orElseThrow(() -> new RuntimeException("Remitter row not persisted after create"));
        }

        UsiBeneficiaryEntity usiBnf = usiBeneficiaryRepository
                .findByLocalBeneficiaryId(String.valueOf(tx.getBeneficiaryId())).orElse(null);
        if (usiBnf == null) {
            log.info("USI createTransaction({}) — auto-creating beneficiary for local id {}", referenceNumber, tx.getBeneficiaryId());
            Map<String, Object> b = createBeneficiary(tx.getBeneficiaryId());
            if (!"SUCCESS".equalsIgnoreCase(String.valueOf(b.get("status"))) && !"EXISTS".equalsIgnoreCase(String.valueOf(b.get("status")))) {
                json.put("status", "FAILED");
                json.put("step", "createBeneficiary");
                json.put("message", String.valueOf(b.getOrDefault("message", "Beneficiary creation failed")));
                return json;
            }
            usiBnf = usiBeneficiaryRepository.findByLocalBeneficiaryId(String.valueOf(tx.getBeneficiaryId()))
                    .orElseThrow(() -> new RuntimeException("Beneficiary row not persisted after create"));
        }

        BeneficiaryEntity bnf = beneficiaryRepository.findById(tx.getBeneficiaryId())
                .orElseThrow(() -> new RuntimeException("Local beneficiary missing: " + tx.getBeneficiaryId()));

        UserEntity sender = userRepository.findById(tx.getSenderId())
                .orElseThrow(() -> new RuntimeException("Sender user missing: " + tx.getSenderId()));

        UsiTransactionEntity row = usiTransactionRepository.findByTransactionId(referenceNumber)
                .orElseGet(() -> UsiTransactionEntity.builder().transactionId(referenceNumber).build());

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("group", "transaction");
        body.add("method", "createTransaction");
        body.add("agent_trans_ref", tx.getReferenceNumber());
        body.add("remitter_id", remitter.getRemitterId());
        body.add("beneficiary_id", usiBnf.getUsiBeneficiaryId());
        body.add("destination_country", expandCountryName(bnf.getCountry()));

        // Pick the actual USI trans_type — auto-correct when the local delivery_method
        // doesn't match what USI supports for this destination country (e.g. UAE only does
        // bank transfer; sending Cash Collection there 5xx's with empty body).
        DeliveryMethod effective = chooseUsiDeliveryMethod(tx.getDeliveryMethod(), bnf);
        switch (effective) {
            case BANK_DEPOSIT -> body.add("trans_type", "Account");
            case MOBILE_WALLET -> {
                body.add("trans_type", "Mobile Transfer");
                body.add("benef_mobiletransfer_network", normaliseNetwork(bnf.getMobileProvider(), iso2OrThree(bnf.getCountry())));
                body.add("benef_mobiletransfer_number",
                        normalisePhone(bnf.getMobileNumber(), iso2OrThree(bnf.getCountry())));
            }
            case CASH_PICKUP -> {
                body.add("trans_type", "Cash Collection");
                UsiTestAccountEntity fallback = usiTestAccountRepository
                        .findByCountryCodeAndPayoutTypeAndIsActiveTrue(
                                iso2OrThree(bnf.getCountry()), "CASH_COLLECTION")
                        .stream().findFirst().orElse(null);
                if (fallback != null && fallback.getCollectionPoint() != null) {
                    body.add("collection_point", fallback.getCollectionPoint());
                    body.add("collection_point_bank", fallback.getCollectionPoint());
                    body.add("collection_point_address", fallback.getBankBranchCity() != null ? fallback.getBankBranchCity() : "");
                    body.add("collection_point_city", fallback.getBankBranchCity() != null ? fallback.getBankBranchCity() : "");
                }
            }
            default -> body.add("trans_type", "Account");
        }
        if (effective != tx.getDeliveryMethod()) {
            log.info("USI createTransaction({}) — auto-corrected delivery method {} → {} (country={})",
                    referenceNumber, tx.getDeliveryMethod(), effective, bnf.getCountry());
        }

        body.add("relation_to_remitter", "Family");
        body.add("sms_mobile", normalisePhone(sender.getPhone(), countryIso3(sender)));
        body.add("sms_confirmation", "f");
        body.add("sms_notification", "f");
        body.add("amount_type", "DESTINATION");
        body.add("amount_to_send", tx.getReceiveAmount().toPlainString());
        body.add("source_currency", tx.getSendCurrency());
        body.add("dest_currency", tx.getReceiveCurrency());
        body.add("purpose", "1");
        body.add("source_of_income", "1");
        body.add("payment_method", "74");
        body.add("service_level", "1");
        // USI requires a future delivery_date; +2 business days is a safe default that always
        // passes their "not in the past" validator even after slow polling cycles.
        body.add("delivery_date", java.time.LocalDate.now().plusDays(2).toString());
        addCreds(body);

        String responseBody = postMultipart("/transaction/createTransaction", body);
        if (responseBody == null || responseBody.isBlank()) {
            json.put("status", "FAILED");
            json.put("step", "createTransaction");
            json.put("message", "USI returned an empty response (likely a server-side validation failure). Check backend logs.");
            row.setErrorMessage("Empty response from USI sandbox");
            row.setStatus("create_failed");
            usiTransactionRepository.save(row);
            return json;
        }
        try {
            Document doc = parse(responseBody);
            String status = tag(doc, "status", 0);
            json.put("status", status);
            json.put("responseId", tag(doc, "responseId", 0));

            if (!"SUCCESS".equalsIgnoreCase(status)) {
                String msg = firstNonBlank(tag(doc, "error", 0), tag(doc, "message", 0));
                json.put("message", msg);
                row.setErrorMessage(msg);
                row.setStatus("create_failed");
                usiTransactionRepository.save(row);
                return json;
            }

            row.setTransSessionId(tag(doc, "trans_session_id", 0));
            row.setRemitterId(tag(doc, "remitter_id", 0));
            row.setRemitterName(tag(doc, "remitter_name", 0));
            row.setBeneficiaryId(tag(doc, "beneficiary_id", 0));
            row.setBeneficiaryName(tag(doc, "beneficiary_name", 0));
            row.setTransType(tag(doc, "trans_type", 0));
            row.setDestinationCountry(tag(doc, "destination_country", 0));
            row.setSourceCurrency(tag(doc, "source_currency", 0));
            row.setDestinationCurrency(tag(doc, "destination_currency", 0));
            row.setSourceTransferAmount(decimal(tag(doc, "source_transfer_amount", 0)));
            row.setDestinationAmount(decimal(tag(doc, "destination_amount", 0)));
            row.setRate(decimal(tag(doc, "rate", 0)));
            row.setCommission(decimal(tag(doc, "commission", 0)));
            row.setAgentFee(decimal(tag(doc, "agent_fee", 0)));
            row.setHqFee(decimal(tag(doc, "hq_fee", 0)));
            row.setTax(decimal(tag(doc, "tax", 0)));
            row.setRemitterPayAmount(decimal(tag(doc, "remitter_pay_amount", 0)));
            row.setAgentDeduction(decimal(tag(doc, "agent_deduction", 0)));
            row.setAgentToPayHq(decimal(tag(doc, "agent_to_pay_hq", 0)));

            String delDate = tag(doc, "delivery_date", 0);
            if (delDate != null && delDate.length() >= 19) {
                row.setDeliveryDate(LocalDateTime.parse(delDate.substring(0, 19)));
            }
            row.setPaymentToken(tag(doc, "payment_token", 0));
            row.setUsiStatus("initiated");
            row.setStatus("initiated");
            row.setPaymentStatus("funds received");
            usiTransactionRepository.save(row);

            json.put("transSessionId", row.getTransSessionId());
            json.put("paymentToken", row.getPaymentToken());
            json.put("message", "Transaction created successfully");
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI createTransaction response: " + e.getMessage(), e);
        }
        return json;
    }

    @Override
    public Map<String, Object> confirmTransaction(String referenceNumber) {
        guardEnabled();
        Map<String, Object> json = new LinkedHashMap<>();

        UsiTransactionEntity row = usiTransactionRepository.findByTransactionId(referenceNumber)
                .orElseThrow(() -> new RuntimeException("USI transaction not found: " + referenceNumber));

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("group", "transaction");
        body.add("method", "confirmTransaction");
        body.add("trans_session_id", row.getTransSessionId());
        addCreds(body);

        String responseBody = postMultipart("/transaction/confirmTransaction", body);
        try {
            Document doc = parse(responseBody);
            String status = tag(doc, "status", 0);
            json.put("status", status);
            json.put("responseId", tag(doc, "responseId", 0));
            if (!"SUCCESS".equalsIgnoreCase(status)) {
                String msg = firstNonBlank(tag(doc, "error", 0), tag(doc, "message", 0));
                json.put("message", msg);
                row.setErrorMessage(msg);
                row.setStatus("confirm_failed");
                usiTransactionRepository.save(row);
                return json;
            }

            String usiRef = tag(doc, "reference_number", 0);
            String paymentToken = tag(doc, "payment_token", 0);
            row.setReferenceNumber(usiRef);
            row.setPaymentToken(paymentToken);

            // Mirror onto the main transaction so customer / admin detail pages can show it.
            // Production USI sends a numeric payment_token (e.g. 11036122446378); sandbox sends only
            // the human reference_number (USIA…). Prefer the numeric token when present.
            String mainRef = (paymentToken != null && !paymentToken.isBlank()) ? paymentToken : usiRef;
            TransactionEntity mainTxn = transactionRepository.findByReferenceNumber(row.getTransactionId()).orElse(null);
            if (mainTxn != null && mainRef != null && !mainRef.isBlank()) {
                mainTxn.setPayoutReference(mainRef);
                transactionRepository.save(mainTxn);
            }

            row.setRate(decimal(tag(doc, "rate", 0)));
            row.setSourceTransferAmount(decimal(tag(doc, "source_transfer_amount", 0)));
            row.setDestinationAmount(decimal(tag(doc, "destination_amount", 0)));
            row.setCommission(decimal(tag(doc, "commission", 0)));
            row.setAgentFee(decimal(tag(doc, "agent_fee", 0)));
            row.setHqFee(decimal(tag(doc, "hq_fee", 0)));
            row.setTax(decimal(tag(doc, "tax", 0)));
            row.setRemitterPayAmount(decimal(tag(doc, "remitter_pay_amount", 0)));
            row.setAgentDeduction(decimal(tag(doc, "agent_deduction", 0)));
            row.setAgentToPayHq(decimal(tag(doc, "agent_to_pay_hq", 0)));
            row.setPaymentToken(tag(doc, "payment_token", 0));
            row.setUsiStatus(tag(doc, "status", 1));
            row.setStatus("sent for pay");
            row.setPaymentStatus("funds received");

            String delDate = tag(doc, "delivery_date", 0);
            if (delDate != null && delDate.length() >= 19) {
                try { row.setDeliveryDate(OffsetDateTime.parse(delDate).toLocalDateTime()); } catch (Exception ignore) {}
            }
            usiTransactionRepository.save(row);

            json.put("referenceNumber", row.getReferenceNumber());
            json.put("paymentToken", row.getPaymentToken());
            json.put("usiStatus", row.getUsiStatus());
            json.put("message", "Transaction confirmed");
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI confirmTransaction response: " + e.getMessage(), e);
        }
        return json;
    }

    @Override
    public Map<String, Object> getTransactionStatus(String referenceNumber) {
        guardEnabled();
        Map<String, Object> json = new HashMap<>();

        UsiTransactionEntity row = usiTransactionRepository.findByTransactionId(referenceNumber)
                .orElseThrow(() -> new RuntimeException("USI transaction not found: " + referenceNumber));

        if (row.getReferenceNumber() == null) {
            json.put("status", "SKIPPED");
            json.put("message", "Transaction has no USI reference yet");
            return json;
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("trans_ref", row.getReferenceNumber());
        addCreds(body);

        String responseBody = postMultipart("/transaction/getTransactionStatus", body);
        try {
            Document doc = parse(responseBody);
            String apiStatus = tag(doc, "status", 0);
            if (!"SUCCESS".equalsIgnoreCase(apiStatus)) {
                json.put("status", "FAILED");
                return json;
            }
            String txnStatus = tag(doc, "status", 1);
            json.put("usiStatus", txnStatus);

            String errorReason = tagOrNull(doc, "error_reason");
            String errorDetails = tagOrNull(doc, "error_details");
            String deletedReason = tagOrNull(doc, "deleted_reason");
            String msg = String.join(" ",
                    errorReason != null ? errorReason : "",
                    errorDetails != null ? errorDetails : "",
                    deletedReason != null ? deletedReason : "").trim();

            if ("PROCESSED".equalsIgnoreCase(txnStatus)) {
                row.setStatus("paid");
                row.setPaymentStatus("funds received");
                json.put("message", "Transaction PAID");
            } else if ("ABORTED".equalsIgnoreCase(txnStatus)) {
                row.setStatus("cancel_requested");
                row.setErrorMessage(msg);
                json.put("message", "Cancellation requested: " + msg);
            } else if ("DELETED".equalsIgnoreCase(txnStatus)) {
                row.setStatus("cancelled");
                row.setPaymentStatus("FAILED");
                row.setErrorMessage(msg);
                json.put("message", "Transaction CANCELLED: " + msg);
            } else if ("ERROR".equalsIgnoreCase(txnStatus)) {
                row.setStatus("failed");
                row.setPaymentStatus("FAILED");
                row.setErrorMessage(msg.isBlank() ? "USI reported ERROR (no reason)" : msg);
                json.put("message", "Transaction FAILED on USI: " + msg);
            } else if ("AGENT_OK".equalsIgnoreCase(txnStatus)) {
                row.setStatus("awaiting_compliance");
                json.put("message", "Awaiting USI compliance review");
            } else if ("HQ_OK".equalsIgnoreCase(txnStatus)) {
                // Already known intermediate — keep "sent for pay"; USI will flip it next cycle.
                json.put("message", "USI HQ accepted — awaiting payout: " + txnStatus);
            } else {
                json.put("message", "Transaction in progress: " + txnStatus);
            }
            row.setUsiStatus(txnStatus);
            usiTransactionRepository.save(row);
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI getTransactionStatus response: " + e.getMessage(), e);
        }
        return json;
    }

    @Override
    public List<Map<String, Object>> createMultipleTransactions(List<String> refs) {
        return bulk(refs, this::createTransaction);
    }

    @Override
    public List<Map<String, Object>> confirmMultipleTransactions(List<String> refs) {
        return bulk(refs, this::confirmTransaction);
    }

    @Override
    public List<Map<String, Object>> getMultipleTransactionStatus(List<String> refs) {
        return bulk(refs, this::getTransactionStatus);
    }

    // ── Collection points ─────────────────────────────────────────────────

    @Override
    public List<CollectionPointResponse> getCollectionPoints(String countryName) {
        guardEnabled();
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("destination_country", countryName);
        addCreds(body);

        String responseBody = postMultipart("/collectionPoint/getCollectionPoints", body);
        try {
            Document doc = parse(responseBody);
            String status = tag(doc, "status", 0);
            if (!"SUCCESS".equalsIgnoreCase(status)) {
                throw new RuntimeException("Failed to fetch collection points: " + status);
            }
            NodeList nodes = doc.getElementsByTagName("collection_point");
            List<CollectionPointResponse> out = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                CollectionPointResponse dto = new CollectionPointResponse();
                dto.setCollectionId(getElementText(el, "collection_id"));
                dto.setName(getElementText(el, "name"));
                dto.setBank(getElementText(el, "bank"));
                dto.setDeliveryBank(getElementText(el, "delivery_bank"));
                dto.setAddress(getElementText(el, "address"));
                dto.setCity(getElementText(el, "city"));
                dto.setState(getElementText(el, "state"));
                dto.setCountryId(getElementText(el, "country_id"));
                dto.setCode(getElementText(el, "code"));
                dto.setTelephone(getElementText(el, "telephone"));
                dto.setEmail(getElementText(el, "email"));
                dto.setContactPerson(getElementText(el, "contact_person"));
                out.add(dto);
            }
            return out;
        } catch (Exception e) {
            throw new RuntimeException("Error parsing USI getCollectionPoints response: " + e.getMessage(), e);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void guardEnabled() {
        if (!props.isEnabled()) {
            throw new IllegalStateException("USI Money integration is disabled. Set usi.enabled=true and configure usi.url / username / password / pin.");
        }
        if (props.getUrl() == null || props.getUrl().isBlank()) {
            throw new IllegalStateException("usi.url is not configured.");
        }
    }

    private void addCreds(MultiValueMap<String, Object> body) {
        body.add("username", props.getUsername());
        body.add("password", props.getPassword());
        body.add("pin", props.getPin());
    }

    private String postMultipart(String path, MultiValueMap<String, Object> body) {
        // Legacy USI uses application/x-www-form-urlencoded, not multipart/form-data.
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        // Coerce values to String so the form-urlencoded converter accepts them.
        MultiValueMap<String, String> formBody = new LinkedMultiValueMap<>();
        body.forEach((k, vs) -> vs.forEach(v -> formBody.add(k, v == null ? "" : String.valueOf(v))));

        HttpEntity<MultiValueMap<String, String>> req = new HttpEntity<>(formBody, headers);
        String url = props.getUrl() + path;

        // Mask credentials in logs but keep everything else for diagnosis.
        java.util.LinkedHashMap<String, Object> safeBody = new java.util.LinkedHashMap<>();
        formBody.forEach((k, v) -> safeBody.put(k, "password".equals(k) || "pin".equals(k) ? "***" : (v.size() == 1 ? v.get(0) : v)));
        log.info("USI {} POST body: {}", path, abbreviate(safeBody.toString()));

        try {
            ResponseEntity<String> resp = restTemplate.postForEntity(url, req, String.class);
            String b = resp.getBody();
            log.info("USI {} → {}", path, abbreviate(b));
            return b == null ? "" : b;
        } catch (HttpServerErrorException ex) {
            String b = ex.getResponseBodyAsString();
            log.warn("USI {} 5xx → status={} body={}", path, ex.getStatusCode(), abbreviate(b));
            return b == null ? "" : b;
        } catch (org.springframework.web.client.HttpClientErrorException ex) {
            String b = ex.getResponseBodyAsString();
            log.warn("USI {} 4xx → status={} body={}", path, ex.getStatusCode(), abbreviate(b));
            return b == null ? "" : b;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }

    private static String abbreviate(String s) {
        if (s == null) return "<null>";
        return s.length() > 600 ? s.substring(0, 600) + "…" : s;
    }

    private Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder b = factory.newDocumentBuilder();
        return b.parse(new ByteArrayInputStream(xml.getBytes()));
    }

    private String tag(Document doc, String name, int index) {
        Node n = doc.getElementsByTagName(name).item(index);
        return n != null ? n.getTextContent() : "";
    }

    private String tagOrNull(Document doc, String name) {
        Node n = doc.getElementsByTagName(name).item(0);
        return n != null ? n.getTextContent() : null;
    }

    private String getElementText(Element el, String tag) {
        NodeList nl = el.getElementsByTagName(tag);
        if (nl != null && nl.getLength() > 0) {
            Node n = nl.item(0);
            return n != null ? n.getTextContent() : null;
        }
        return null;
    }

    private BigDecimal decimal(String s) {
        if (s == null || s.isBlank()) return null;
        try { return new BigDecimal(s); } catch (NumberFormatException e) { return null; }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    private String value(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : (fallback != null ? fallback : "");
    }

    private String iso2OrThree(String country) {
        if (country == null) return "";
        String u = country.trim().toUpperCase(Locale.ROOT);
        // Accept already-coded inputs as-is; otherwise map common names.
        if (u.length() == 2 || u.length() == 3) return u;
        return switch (u) {
            case "UGANDA" -> "UG";
            case "TURKEY" -> "TR";
            case "EGYPT" -> "EG";
            case "QATAR" -> "QA";
            case "SAUDI ARABIA" -> "SA";
            case "UNITED ARAB EMIRATES", "UAE" -> "AE";
            default -> u.substring(0, Math.min(2, u.length()));
        };
    }

    private String countryIso3(UserEntity u) {
        String c = u.getCountryCode() != null ? u.getCountryCode()
                : (u.getCountry() != null ? u.getCountry() : "");
        return c.toUpperCase(Locale.ROOT);
    }

    /**
     * Pick the actual USI delivery method. Honour the user's local choice whenever USI
     * supports it for the destination country AND the beneficiary has the right data on
     * file. Auto-correct only when the local choice is unsupported (e.g. Cash to UAE).
     */
    private DeliveryMethod chooseUsiDeliveryMethod(DeliveryMethod local, BeneficiaryEntity bnf) {
        String iso2 = iso2OrThree(bnf.getCountry());
        if (iso2 == null || iso2.isBlank()) return local != null ? local : DeliveryMethod.BANK_DEPOSIT;

        boolean hasBank   = (bnf.getBankName() != null && !bnf.getBankName().isBlank())
                         || (bnf.getIban() != null && !bnf.getIban().isBlank())
                         || (bnf.getAccountNumber() != null && !bnf.getAccountNumber().isBlank());
        boolean hasMobile = bnf.getMobileNumber() != null && !bnf.getMobileNumber().isBlank()
                         && bnf.getMobileProvider() != null && !bnf.getMobileProvider().isBlank();

        // USI corridor capabilities (matches V532 payout_types config).
        // 'true' = USI supports this delivery method for the country.
        return switch (iso2) {
            // Bank-only corridors → always BANK_DEPOSIT
            case "AE", "SA", "TR", "EG" -> DeliveryMethod.BANK_DEPOSIT;
            // Cash-only corridor → always CASH_PICKUP
            case "QA"                   -> DeliveryMethod.CASH_PICKUP;
            // Multi-method corridor — honour local choice if it matches beneficiary data,
            // else fall back to whatever data is present.
            case "UG" -> {
                if (local == DeliveryMethod.MOBILE_WALLET && hasMobile) yield DeliveryMethod.MOBILE_WALLET;
                if (local == DeliveryMethod.BANK_DEPOSIT  && hasBank)   yield DeliveryMethod.BANK_DEPOSIT;
                if (local == DeliveryMethod.CASH_PICKUP)                yield DeliveryMethod.CASH_PICKUP;
                // Local choice can't be honoured — pick from available data
                if (hasMobile) yield DeliveryMethod.MOBILE_WALLET;
                if (hasBank)   yield DeliveryMethod.BANK_DEPOSIT;
                yield DeliveryMethod.BANK_DEPOSIT;
            }
            default -> local != null ? local : DeliveryMethod.BANK_DEPOSIT;
        };
    }

    /** Capital-city fallback when a beneficiary record has no address on file. */
    private String defaultCityFor(String country) {
        String iso = iso2OrThree(country);
        return switch (iso) {
            case "UG" -> "Kampala";
            case "TR" -> "Istanbul";
            case "EG" -> "Cairo";
            case "QA" -> "Doha";
            case "SA" -> "Riyadh";
            case "AE" -> "Dubai";
            default   -> "City";
        };
    }

    /** ISO-2 / ISO-3 / name → full country name expected by USI Money. */
    private String expandCountryName(String c) {
        if (c == null) return "";
        String u = c.trim().toUpperCase(Locale.ROOT);
        if (u.isEmpty()) return "";
        // If the caller already passed a multi-word country name, keep it.
        if (u.contains(" ") && u.length() > 4) return c;
        return switch (u) {
            case "UG", "UGA"                      -> "Uganda";
            case "TR", "TUR"                      -> "Turkey";
            case "EG", "EGY"                      -> "Egypt";
            case "QA", "QAT"                      -> "Qatar";
            case "SA", "SAU"                      -> "Saudi Arabia";
            case "AE", "ARE", "UAE"               -> "United Arab Emirates";
            case "GB", "GBR", "UK"                -> "United Kingdom";
            case "US", "USA"                      -> "United States";
            case "IN", "IND"                      -> "India";
            case "PK", "PAK"                      -> "Pakistan";
            case "NG", "NGA"                      -> "Nigeria";
            case "BD", "BGD"                      -> "Bangladesh";
            case "PH", "PHL"                      -> "Philippines";
            case "KE", "KEN"                      -> "Kenya";
            case "GH", "GHA"                      -> "Ghana";
            case "NP", "NPL"                      -> "Nepal";
            case "DE", "DEU"                      -> "Germany";
            case "AU", "AUS"                      -> "Australia";
            default                               -> c;
        };
    }

    /** Find the strongest identity document for a user (passport > licence > national-id). */
    private KycDocumentEntity pickPrimaryId(Long userId) {
        List<KycDocumentEntity> docs = kycDocumentRepository.findByUserId(userId);
        if (docs == null || docs.isEmpty()) return null;
        for (String preferred : new String[] {"PASSPORT", "DRIVING_LICENCE", "NATIONAL_ID"}) {
            for (KycDocumentEntity d : docs) {
                if (d.getDocumentType() != null && d.getDocumentType().name().equals(preferred)
                        && d.getDocumentNumber() != null && !d.getDocumentNumber().endsWith("_BACK")) {
                    return d;
                }
            }
        }
        return null;
    }

    /** Map our KycDocumentType enum value to the legacy USI id_type string. */
    private String mapKycToUsiIdType(String t) {
        if (t == null) return "Other";
        return switch (t) {
            case "PASSPORT"         -> "Passport";
            case "DRIVING_LICENCE"  -> "Driving_License";
            case "NATIONAL_ID"      -> "National_ID";
            default                 -> "Other";
        };
    }

    /** ISO country code → international dialing code. */
    private static final java.util.Map<String, String> DIAL = java.util.Map.ofEntries(
            java.util.Map.entry("GB", "44"), java.util.Map.entry("GBR", "44"),
            java.util.Map.entry("US", "1"),  java.util.Map.entry("USA", "1"),
            java.util.Map.entry("UG", "256"), java.util.Map.entry("UGA", "256"),
            java.util.Map.entry("TR", "90"), java.util.Map.entry("TUR", "90"),
            java.util.Map.entry("EG", "20"), java.util.Map.entry("EGY", "20"),
            java.util.Map.entry("QA", "974"), java.util.Map.entry("QAT", "974"),
            java.util.Map.entry("SA", "966"), java.util.Map.entry("SAU", "966"),
            java.util.Map.entry("AE", "971"), java.util.Map.entry("ARE", "971"),
            java.util.Map.entry("IN", "91"), java.util.Map.entry("IND", "91"),
            java.util.Map.entry("PK", "92"), java.util.Map.entry("PAK", "92"),
            java.util.Map.entry("NG", "234"), java.util.Map.entry("NGA", "234"),
            java.util.Map.entry("BD", "880"), java.util.Map.entry("BGD", "880"),
            java.util.Map.entry("PH", "63"), java.util.Map.entry("PHL", "63"),
            java.util.Map.entry("KE", "254"), java.util.Map.entry("KEN", "254"),
            java.util.Map.entry("GH", "233"), java.util.Map.entry("GHA", "233"),
            java.util.Map.entry("NP", "977"), java.util.Map.entry("NPL", "977"),
            java.util.Map.entry("DE", "49"), java.util.Map.entry("DEU", "49"),
            java.util.Map.entry("AU", "61"), java.util.Map.entry("AUS", "61")
    );

    /**
     * Map a mobile network name to the canonical form USI Money expects.
     * USI's Uganda corridor recognises "MTN Mobile Money" and "Airtel Money".
     * Inputs like "MTN", "Mtn", "AIRTEL", "Airtel Money UG" get normalised.
     */
    private String normaliseNetwork(String raw, String countryCode) {
        if (raw == null || raw.isBlank()) return "";
        String up = raw.trim().toUpperCase(Locale.ROOT);
        String iso2 = countryCode == null ? "" : countryCode.toUpperCase(Locale.ROOT);
        if ("UG".equals(iso2) || "UGA".equals(iso2)) {
            if (up.contains("MTN"))    return "MTN Mobile Money";
            if (up.contains("AIRTEL")) return "Airtel Money";
        }
        // Pass through whatever was supplied (already canonical from our mobile_money_services seed)
        return raw;
    }

    /**
     * Coerce a phone number into the USI-required "+&lt;dialing-code&gt;&lt;number&gt;" form.
     * Already-prefixed numbers ("+447…", "00447…") are normalised; bare locals get the
     * country's dialing code prepended.
     */
    private String normalisePhone(String raw, String countryCode) {
        if (raw == null || raw.isBlank()) return "";
        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) return digits;
        if (digits.startsWith("00")) return "+" + digits.substring(2);
        String code = countryCode == null ? "" : DIAL.getOrDefault(countryCode.toUpperCase(Locale.ROOT), "");
        if (code.isEmpty()) return digits.startsWith("+") ? digits : "+" + digits;
        // Strip a leading 0 the way most national-format numbers carry it.
        if (digits.startsWith("0")) digits = digits.substring(1);
        return "+" + code + digits;
    }

    private String mapDeliveryToPayoutType(DeliveryMethod dm) {
        if (dm == null) return "BANK_TRANSFER";
        return switch (dm) {
            case BANK_DEPOSIT -> "BANK_TRANSFER";
            case MOBILE_WALLET -> "MOBILE_MONEY";
            case CASH_PICKUP -> "CASH_COLLECTION";
            default -> "BANK_TRANSFER";
        };
    }

    private List<Map<String, Object>> bulk(List<String> refs, java.util.function.Function<String, Map<String, Object>> op) {
        List<Map<String, Object>> out = new ArrayList<>(refs.size());
        for (String ref : refs) {
            try {
                out.add(op.apply(ref));
            } catch (Exception e) {
                Map<String, Object> err = new HashMap<>();
                err.put("transactionId", ref);
                err.put("status", "FAILED");
                err.put("message", e.getMessage());
                out.add(err);
            }
        }
        return out;
    }
}

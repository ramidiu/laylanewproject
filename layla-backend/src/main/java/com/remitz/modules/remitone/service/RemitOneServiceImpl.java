package com.remitz.modules.remitone.service;

import com.remitz.common.enums.DeliveryMethod;
import com.remitz.common.enums.KycDocumentStatus;
import com.remitz.modules.auth.entity.UserEntity;
import com.remitz.modules.auth.repository.UserRepository;
import com.remitz.modules.fx.entity.CorridorEntity;
import com.remitz.modules.fx.repository.CorridorRepository;
import com.remitz.modules.remitone.entity.RemitOneTransactionEntity;
import com.remitz.modules.remitone.repository.RemitOneTransactionRepository;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.remitz.modules.user.entity.KycDocumentEntity;
import com.remitz.modules.user.repository.KycDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RemitOneServiceImpl implements RemitOneService {

    @Value("${remit-one.username:}")
    private String username;

    @Value("${remit-one.password:}")
    private String password;

    @Value("${remit-one.pin:}")
    private String pin;

    @Value("${remit-one.url:https://remitby.remitone.com/universalsecurities/ws/compliance/insertTransactionForCompliance}")
    private String apiUrl;

    @Value("${remit-one.agent-name:Universal Securities}")
    private String agentName;

    @Value("${remit-one.enabled:false}")
    private boolean enabled;

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final CorridorRepository corridorRepository;
    private final KycDocumentRepository kycDocumentRepository;
    private final RemitOneTransactionRepository remitOneRepository;

    private static final String SUDAN_CURRENCY = "SDG";
    private static final String SUDAN_COUNTRY   = "SD";

    @Override
    @Async
    public void triggerCompliance(Long transactionId) {
        if (!enabled) {
            log.debug("RemitOne disabled — skipping compliance call for txn {}", transactionId);
            return;
        }

        log.info("========== RemitOne Compliance START | txnId={} ==========", transactionId);
        try {
            TransactionEntity tx = transactionRepository.findById(transactionId).orElse(null);
            if (tx == null) {
                log.warn("RemitOne: TransactionEntity not found | txnId={}", transactionId);
                return;
            }

            // Only process Sudan SDG transactions
            if (!SUDAN_CURRENCY.equalsIgnoreCase(tx.getReceiveCurrency())) {
                log.info("RemitOne: skipping — receiveCurrency={} not SDG", tx.getReceiveCurrency());
                return;
            }

            // Idempotency: skip if already SUCCESS
            Optional<RemitOneTransactionEntity> existing = remitOneRepository.findByTransactionId(
                    tx.getReferenceNumber());
            if (existing.isPresent() && "SUCCESS".equalsIgnoreCase(existing.get().getPaymentStatus())) {
                log.info("RemitOne: already SUCCESS for txn {}", tx.getReferenceNumber());
                return;
            }

            // Load sender
            UserEntity sender = userRepository.findById(tx.getSenderId()).orElse(null);
            if (sender == null) {
                log.warn("RemitOne: sender not found | senderId={}", tx.getSenderId());
                return;
            }

            // Load beneficiary
            BeneficiaryEntity beneficiary = beneficiaryRepository.findById(tx.getBeneficiaryId()).orElse(null);
            if (beneficiary == null) {
                log.warn("RemitOne: beneficiary not found | beneficiaryId={}", tx.getBeneficiaryId());
                return;
            }

            // Load corridor for country ISO
            CorridorEntity corridor = corridorRepository.findById(tx.getCorridorId()).orElse(null);
            String originatingCountry = corridor != null ? corridor.getSendCountry() : "GBR";
            String destinationCountry = corridor != null ? corridor.getReceiveCountry() : SUDAN_COUNTRY;

            // Load latest approved KYC document for ID details
            KycDocumentEntity idDoc = getLatestApprovedDoc(tx.getSenderId());

            // Determine delivery / trans type
            String transType = mapDeliveryMethod(tx.getDeliveryMethod());

            // Build request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("username", username);
            params.add("password", password);
            params.add("pin", pin);
            params.add("agent_name", agentName);
            params.add("trans_type", transType);
            params.add("agent_trans_ref", tx.getReferenceNumber());
            params.add("originating_country", originatingCountry);
            params.add("currency", tx.getSendCurrency());
            params.add("source_amount", tx.getSendAmount().toPlainString());
            params.add("destination_country", destinationCountry);
            params.add("dest_currency", tx.getReceiveCurrency());
            params.add("remitt_name", buildFullName(sender.getFirstName(), sender.getLastName()));
            params.add("commission", "0");

            // Sender address
            String address = buildAddress(sender.getAddressLine1(), sender.getAddressLine2());
            params.add("remitt_address1", address);

            String city = sanitizeCity(sender.getCity());
            params.add("remitt_city", city);
            params.add("remitt_postcode", nvl(sender.getPostcode(), ""));
            params.add("remitt_country_of_birth", originatingCountry);
            params.add("remitt_nationality", originatingCountry);
            params.add("remitt_dob", sender.getDateOfBirth() != null ? sender.getDateOfBirth().toString() : "");
            params.add("remitt_gender", "MALE");

            // KYC / ID document
            if (idDoc != null) {
                params.add("remitt_id_type", idDoc.getDocumentType().name());
                params.add("remitt_id_details", nvl(idDoc.getDocumentNumber(), ""));
                params.add("remitt_id_expiry", idDoc.getExpiryDate() != null ? idDoc.getExpiryDate().toString() : "");
            } else {
                params.add("remitt_id_type", "");
                params.add("remitt_id_details", "");
                params.add("remitt_id_expiry", "");
            }

            params.add("purpose", nvl(tx.getNotes(), "Family Support"));
            params.add("source_of_income", "Employment");

            // Beneficiary
            params.add("benef_name", nvl(beneficiary.getFullName(), ""));
            params.add("benef_address", nvl(beneficiary.getAddress(), ""));
            params.add("benef_city", nvl(beneficiary.getAddress(), ""));
            params.add("payment_method", "11");

            if (tx.getDeliveryMethod() == DeliveryMethod.BANK_DEPOSIT) {
                params.add("benef_ac", nvl(beneficiary.getAccountNumber(), ""));
                params.add("benef_bank", nvl(beneficiary.getBankName(), ""));
            }

            HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(params, headers);

            log.info("RemitOne: calling API for txn={}", tx.getReferenceNumber());
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, requestEntity, String.class);

            log.info("RemitOne: HTTP {} | body={}", response.getStatusCode(), response.getBody());

            String xmlBody = response.getBody();
            if (xmlBody == null || xmlBody.isBlank()) {
                log.error("RemitOne: empty response for txn={}", tx.getReferenceNumber());
                return;
            }

            RemitOneTransactionEntity record = parseXml(xmlBody, tx.getReferenceNumber());
            record.setRawResponse(xmlBody);
            record.setCreatedOn(LocalDateTime.now());
            remitOneRepository.save(record);

            log.info("RemitOne: saved | status={} | txn={}", record.getPaymentStatus(), tx.getReferenceNumber());

        } catch (Exception ex) {
            log.error("RemitOne: EXCEPTION for txnId={}", transactionId, ex);
        }
        log.info("========== RemitOne Compliance END | txnId={} ==========", transactionId);
    }

    private String mapDeliveryMethod(DeliveryMethod method) {
        if (method == null) return "Account";
        return switch (method) {
            case CASH_PICKUP -> "Cash Collection";
            case MOBILE_WALLET -> "Mobile Transfer";
            default -> "Account";
        };
    }

    private KycDocumentEntity getLatestApprovedDoc(Long userId) {
        List<KycDocumentEntity> docs = kycDocumentRepository.findByUserIdAndStatus(userId, KycDocumentStatus.APPROVED);
        return docs.stream()
                .max(Comparator.comparing(d -> d.getVerifiedAt() != null ? d.getVerifiedAt() : LocalDateTime.MIN))
                .orElse(null);
    }

    private String buildFullName(String first, String last) {
        return ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
    }

    private String buildAddress(String line1, String line2) {
        String raw;
        if (line1 != null && !line1.isBlank() && line2 != null && !line2.isBlank()) {
            raw = line1 + " " + line2;
        } else if (line1 != null && !line1.isBlank()) {
            raw = line1;
        } else if (line2 != null && !line2.isBlank()) {
            raw = line2;
        } else {
            throw new IllegalArgumentException("Remitter address is required for RemitOne compliance");
        }
        return raw.replaceAll("[^a-zA-Z0-9 ]", "").replaceAll("\\s+", " ").trim();
    }

    private String sanitizeCity(String city) {
        if (city == null || city.isBlank()) {
            throw new IllegalArgumentException("Remitter city is required for RemitOne compliance");
        }
        return city.replaceAll("[^a-zA-Z ]", "").replaceAll("\\s+", " ").trim();
    }

    private String nvl(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }

    private RemitOneTransactionEntity parseXml(String xml, String transactionId) throws Exception {
        xml = sanitizeXml(xml);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        RemitOneTransactionEntity entity = remitOneRepository.findByTransactionId(transactionId)
                .orElseGet(() -> RemitOneTransactionEntity.builder().transactionId(transactionId).build());

        entity.setPaymentStatus(getTag(doc, "status"));
        entity.setMessage(getTag(doc, "message"));
        entity.setTransSessionId(getTag(doc, "trans_session_id"));
        entity.setTransType(getTag(doc, "trans_type"));
        entity.setRemitterId(getTag(doc, "remitter_id"));
        entity.setRemitterName(getTag(doc, "remitter_name"));
        entity.setBeneficiaryId(getTag(doc, "beneficiary_id"));
        entity.setBeneficiaryName(getTag(doc, "beneficiary_name"));
        entity.setDestinationCountry(getTag(doc, "destination_country"));
        entity.setSourceCurrency(getTag(doc, "source_currency"));
        entity.setDestinationCurrency(getTag(doc, "destination_currency"));
        entity.setSourceAmount(getDecimal(doc, "source_amount"));
        entity.setRate(getDecimal(doc, "rate"));
        entity.setDestinationAmount(getDecimal(doc, "destination_amount"));
        entity.setCommission(getDecimal(doc, "commission"));
        entity.setTax(getDecimal(doc, "tax"));
        entity.setRemitterPayAmount(getDecimal(doc, "remitter_pay_amount"));
        entity.setCommentsToBeneficiary(getTag(doc, "comments_to_beneficiary"));
        return entity;
    }

    private String getTag(Document doc, String tag) {
        NodeList list = doc.getElementsByTagName(tag);
        return (list != null && list.getLength() > 0) ? list.item(0).getTextContent() : null;
    }

    private BigDecimal getDecimal(Document doc, String tag) {
        try {
            String val = getTag(doc, tag);
            return (val != null && !val.isBlank()) ? new BigDecimal(val) : BigDecimal.ZERO;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private String sanitizeXml(String xml) {
        if (xml == null) return null;
        int firstTag = xml.indexOf("<");
        if (firstTag > 0) xml = xml.substring(firstTag);
        xml = xml.replaceAll("(?s)<\\?xml.*?\\?>", "");
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + xml.trim();
    }
}

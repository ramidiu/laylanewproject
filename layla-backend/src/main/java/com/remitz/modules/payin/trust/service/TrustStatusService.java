package com.remitz.modules.payin.trust.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.remitz.common.exception.RemitzException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trust Payments Webservices TRANSACTIONQUERY — READ-ONLY status lookups.
 *
 * <p>Performs a server-side TRANSACTIONQUERY against Trust's Webservices JSON API to report the latest
 * known state of a transaction by our {@code orderreference}. It is completely independent of the live
 * STPP hosted-page payment flow and the webhook/callback handler: it does not write to our database and
 * does not change any transaction status. Credentials live only on the server and are never returned to
 * the client.
 *
 * <p>Request/response format per Trust's official spec:
 * <a href="https://help.trustpayments.com/hc/en-us/articles/4402764105617-TRANSACTIONQUERY">TRANSACTIONQUERY</a>.
 * The {@code sitereference} filter must be accompanied by at least one other filter — the
 * {@code orderreference} satisfies that requirement.
 */
@Service
@Slf4j
public class TrustStatusService {

    private final String wsUrl;
    private final String username;
    private final String password;
    private final String siteReference;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TrustStatusService(
            @Value("${app.trust.ws.url:https://webservices.securetrading.net/json/}") String wsUrl,
            @Value("${app.trust.ws.username:}") String username,
            @Value("${app.trust.ws.password:}") String password,
            @Value("${app.trust.ws.site-reference:laylalondo147951}") String siteReference,
            @Value("${app.trust.ws.timeout-ms:10000}") int timeoutMs) {
        this.wsUrl = wsUrl;
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.siteReference = siteReference == null ? "" : siteReference.trim();

        // Bounded connect/read timeouts so a slow Trust endpoint can never hang a request thread.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        this.restTemplate = new RestTemplate(factory);
    }

    /**
     * Run a TRANSACTIONQUERY for the given orderreference against the live site reference and return the
     * latest matching record's key fields. Never mutates local state.
     */
    public Map<String, Object> queryByOrderReference(String orderReference) {
        if (orderReference == null || orderReference.isBlank()) {
            throw new RemitzException("orderreference is required", HttpStatus.BAD_REQUEST);
        }
        if (username.isEmpty() || password.isEmpty()) {
            throw new RemitzException(
                    "Trust Webservices credentials are not configured (set TRUST_WS_USERNAME and TRUST_WS_PASSWORD).",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        String requestBody = buildQueryJson(orderReference.trim());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(username, password);   // credentials stay server-side

        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(wsUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), String.class);
        } catch (Exception e) {
            log.error("Trust TRANSACTIONQUERY call failed for orderref={}: {}", orderReference, e.getMessage());
            throw new RemitzException("Trust Payments status query failed: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY);
        }

        return parseLatestRecord(response.getBody(), orderReference);
    }

    /** True when Webservices credentials are present, so a TRANSACTIONQUERY can actually be made. */
    public boolean isConfigured() {
        return !username.isEmpty() && !password.isEmpty();
    }

    /**
     * Read-only settlement view of a Trust transaction, resolved by its {@code transactionreference}.
     * Contains ONLY non-sensitive fields safe to return to an authorised caller — no credentials.
     */
    public record SettlementView(
            int found,
            String transactionReference,
            String requestTypeDescription,
            String transactionStatus,   // Trust's own 'status' field on the record
            String settleStatus,        // raw Trust settlestatus, preserved verbatim
            String settlementState,     // mapped: SETTLED / SCHEDULED / FAILED / UNKNOWN / NOT_FOUND
            String settlementDueDate,
            String baseAmount) {        // Trust baseamount in MINOR units (e.g. "12080" = £120.80)
    }

    /**
     * Run a TRANSACTIONQUERY filtered by {@code sitereference} + {@code transactionreference} and return
     * the settlement view of the preferred (AUTH) record. READ-ONLY — never writes to our database and
     * never changes any transaction status. Credentials stay server-side.
     */
    public SettlementView queryByTransactionReference(String transactionReference) {
        if (transactionReference == null || transactionReference.isBlank()) {
            throw new RemitzException("transactionreference is required", HttpStatus.BAD_REQUEST);
        }
        if (username.isEmpty() || password.isEmpty()) {
            throw new RemitzException(
                    "Trust Webservices credentials are not configured (set TRUST_PAYMENTS_USERNAME and TRUST_PAYMENTS_PASSWORD).",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        String requestBody = buildTxnRefQueryJson(transactionReference.trim());
        String rawBody = execute(requestBody, transactionReference.trim());
        return parseSettlementView(rawBody, transactionReference.trim());
    }

    /** POST a prepared TRANSACTIONQUERY body to Trust with server-side Basic Auth; return the raw body. */
    private String execute(String requestBody, String label) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBasicAuth(username, password);   // credentials stay server-side, never logged
        try {
            ResponseEntity<String> response = restTemplate.exchange(wsUrl, HttpMethod.POST,
                    new HttpEntity<>(requestBody, headers), String.class);
            return response.getBody();
        } catch (Exception e) {
            // Log the message only (never headers/credentials/body).
            log.error("Trust TRANSACTIONQUERY call failed for txnref={}: {}", label, e.getMessage());
            throw new RemitzException("Trust Payments status query failed", HttpStatus.BAD_GATEWAY);
        }
    }

    /** Build a TRANSACTIONQUERY request JSON filtered by sitereference + transactionreference. */
    private String buildTxnRefQueryJson(String transactionReference) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("alias", username);
        root.put("version", "1.00");
        ArrayNode requestArr = root.putArray("request");
        ObjectNode req = requestArr.addObject();
        req.putArray("requesttypedescriptions").add("TRANSACTIONQUERY");
        ObjectNode filter = req.putObject("filter");
        filter.putArray("sitereference").addObject().put("value", siteReference);
        filter.putArray("transactionreference").addObject().put("value", transactionReference);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RemitzException("Failed to build Trust query request", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Parse response, pick the preferred (AUTH) record, and map it to a safe SettlementView. */
    private SettlementView parseSettlementView(String rawBody, String transactionReference) {
        try {
            JsonNode body = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            JsonNode response0 = body.path("response").path(0);

            String respErrorCode = response0.path("errorcode").asText("");
            if (!respErrorCode.isEmpty() && !"0".equals(respErrorCode)) {
                String msg = response0.path("errormessage").asText("Query error");
                log.warn("Trust TRANSACTIONQUERY errorcode={} msg={} for txnref={}", respErrorCode, msg, transactionReference);
                throw new RemitzException("Trust query error (" + respErrorCode + ")", HttpStatus.BAD_GATEWAY);
            }

            JsonNode records = response0.path("records");
            int found = response0.path("found").asInt(records.isArray() ? records.size() : 0);

            if (!records.isArray() || records.isEmpty()) {
                return new SettlementView(found, transactionReference, null, null, null, "NOT_FOUND", null, null);
            }

            JsonNode preferred = preferAuthRecord(records);
            String settleStatus = text(preferred, "settlestatus");
            return new SettlementView(
                    found,
                    firstNonNull(text(preferred, "transactionreference"), transactionReference),
                    text(preferred, "requesttypedescription"),
                    text(preferred, "status"),
                    settleStatus,
                    mapSettlementState(settleStatus),
                    text(preferred, "settleduedate"),
                    text(preferred, "baseamount"));
        } catch (RemitzException re) {
            throw re;
        } catch (Exception e) {
            log.error("Failed to parse Trust TRANSACTIONQUERY response for txnref={}: {}", transactionReference, e.getMessage());
            throw new RemitzException("Failed to parse Trust Payments response", HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Prefer the AUTH record — settlement lifecycle (settlestatus/settleduedate) lives on the AUTH.
     * Falls back to the last record (Trust returns records in chronological order) if none is an AUTH.
     */
    private static JsonNode preferAuthRecord(JsonNode records) {
        for (int i = records.size() - 1; i >= 0; i--) {
            JsonNode r = records.get(i);
            if ("AUTH".equalsIgnoreCase(text(r, "requesttypedescription"))) {
                return r;
            }
        }
        return records.get(records.size() - 1);
    }

    /**
     * Map Trust's {@code settlestatus} to a settlement state, using the same conventions as the live
     * card flow (see TrustPaymentController): 100 = settled; 0/1/10 = scheduled to settle; 2/3 =
     * suspended/cancelled; anything else = unknown. The raw settlestatus is preserved separately.
     */
    private static String mapSettlementState(String settleStatus) {
        if (settleStatus == null || settleStatus.isBlank()) return "UNKNOWN";
        switch (settleStatus.trim()) {
            case "100":                       return "SETTLED";
            case "0":  case "1":  case "10":  return "SCHEDULED";
            case "2":  case "3":              return "FAILED";
            default:                          return "UNKNOWN";
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    /**
     * Convert Trust's {@code baseamount} (MINOR units, e.g. "12080") to major units (e.g. 120.80).
     * Returns null when absent or unparseable, so callers can fail-closed on amount verification.
     */
    public static BigDecimal baseAmountToMajor(String baseAmount) {
        if (baseAmount == null || baseAmount.isBlank()) return null;
        try {
            return new BigDecimal(baseAmount.trim()).movePointLeft(2);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Build the official TRANSACTIONQUERY request JSON: alias + version + request[filter]. */
    private String buildQueryJson(String orderReference) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("alias", username);
        root.put("version", "1.00");
        ArrayNode requestArr = root.putArray("request");
        ObjectNode req = requestArr.addObject();
        req.putArray("requesttypedescriptions").add("TRANSACTIONQUERY");
        ObjectNode filter = req.putObject("filter");
        filter.putArray("sitereference").addObject().put("value", siteReference);
        filter.putArray("orderreference").addObject().put("value", orderReference);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new RemitzException("Failed to build Trust query request", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Extract response[0].records[], returning the latest record's key fields as a clean map. */
    private Map<String, Object> parseLatestRecord(String rawBody, String orderReference) {
        try {
            JsonNode body = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            JsonNode response0 = body.path("response").path(0);

            String respErrorCode = response0.path("errorcode").asText("");
            if (!respErrorCode.isEmpty() && !"0".equals(respErrorCode)) {
                String msg = response0.path("errormessage").asText("Query error");
                log.warn("Trust TRANSACTIONQUERY errorcode={} msg={} for orderref={}", respErrorCode, msg, orderReference);
                throw new RemitzException("Trust query error (" + respErrorCode + "): " + msg, HttpStatus.BAD_GATEWAY);
            }

            JsonNode records = response0.path("records");
            int found = response0.path("found").asInt(records.isArray() ? records.size() : 0);

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("found", found);
            out.put("orderreference", orderReference);

            if (!records.isArray() || records.isEmpty()) {
                out.put("record", null);
                return out;
            }

            // "Latest" = the last record (Trust returns records in chronological order).
            JsonNode latest = records.get(records.size() - 1);
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("transactionreference", text(latest, "transactionreference"));
            record.put("orderreference", text(latest, "orderreference"));
            record.put("baseamount", text(latest, "baseamount"));
            record.put("currencyiso3a", text(latest, "currencyiso3a"));
            record.put("errorcode", text(latest, "errorcode"));
            record.put("settlestatus", text(latest, "settlestatus"));
            record.put("settlebaseamount", text(latest, "settlebaseamount"));
            record.put("settledtimestamp", text(latest, "settledtimestamp"));
            record.put("requesttypedescription", text(latest, "requesttypedescription"));
            record.put("livestatus", text(latest, "livestatus"));
            out.put("record", record);
            return out;
        } catch (RemitzException re) {
            throw re;
        } catch (Exception e) {
            log.error("Failed to parse Trust TRANSACTIONQUERY response for orderref={}: {}", orderReference, e.getMessage());
            throw new RemitzException("Failed to parse Trust Payments response", HttpStatus.BAD_GATEWAY);
        }
    }

    /** Null-safe field read — returns null (rather than "") when Trust omits a field. */
    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}

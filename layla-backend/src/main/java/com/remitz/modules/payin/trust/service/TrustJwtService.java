package com.remitz.modules.payin.trust.service;

import com.remitz.modules.transaction.entity.TransactionEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Trust Payments TRU Connect (JavaScript Library / st.js) JWT integration.
 *
 * <p>This is the SECURE replacement for the legacy unsigned form POST. The amount is placed inside a
 * JWT that is <b>signed server-side (HS256)</b> with the webservices shared secret, so the customer's
 * browser cannot tamper with it (the root cause of the 0.05-vs-510 incident). The result Trust returns
 * is itself a signed JWT which we verify with the same secret before trusting any of its values.
 *
 * <h2>Request JWT payload (per Trust's "Configure the JSON Web Token" spec)</h2>
 * <pre>
 * { "iss": &lt;webservices username&gt;, "iat": &lt;now&gt;, "payload": {
 *     "requesttypedescriptions": ["THREEDQUERY","AUTH"],   // 3-D Secure then authorise
 *     "accounttypedescription": "ECOM",
 *     "sitereference": &lt;site ref&gt;,
 *     "currencyiso3a": &lt;GBP&gt;,
 *     "baseamount": &lt;minor units, e.g. 1050&gt;,             // SERVER-authoritative, from the txn
 *     "orderreference": &lt;our TXN reference&gt; } }
 * </pre>
 *
 * <p><b>Feature-flagged:</b> {@code app.trust.jwt.enabled=false} by default, and both credentials are
 * empty until configured, so this stays inert (throws {@link IllegalStateException} if used) and the
 * live flow is untouched until the secret/username are set and sandbox-tested.
 *
 * <p>HS256 key is built with {@link SecretKeySpec} directly (not {@code Keys.hmacShaKeyFor}) so a
 * Trust secret shorter than 32 bytes is accepted — Trust, not us, controls the secret length.
 */
@Service
@Slf4j
public class TrustJwtService {

    private final boolean enabled;
    private final String username;
    private final String secret;
    private final String siteReference;
    private final int liveStatus;

    public TrustJwtService(
            @Value("${app.trust.jwt.enabled:false}") boolean enabled,
            @Value("${app.trust.jwt.username:}") String username,
            @Value("${app.trust.jwt.secret:}") String secret,
            @Value("${app.trust.jwt.site-reference:}") String siteReference,
            @Value("${app.trust.jwt.live-status:0}") int liveStatus) {
        this.enabled = enabled;
        this.username = username == null ? "" : username.trim();
        this.secret = secret == null ? "" : secret;
        this.siteReference = siteReference == null ? "" : siteReference.trim();
        this.liveStatus = liveStatus;
    }

    public boolean isEnabled() {
        return enabled && !username.isEmpty() && !secret.isEmpty() && !siteReference.isEmpty();
    }

    public String getSiteReference() { return siteReference; }
    public int getLiveStatus() { return liveStatus; }

    /** The AUTH outcome extracted from a verified Trust response JWT. */
    public record AuthResult(String errorCode, String settleStatus, String baseAmount,
                             String transactionReference, String orderReference) {}

    private SecretKey key() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private void requireEnabled() {
        if (!isEnabled()) {
            throw new IllegalStateException("Trust JWT integration is not configured "
                    + "(set app.trust.jwt.enabled=true with username/secret/site-reference).");
        }
    }

    /**
     * Build the signed request JWT for a transaction. The amount is taken from the transaction
     * (total debit, falling back to send amount) — never from the client — so it is authoritative.
     */
    public String createRequestJwt(TransactionEntity tx) {
        requireEnabled();
        BigDecimal amount = tx.getTotalDebitAmount() != null ? tx.getTotalDebitAmount() : tx.getSendAmount();
        if (amount == null) {
            throw new IllegalStateException("Transaction " + tx.getReferenceNumber() + " has no amount to charge");
        }
        // Trust expects baseamount in minor units (e.g. £10.50 -> "1050").
        String baseamount = amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toPlainString();
        String currency = tx.getSendCurrency() != null ? tx.getSendCurrency() : "GBP";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requesttypedescriptions", List.of("THREEDQUERY", "AUTH"));
        payload.put("accounttypedescription", "ECOM");
        payload.put("sitereference", siteReference);
        payload.put("currencyiso3a", currency);
        payload.put("baseamount", baseamount);
        payload.put("orderreference", tx.getReferenceNumber());

        String jwt = Jwts.builder()
                .issuer(username)
                .issuedAt(new Date())
                .claim("payload", payload)
                .signWith(key(), Jwts.SIG.HS256)
                .compact();
        log.info("Trust JWT: built request for {} (baseamount={}, currency={}, siteref={})",
                tx.getReferenceNumber(), baseamount, currency, siteReference);
        return jwt;
    }

    /**
     * Verify the signature on a Trust response JWT and extract the AUTH record. Throws if the
     * signature is invalid (wrong/absent secret) — a response we cannot cryptographically trust must
     * never drive a status change. Returns null only if there is genuinely no AUTH record present.
     */
    @SuppressWarnings("unchecked")
    public AuthResult verifyAndExtract(String responseJwt) {
        requireEnabled();
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(responseJwt)   // throws JwtException on bad signature
                .getPayload();

        Object payloadObj = claims.get("payload");
        if (!(payloadObj instanceof Map)) {
            log.warn("Trust JWT: response has no payload object");
            return null;
        }
        Object responseObj = ((Map<String, Object>) payloadObj).get("response");
        if (!(responseObj instanceof List)) {
            log.warn("Trust JWT: response payload has no response[] array");
            return null;
        }

        // The AUTH record carries the money-movement result (baseamount, settlestatus, errorcode).
        // A THREEDQUERY record may also be present; we ignore it here.
        for (Object item : (List<Object>) responseObj) {
            if (!(item instanceof Map)) continue;
            Map<String, Object> rec = (Map<String, Object>) item;
            if ("AUTH".equalsIgnoreCase(str(rec.get("requesttypedescription")))) {
                return new AuthResult(
                        str(rec.get("errorcode")),
                        str(rec.get("settlestatus")),
                        str(rec.get("baseamount")),
                        str(rec.get("transactionreference")),
                        str(rec.get("orderreference")));
            }
        }
        log.warn("Trust JWT: no AUTH record found in response[]");
        return null;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}

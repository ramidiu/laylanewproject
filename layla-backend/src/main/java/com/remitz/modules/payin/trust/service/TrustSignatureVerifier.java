package com.remitz.modules.payin.trust.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SECURITY (SEC-04): verifies the {@code responsesitesecurity} hash on Trust Payments URL
 * notifications (webhooks). Trust computes it as SHA-256(hex) of the configured response fields'
 * values concatenated in the MyST-defined order, followed by the account's Site Security password.
 *
 * <p>Deliberately runs in <b>LOG-ONLY</b> mode by default ({@code app.trust.verify-signature=false}):
 * it logs MATCH / MISMATCH / cannot-compute for every callback but NEVER rejects, so it can be
 * deployed with zero risk of dropping a genuine payment callback. Once the logs confirm real Trust
 * traffic hashes correctly (password + field order set), flip {@code verify-signature=true} to enforce.
 *
 * <p>Uses only the JDK ({@link MessageDigest}) — no new dependency.
 */
@Component
@Slf4j
public class TrustSignatureVerifier {

    /** Field carrying Trust's hash on the notification. */
    private static final String SIG_FIELD = "responsesitesecurity";

    private final String password;
    private final boolean enforce;
    private final List<String> fields;

    public TrustSignatureVerifier(
            @Value("${app.trust.site-security-password:}") String password,
            @Value("${app.trust.verify-signature:false}") boolean enforce,
            @Value("${app.trust.signature-fields:}") String signatureFields) {
        this.password = password == null ? "" : password.trim();
        this.enforce = enforce;
        this.fields = (signatureFields == null || signatureFields.isBlank())
                ? List.of()
                : Arrays.stream(signatureFields.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList());
    }

    /** Outcome of verifying a notification. {@code reject} is only ever true when enforcing. */
    public record Result(boolean reject, String reason) {
        static Result allow(String reason) { return new Result(false, reason); }
        static Result deny(String reason)  { return new Result(true, reason); }
    }

    /**
     * Verify (log-only unless {@code verify-signature=true}) the Trust notification signature.
     * Never throws; on any internal error it fails OPEN (allows) so payments are never lost to a bug.
     */
    public Result verify(Map<String, String> params, String source) {
        try {
            String received = params.get(SIG_FIELD);

            // No password configured -> verification disabled. Nothing to check.
            if (password.isEmpty()) {
                log.debug("Trust {} : signature check skipped (no site-security-password configured)", source);
                return Result.allow("verification disabled (no password)");
            }

            // Password set but we don't yet know which fields Trust hashes -> cannot compute.
            // Log the received hash + the field names present so the order can be determined from
            // real traffic, then allow. We never enforce without a field list (would drop everything).
            if (fields.isEmpty()) {
                log.warn("Trust {} : cannot verify signature yet — app.trust.signature-fields is not set. "
                                + "received {}={} ; notification fields present={}",
                        source, SIG_FIELD, received, params.keySet());
                return Result.allow("no signature-fields configured");
            }

            if (received == null || received.isBlank()) {
                String msg = "notification carried no " + SIG_FIELD;
                if (enforce) {
                    log.error("Trust {} : {} — REJECTING (enforce on)", source, msg);
                    return Result.deny(msg);
                }
                log.warn("Trust {} : {} — allowing (log-only)", source, msg);
                return Result.allow(msg);
            }

            String concat = fields.stream()
                    .map(f -> params.getOrDefault(f, ""))
                    .collect(Collectors.joining());
            String expected = sha256Hex(concat + password);
            boolean match = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));

            if (match) {
                log.info("Trust {} : signature MATCH (fields={})", source, fields);
                return Result.allow("signature match");
            }

            String msg = "signature MISMATCH — expected=" + expected + " received=" + received
                    + " (fields=" + fields + ")";
            if (enforce) {
                log.error("Trust {} : {} — REJECTING (enforce on)", source, msg);
                return Result.deny(msg);
            }
            log.warn("Trust {} : {} — allowing (log-only)", source, msg);
            return Result.allow(msg);
        } catch (Exception e) {
            // Fail open: a verification bug must never drop a real payment callback.
            log.error("Trust {} : signature verification error (allowing): {}", source, e.getMessage());
            return Result.allow("verifier error: " + e.getMessage());
        }
    }

    private static String sha256Hex(String input) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}

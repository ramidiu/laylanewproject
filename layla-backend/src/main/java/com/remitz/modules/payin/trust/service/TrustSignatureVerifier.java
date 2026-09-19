package com.remitz.modules.payin.trust.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SECURITY (SEC-04): verifies the {@code responsesitesecurity} hash on Trust Payments URL
 * notifications (webhooks), authenticating that a callback genuinely came from Trust.
 *
 * <h2>Trust's algorithm (self-describing — no field list to configure)</h2>
 * Per Trust's "Response site security" spec, the hash is computed as:
 * <ol>
 *   <li>Take <b>every</b> field in the notification EXCEPT {@code notificationreference} and
 *       {@code responsesitesecurity} (and any field whose value is blank — blanks are omitted).</li>
 *   <li>Sort the remaining fields by field <b>name</b> in ASCII alphabetical order.</li>
 *   <li>Concatenate their <b>values</b> (values only, not name=value; not URL-encoded; whitespace kept).</li>
 *   <li>Append the account's Site Security password at the <b>end</b>.</li>
 *   <li>{@code SHA-256} the string and hex-encode (lower-case). The response hash is NOT prefixed with 'h'.</li>
 * </ol>
 * Worked example from Trust's docs: fields errorcode=0, orderreference=Order,
 * paymenttypedescription=VISA, requestreference=RR555, settlestatus=0, sitereference=test_site12345,
 * transactionreference=2-44-66, password=PASSWORD →
 * {@code sha256("0OrderVISARR5550test_site123452-44-66PASSWORD")}
 * = {@code 1a8b45c137c1d1df8ce6ff923421043f879a85a181e9c0d96a8904211af8b0b0}.
 *
 * <p>Runs in <b>LOG-ONLY</b> mode by default ({@code app.trust.verify-signature=false}): it logs
 * MATCH / MISMATCH for every callback but NEVER rejects, so it can be deployed with zero risk of
 * dropping a genuine payment callback. Once the logs confirm real Trust traffic hashes correctly
 * (the Site Security password is set and matches MyST), flip {@code verify-signature=true} to enforce.
 *
 * <p>Uses only the JDK ({@link MessageDigest}) — no new dependency. Never throws: on any internal
 * error it fails OPEN (allows) so a verifier bug can never lose a real payment notification.
 */
@Component
@Slf4j
public class TrustSignatureVerifier {

    /** Field carrying Trust's hash on the notification. */
    private static final String SIG_FIELD = "responsesitesecurity";

    /** Fields Trust excludes from the hash input (per spec). */
    private static final Set<String> EXCLUDED = Set.of("notificationreference", SIG_FIELD);

    private final String password;
    private final boolean enforce;

    public TrustSignatureVerifier(
            @Value("${app.trust.site-security-password:}") String password,
            @Value("${app.trust.verify-signature:false}") boolean enforce) {
        this.password = password == null ? "" : password.trim();
        this.enforce = enforce;
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

            if (received == null || received.isBlank()) {
                String msg = "notification carried no " + SIG_FIELD;
                if (enforce) {
                    log.error("Trust {} : {} — REJECTING (enforce on)", source, msg);
                    return Result.deny(msg);
                }
                log.warn("Trust {} : {} — allowing (log-only)", source, msg);
                return Result.allow(msg);
            }

            // Trust's algorithm: all fields except the two excluded, blanks omitted, sorted by field
            // name (ASCII), values concatenated, password appended. See class javadoc.
            String concat = params.entrySet().stream()
                    .filter(e -> !EXCLUDED.contains(e.getKey()))
                    .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .sorted(Map.Entry.comparingByKey())
                    .map(Map.Entry::getValue)
                    .collect(Collectors.joining());
            String expected = sha256Hex(concat + password);
            boolean match = MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    received.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));

            if (match) {
                log.info("Trust {} : signature MATCH", source);
                return Result.allow("signature match");
            }

            // On mismatch, log the field ORDER we hashed (names only — never the password) so a real
            // mismatch can be diagnosed against MyST without leaking secrets.
            String order = params.entrySet().stream()
                    .filter(e -> !EXCLUDED.contains(e.getKey()))
                    .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .sorted()
                    .collect(Collectors.joining(","));
            String msg = "signature MISMATCH — expected=" + expected + " received=" + received
                    + " (hashed fields in order=[" + order + "])";
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

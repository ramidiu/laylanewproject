package com.remitz.modules.payin.trust;

import com.remitz.modules.payin.trust.service.TrustSignatureVerifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the {@link TrustSignatureVerifier} to Trust Payments' documented "Response site security"
 * algorithm, using the worked example straight from their docs:
 *
 * <pre>
 *   errorcode=0, orderreference=Order, paymenttypedescription=VISA, requestreference=RR555,
 *   settlestatus=0, sitereference=test_site12345, transactionreference=2-44-66, password=PASSWORD
 *   -> sha256("0OrderVISARR5550test_site123452-44-66PASSWORD")
 *   =  1a8b45c137c1d1df8ce6ff923421043f879a85a181e9c0d96a8904211af8b0b0
 * </pre>
 */
class TrustSignatureVerifierTest {

    private static final String PASSWORD = "PASSWORD";
    private static final String DOC_HASH = "1a8b45c137c1d1df8ce6ff923421043f879a85a181e9c0d96a8904211af8b0b0";

    /** Trust's example fields, deliberately inserted out of alphabetical order to prove we sort. */
    private static Map<String, String> exampleFields() {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("transactionreference", "2-44-66");
        p.put("sitereference", "test_site12345");
        p.put("settlestatus", "0");
        p.put("requestreference", "RR555");
        p.put("paymenttypedescription", "VISA");
        p.put("orderreference", "Order");
        p.put("errorcode", "0");
        return p;
    }

    @Test
    void enforcing_acceptsTheDocumentedHash() {
        TrustSignatureVerifier verifier = new TrustSignatureVerifier(PASSWORD, true);
        Map<String, String> params = exampleFields();
        params.put("responsesitesecurity", DOC_HASH);

        TrustSignatureVerifier.Result result = verifier.verify(params, "test");

        assertThat(result.reject()).isFalse();
        assertThat(result.reason()).isEqualTo("signature match");
    }

    @Test
    void enforcing_rejectsATamperedAmount() {
        TrustSignatureVerifier verifier = new TrustSignatureVerifier(PASSWORD, true);
        Map<String, String> params = exampleFields();
        params.put("baseamount", "5");                 // extra field not covered by the doc hash
        params.put("responsesitesecurity", DOC_HASH);  // stale hash -> must not verify

        TrustSignatureVerifier.Result result = verifier.verify(params, "test");

        assertThat(result.reject()).isTrue();
    }

    @Test
    void enforcing_excludesNotificationreferenceAndSecurityField() {
        // notificationreference must NOT affect the hash — adding it keeps the doc hash valid.
        TrustSignatureVerifier verifier = new TrustSignatureVerifier(PASSWORD, true);
        Map<String, String> params = exampleFields();
        params.put("notificationreference", "abc-123-should-be-ignored");
        params.put("responsesitesecurity", DOC_HASH);

        TrustSignatureVerifier.Result result = verifier.verify(params, "test");

        assertThat(result.reject()).isFalse();
    }

    @Test
    void enforcing_omitsBlankFields() {
        // A blank field must be omitted from the hash input, so the doc hash still matches.
        TrustSignatureVerifier verifier = new TrustSignatureVerifier(PASSWORD, true);
        Map<String, String> params = exampleFields();
        params.put("authcode", "");                    // blank -> omitted
        params.put("responsesitesecurity", DOC_HASH);

        TrustSignatureVerifier.Result result = verifier.verify(params, "test");

        assertThat(result.reject()).isFalse();
    }

    @Test
    void logOnly_neverRejectsEvenOnMismatch() {
        TrustSignatureVerifier verifier = new TrustSignatureVerifier(PASSWORD, false); // enforce=false
        Map<String, String> params = exampleFields();
        params.put("responsesitesecurity", "deadbeef");

        TrustSignatureVerifier.Result result = verifier.verify(params, "test");

        assertThat(result.reject()).isFalse(); // log-only: mismatch is logged but allowed
    }

    @Test
    void noPasswordConfigured_skipsVerification() {
        TrustSignatureVerifier verifier = new TrustSignatureVerifier("", true);
        Map<String, String> params = new HashMap<>(exampleFields());
        params.put("responsesitesecurity", "whatever");

        TrustSignatureVerifier.Result result = verifier.verify(params, "test");

        assertThat(result.reject()).isFalse();
    }
}

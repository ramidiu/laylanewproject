package com.remitz.modules.payin.trust;

import com.remitz.modules.payin.trust.service.TrustJwtService;
import com.remitz.modules.transaction.entity.TransactionEntity;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustJwtServiceTest {

    private static final String SECRET = "test-secret-0123456789abcdef0123456789ABCDEF";
    private static final String USER = "webservices@example.com";
    private static final String SITEREF = "test_laylalondo147950";

    private TrustJwtService enabledService() {
        return new TrustJwtService(true, USER, SECRET, SITEREF, 0);
    }

    private static TransactionEntity txn() {
        return TransactionEntity.builder()
                .referenceNumber("TXN5291")
                .totalDebitAmount(new BigDecimal("510.00"))
                .sendCurrency("GBP")
                .build();
    }

    @Test
    void createRequestJwt_signsServerAuthoritativeAmountInMinorUnits() {
        String jwt = enabledService().createRequestJwt(txn());

        // Verify with the same secret and inspect the payload.
        var key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(jwt).getPayload();

        assertThat(claims.getIssuer()).isEqualTo(USER);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = claims.get("payload", Map.class);
        assertThat(payload.get("baseamount")).isEqualTo("51000");         // £510.00 -> minor units
        assertThat(payload.get("currencyiso3a")).isEqualTo("GBP");
        assertThat(payload.get("orderreference")).isEqualTo("TXN5291");
        assertThat(payload.get("sitereference")).isEqualTo(SITEREF);
        assertThat(payload.get("accounttypedescription")).isEqualTo("ECOM");
    }

    @Test
    void verifyAndExtract_pullsTheAuthRecordFromAValidlySignedResponse() {
        String responseJwt = signedResponse(SECRET, "0", "0", "51000", "12-70-12347");

        TrustJwtService.AuthResult auth = enabledService().verifyAndExtract(responseJwt);

        assertThat(auth).isNotNull();
        assertThat(auth.errorCode()).isEqualTo("0");
        assertThat(auth.settleStatus()).isEqualTo("0");
        assertThat(auth.baseAmount()).isEqualTo("51000");
        assertThat(auth.transactionReference()).isEqualTo("12-70-12347");
        assertThat(auth.orderReference()).isEqualTo("TXN5291");
    }

    @Test
    void verifyAndExtract_rejectsAResponseSignedWithTheWrongSecret() {
        String forged = signedResponse("a-different-attacker-secret-000000000000", "0", "0", "5", "x");

        assertThatThrownBy(() -> enabledService().verifyAndExtract(forged))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void disabledWhenCredentialsMissing_andRefusesToBuild() {
        TrustJwtService disabled = new TrustJwtService(true, "", "", SITEREF, 0);
        assertThat(disabled.isEnabled()).isFalse();
        assertThatThrownBy(() -> disabled.createRequestJwt(txn()))
                .isInstanceOf(IllegalStateException.class);
    }

    /** Build a response JWT shaped like Trust's, with a THREEDQUERY then an AUTH record. */
    private static String signedResponse(String secret, String errorcode, String settlestatus,
                                         String baseamount, String txref) {
        var key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Map<String, Object> threedquery = Map.of(
                "requesttypedescription", "THREEDQUERY",
                "errorcode", "0",
                "orderreference", "TXN5291");
        Map<String, Object> auth = Map.of(
                "requesttypedescription", "AUTH",
                "errorcode", errorcode,
                "settlestatus", settlestatus,
                "baseamount", baseamount,
                "transactionreference", txref,
                "orderreference", "TXN5291");
        Map<String, Object> payload = Map.of("response", List.of(threedquery, auth));
        return Jwts.builder()
                .issuer("webservices@example.com")
                .issuedAt(new Date())
                .claim("payload", payload)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}

package com.remitz.modules.payin.kuber.service;

import com.remitz.modules.payin.kuber.config.KuberProperties;
import com.remitz.modules.payin.kuber.dto.KuberAddItemMenuItem;
import com.remitz.modules.payin.kuber.dto.KuberAddItemRequest;
import com.remitz.modules.payin.kuber.dto.KuberAddItemResponse;
import com.remitz.modules.payin.kuber.dto.KuberCheckoutTestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Code added by Naresh: Phase 3 — maps DEV-only input onto Kuber's event-shop-shaped
 * addItem payload and delegates the HTTP call to {@link KuberClient}.
 *
 * All event-shop semantics (aboutEvent, eventName, perTicketPrice, menuList) are
 * contained here; callers outside this module see only a normal checkout result.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KuberCheckoutService {

    private final KuberProperties properties;
    private final KuberClient client;

    /**
     * Builds a Kuber addItem request from the DEV smoke input and invokes {@link KuberClient#createCheckout}.
     * Returns the underlying response so the controller can surface orderID + redirectURL.
     */
    public KuberAddItemResponse createCheckoutFromDevInput(KuberCheckoutTestRequest input) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Kuber integration is disabled (kuber.enabled=false)");
        }
        if (input == null) {
            throw new IllegalArgumentException("Checkout request body is required");
        }
        if (input.amount() == null || input.amount().signum() <= 0) {
            throw new IllegalArgumentException("amount must be a positive value");
        }

        KuberAddItemRequest body = buildAddItemRequest(input);
        log.info("Kuber DEV checkout build complete (email={}, amount={})",
                input.email(), input.amount());
        return client.createCheckout(body);
    }

    // Visible for phase 3 test-case 8 (placeholder mapping review).
    KuberAddItemRequest buildAddItemRequest(KuberCheckoutTestRequest input) {
        String[] nameParts = splitFullName(input.fullName());
        String firstName = nameParts[0];
        String lastName = nameParts[1];

        BigDecimal amount = input.amount();

        KuberAddItemMenuItem item = new KuberAddItemMenuItem(
                firstName,
                lastName,
                safe(input.email()),
                safe(input.mobile()),
                // TODO (phase 4+): replace address placeholders with the real billing address
                // collected from the customer wallet / KYC record. Kuber requires non-blank
                // strings for street/city/state/postal/country; placeholder values keep the
                // DEV happy-path working until the business mapping is finalised.
                "NA",       // streetNumber
                "NA",       // streetName
                "NA",       // city
                "NA",       // state
                "0000",     // postal
                "AU",       // country — Kuber is AU-only (PayTo rail); confirm with vendor
                // TODO (phase 4+): decide what aboutEvent should carry for a remittance
                // pay-in — vendor may accept a fixed string or require the transaction ref.
                "Remitz remittance pay-in",
                1,          // quantity — fixed for remittance use-case
                amount      // perTicketPrice mirrors the requested amount for quantity=1
        );

        return new KuberAddItemRequest(
                List.of(item),
                // TODO (phase 4+): confirm with vendor what eventName should be for a
                // remittance pay-in; current value is a placeholder.
                "Remitz Pay-In",
                // TODO (phase 4+): eventDateTime is unix-seconds in Kuber's sample. For a
                // remittance pay-in "now" is a reasonable placeholder; revisit once the
                // vendor clarifies the intended semantics.
                Instant.now().getEpochSecond(),
                amount
        );
    }

    private static String[] splitFullName(String fullName) {
        if (fullName == null) return new String[]{"", ""};
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) return new String[]{"", ""};
        int firstSpace = trimmed.indexOf(' ');
        if (firstSpace < 0) return new String[]{trimmed, ""};
        return new String[]{
                trimmed.substring(0, firstSpace).trim(),
                trimmed.substring(firstSpace + 1).trim()
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

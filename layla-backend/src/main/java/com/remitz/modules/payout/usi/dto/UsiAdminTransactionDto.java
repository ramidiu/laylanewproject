package com.remitz.modules.payout.usi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Row shape for the admin USI Money page — joins a Layla transaction with
 * its USI-side counterpart (if any) so the operator can see what's been
 * pushed and what still needs creating / confirming / status-checking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsiAdminTransactionDto {

    // Layla-side
    private String  referenceNumber;        // TXN<n>
    private Long    senderId;
    private String  senderName;
    private String  senderEmail;
    private Long    beneficiaryId;
    private String  beneficiaryName;        // looked up
    private String  destinationCountry;
    private BigDecimal sendAmount;
    private String  sendCurrency;
    private BigDecimal receiveAmount;
    private String  receiveCurrency;
    private String  deliveryMethod;
    private String  laylaStatus;            // transactions.status (CREATED/PENDING/PROCESSING/PAID/...)
    private LocalDateTime createdAt;

    // USI-side (null until createTransaction is called)
    private String  usiTransSessionId;
    private String  usiReferenceNumber;     // e.g. USIA036126446596 (sandbox) or set in confirm
    private String  usiPaymentToken;        // e.g. 11036122446378 (production) — preferred display
    private String  usiStatus;              // initiated / sent for pay / paid / cancelled
    private String  usiPaymentStatus;       // funds received / FAILED
    private String  usiErrorMessage;
    private LocalDateTime usiUpdatedAt;

    /** Convenience for the UI — what action button to show next. */
    public String getNextAction() {
        if (usiStatus == null)                              return "CREATE";
        if ("initiated".equalsIgnoreCase(usiStatus))        return "CONFIRM";
        if ("sent for pay".equalsIgnoreCase(usiStatus))     return "CHECK_STATUS";
        if ("paid".equalsIgnoreCase(usiStatus))             return "DONE";
        if ("cancelled".equalsIgnoreCase(usiStatus))        return "DONE";
        return "CHECK_STATUS";
    }
}

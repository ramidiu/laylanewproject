package com.remitz.modules.transaction.service;

import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.entity.BeneficiaryEntity;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.BeneficiaryRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.svgsupport.BatikSVGDrawer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Renders a branded PDF transaction receipt using openhtmltopdf.
 * Loads an HTML template from classpath, substitutes {{placeholder}} tokens,
 * and renders to PDF bytes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionReceiptService {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm 'UTC'");
    // Code added by Naresh: split date/time for the Transaction ID card
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d MMM yyyy");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm 'UTC'");

    private final TransactionRepository transactionRepository;
    private final BeneficiaryRepository beneficiaryRepository;

    public byte[] generatePdfForTransaction(Long transactionId) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        return render(tx);
    }

    /** The branded receipt as HTML (same template/vars as the PDF) — for inline display
     *  in the mobile WebView, which can't render the PDF. */
    public String generateHtmlForTransaction(Long transactionId) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));
        BeneficiaryEntity beneficiary = beneficiaryRepository.findById(tx.getBeneficiaryId()).orElse(null);
        return substitute(loadTemplate(), buildVariables(tx, beneficiary));
    }

    public byte[] generatePdfByReference(String referenceNumber) {
        TransactionEntity tx = transactionRepository.findByReferenceNumber(referenceNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "referenceNumber", referenceNumber));
        return render(tx);
    }

    private byte[] render(TransactionEntity tx) {
        BeneficiaryEntity beneficiary = beneficiaryRepository.findById(tx.getBeneficiaryId()).orElse(null);

        String template = loadTemplate();
        Map<String, String> vars = buildVariables(tx, beneficiary);
        String html = substitute(template, vars);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useSVGDrawer(new BatikSVGDrawer());
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to render receipt PDF for txn {}: {}", tx.getReferenceNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate receipt PDF: " + e.getMessage(), e);
        }
    }

    private String loadTemplate() {
        try (InputStream in = new ClassPathResource("templates/receipt.html").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load receipt template", e);
        }
    }

    private Map<String, String> buildVariables(TransactionEntity tx, BeneficiaryEntity beneficiary) {
        Map<String, String> v = new HashMap<>();
        v.put("referenceNumber", safe(tx.getReferenceNumber()));
        v.put("status", tx.getStatus() != null ? tx.getStatus().name() : "UNKNOWN");
        v.put("statusLower", tx.getStatus() != null ? tx.getStatus().name().toLowerCase() : "unknown");
        v.put("createdAt", formatDateTime(tx.getCreatedAt()));
        v.put("updatedAt", formatDateTime(tx.getUpdatedAt()));
        v.put("deliveryMethod", tx.getDeliveryMethod() != null ? tx.getDeliveryMethod().name() : "—");
        v.put("paymentMethodType", tx.getPaymentMethodType() != null ? tx.getPaymentMethodType().name() : "—");
        v.put("senderName", safe(tx.getSenderName()));
        v.put("senderEmail", safe(tx.getSenderEmail()));

        if (beneficiary != null) {
            v.put("beneficiaryName", safe(beneficiary.getFullName()));
            v.put("beneficiaryCountry", safe(beneficiary.getCountry()));
            v.put("beneficiaryBank", buildBankLine(beneficiary));
            v.put("beneficiaryPhone", safe(beneficiary.getMobileNumber()));
            v.put("beneficiaryCity", safe(beneficiary.getBranchCity()));
            v.put("beneficiaryAccountNumber",
                    safe(beneficiary.getAccountNumber() != null && !beneficiary.getAccountNumber().isBlank()
                            ? beneficiary.getAccountNumber() : beneficiary.getIban()));
            v.put("beneficiaryBankName", safe(beneficiary.getBankName()));
            v.put("beneficiaryBranch", safe(beneficiary.getBranchState()));
            v.put("beneficiarySwift", safe(beneficiary.getSwiftBic()));
        } else {
            v.put("beneficiaryName", "—");
            v.put("beneficiaryCountry", "—");
            v.put("beneficiaryBank", "—");
            v.put("beneficiaryPhone", "—");
            v.put("beneficiaryCity", "—");
            v.put("beneficiaryAccountNumber", "—");
            v.put("beneficiaryBankName", "—");
            v.put("beneficiaryBranch", "—");
            v.put("beneficiarySwift", "—");
        }

        v.put("sendAmount", formatAmount(tx.getSendAmount()));
        v.put("sendCurrency", safe(tx.getSendCurrency()));
        v.put("receiveAmount", formatAmount(tx.getReceiveAmount()));
        v.put("receiveCurrency", safe(tx.getReceiveCurrency()));
        // Code added by Naresh: trim trailing zeros from rate so "1.00000000" → "1"
        v.put("appliedRate", tx.getAppliedRate() != null ? formatRate(tx.getAppliedRate()) : "—");
        v.put("feeAmount", formatAmount(tx.getFeeAmount()));
        v.put("generatedAt", formatDateTime(LocalDateTime.now()));
        // embed Remitz logo as data URI
        v.put("logoDataUri", loadLogoDataUri());
        // Code added by Naresh: human-readable status labels for heading + ribbon
        String s = tx.getStatus() != null ? tx.getStatus().name() : "UNKNOWN";
        String heading, ribbon, tone;
        switch (s) {
            case "PAID", "COMPLETED"       -> { heading = "Successful"; ribbon = "SUCCESS"; tone = "success"; }
            case "CREATED", "PENDING",
                 "PROCESSING", "FUNDS_RECEIVED",
                 "SENT_TO_PAYOUT"          -> { heading = "In Progress"; ribbon = "PENDING"; tone = "pending"; }
            case "COMPLIANCE_HOLD"         -> { heading = "On Hold"; ribbon = "HOLD"; tone = "pending"; }
            case "FAILED", "CANCELLED",
                 "REFUNDED"                -> { heading = "Failed"; ribbon = "FAILED"; tone = "failed"; }
            default                         -> { heading = s; ribbon = s; tone = "pending"; }
        }
        v.put("statusHeading", heading);
        v.put("statusRibbon", ribbon);
        v.put("statusTone", tone);
        v.put("statusColorSvg", statusColor(tone));
        v.put("statusBgSvg", statusBg(tone));
        v.put("statusBgSoftSvg", statusBgSoft(tone));
        v.put("statusIconSvg", statusIconSvg(tone));
        v.put("statusSubtitleSvg", statusSubtitle(tone));
        // Totals
        BigDecimal totalDeducted = (tx.getSendAmount() != null ? tx.getSendAmount() : BigDecimal.ZERO)
                .add(tx.getFeeAmount() != null ? tx.getFeeAmount() : BigDecimal.ZERO);
        v.put("totalDeducted", formatAmount(totalDeducted));
        // Code added by Naresh: expand ISO country codes to human-readable names
        v.put("senderCountry", countryName(tx.getSendCurrency()));
        v.put("receiverCountry", countryName(beneficiary != null ? beneficiary.getCountry() : tx.getReceiveCurrency()));
        // Code added by Naresh: split timestamp for the Transaction ID card
        LocalDateTime created = tx.getCreatedAt();
        v.put("createdDate", created != null ? created.format(DATE_FMT) : "—");
        v.put("createdTime", created != null ? created.format(TIME_FMT) : "—");
        return v;
    }

    private String countryName(String code) {
        if (code == null || code.isBlank()) return "—";
        String c = code.toUpperCase();
        return switch (c) {
            case "GB", "GBR", "GBP"  -> "United Kingdom";
            case "US", "USA", "USD"  -> "United States";
            case "AU", "AUS", "AUD"  -> "Australia";
            case "IN", "IND", "INR"  -> "India";
            case "PK", "PAK", "PKR"  -> "Pakistan";
            case "NG", "NGA", "NGN"  -> "Nigeria";
            case "GH", "GHA", "GHS"  -> "Ghana";
            case "PH", "PHL", "PHP"  -> "Philippines";
            case "BD", "BGD", "BDT"  -> "Bangladesh";
            case "AE", "ARE", "AED"  -> "United Arab Emirates";
            case "DE", "DEU", "EUR"  -> "Germany";
            case "FR", "FRA"         -> "France";
            case "CA", "CAN", "CAD"  -> "Canada";
            case "KE", "KEN", "KES"  -> "Kenya";
            case "NP", "NPL", "NPR"  -> "Nepal";
            case "LK", "LKA", "LKR"  -> "Sri Lanka";
            default                   -> c;
        };
    }

    private String loadLogoDataUri() {
        try (InputStream in = new ClassPathResource("static/remitz-logo-email.png").getInputStream()) {
            byte[] bytes = in.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            log.warn("Logo not found for receipt: {}", e.getMessage());
            return "";
        }
    }

    private String substitute(String template, Map<String, String> vars) {
        String result = template;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            String value = e.getValue();
            if (value == null) { value = "—"; }
            // Keys ending in "Svg" or "Html" contain pre-composed raw HTML and must NOT be escaped.
            else if (!e.getKey().endsWith("Svg") && !e.getKey().endsWith("Html")) {
                value = escapeHtml(value);
            }
            result = result.replace("{{" + e.getKey() + "}}", value);
        }
        return result;
    }

    private String statusColor(String tone) {
        return switch (tone) {
            case "success" -> "#16A34A";
            case "failed"  -> "#DC2626";
            default         -> "#F59E0B";
        };
    }

    private String statusBg(String tone) {
        return switch (tone) {
            case "success" -> "#DCFCE7";
            case "failed"  -> "#FEE2E2";
            default         -> "#FEF3C7";
        };
    }

    private String statusBgSoft(String tone) {
        return switch (tone) {
            case "success" -> "#F0FDF4";
            case "failed"  -> "#FEF2F2";
            default         -> "#FFFBEB";
        };
    }

    private String statusSubtitle(String tone) {
        return switch (tone) {
            case "success" -> "Your payment has been delivered successfully";
            case "failed"  -> "Your payment could not be completed";
            default         -> "Your payment is being processed securely";
        };
    }

    private String statusIconSvg(String tone) {
        return switch (tone) {
            case "success" -> "<svg width=\"30\" height=\"30\" viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">"
                    + "<path d=\"M5 12 l5 5 l9 -10\" stroke=\"#FFFFFF\" stroke-width=\"3\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
                    + "</svg>";
            case "failed" -> "<svg width=\"30\" height=\"30\" viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">"
                    + "<path d=\"M6 6 l12 12 M18 6 l-12 12\" stroke=\"#FFFFFF\" stroke-width=\"3\" stroke-linecap=\"round\"/>"
                    + "</svg>";
            default -> "<svg width=\"30\" height=\"30\" viewBox=\"0 0 24 24\" fill=\"none\" xmlns=\"http://www.w3.org/2000/svg\">"
                    + "<circle cx=\"12\" cy=\"12\" r=\"9\" stroke=\"#FFFFFF\" stroke-width=\"2.5\"/>"
                    + "<polyline points=\"12 7 12 12 15.5 14\" stroke=\"#FFFFFF\" stroke-width=\"2.5\" stroke-linecap=\"round\" stroke-linejoin=\"round\" fill=\"none\"/>"
                    + "</svg>";
        };
    }

    private String buildBankLine(BeneficiaryEntity b) {
        StringBuilder sb = new StringBuilder();
        if (b.getBankName() != null && !b.getBankName().isBlank()) sb.append(b.getBankName());
        if (b.getAccountNumber() != null && !b.getAccountNumber().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(maskAccount(b.getAccountNumber()));
        } else if (b.getIban() != null && !b.getIban().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(maskAccount(b.getIban()));
        } else if (b.getMobileNumber() != null && !b.getMobileNumber().isBlank()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(b.getMobileNumber());
        }
        return sb.length() > 0 ? sb.toString() : "—";
    }

    private String maskAccount(String account) {
        if (account == null || account.length() <= 4) return account;
        String last4 = account.substring(account.length() - 4);
        return "••••" + last4;
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) return "0.00";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String formatRate(BigDecimal rate) {
        if (rate == null) return "—";
        BigDecimal r = rate.setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros();
        // Guard against exponent like 1E+2
        return r.scale() <= 0 ? r.toPlainString() : r.toPlainString();
    }

    private String formatDateTime(LocalDateTime dt) {
        if (dt == null) return "—";
        return dt.format(TIMESTAMP_FMT);
    }

    private String safe(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}

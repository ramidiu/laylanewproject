package com.remitz.modules.transaction.service;

import com.remitz.modules.fx.dto.CorridorAutoCreateRequest;
import com.remitz.modules.fx.service.CorridorAutoCreateService;
import com.remitz.modules.transaction.entity.PaymentMethod;
import com.remitz.modules.transaction.entity.PayoutType;
import com.remitz.modules.transaction.repository.PaymentMethodRepository;
import com.remitz.modules.transaction.repository.PayoutTypeRepository;
import com.remitz.modules.user.entity.KycDocumentTypeConfig;
import com.remitz.modules.user.repository.KycDocumentTypeConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionCorridorSetupService {

    private final CorridorAutoCreateService fxCorridorAutoCreateService;
    private final KycDocumentTypeConfigRepository kycDocumentTypeConfigRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PayoutTypeRepository payoutTypeRepository;

    private static final Map<String, String> COUNTRY_TO_CURRENCY = Map.ofEntries(
            Map.entry("GB", "GBP"), Map.entry("US", "USD"), Map.entry("AU", "AUD"),
            Map.entry("AE", "AED"), Map.entry("DE", "EUR"), Map.entry("IN", "INR"),
            Map.entry("PK", "PKR"), Map.entry("NG", "NGN"), Map.entry("GH", "GHS"),
            Map.entry("PH", "PHP"), Map.entry("KE", "KES"), Map.entry("BD", "BDT"),
            Map.entry("ZA", "ZAR"), Map.entry("LK", "LKR"), Map.entry("NP", "NPR"),
            Map.entry("SD", "SDG"), Map.entry("TR", "TRY"), Map.entry("EG", "EGP"),
            Map.entry("SA", "SAR"), Map.entry("QA", "QAR"), Map.entry("UG", "UGX")
    );

    private static final Map<String, String> COUNTRY_TO_ISO3 = Map.ofEntries(
            Map.entry("GB", "GBR"), Map.entry("US", "USA"), Map.entry("AU", "AUS"),
            Map.entry("AE", "ARE"), Map.entry("DE", "DEU"), Map.entry("IN", "IND"),
            Map.entry("PK", "PAK"), Map.entry("NG", "NGA"), Map.entry("GH", "GHA"),
            Map.entry("PH", "PHL"), Map.entry("KE", "KEN"), Map.entry("BD", "BGD"),
            Map.entry("ZA", "ZAF"), Map.entry("LK", "LKA"), Map.entry("NP", "NPL"),
            Map.entry("SD", "SDN"), Map.entry("TR", "TUR"), Map.entry("EG", "EGY"),
            Map.entry("SA", "SAU"), Map.entry("QA", "QAT"), Map.entry("UG", "UGA")
    );

    private static final Map<String, String> CURRENCY_TO_COUNTRY = new HashMap<>();
    static {
        COUNTRY_TO_CURRENCY.forEach((country, currency) -> CURRENCY_TO_COUNTRY.putIfAbsent(currency, country));
    }

    private static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
            Map.entry("GB", "United Kingdom"), Map.entry("US", "United States"),
            Map.entry("AU", "Australia"), Map.entry("DE", "Germany"),
            Map.entry("IN", "India"), Map.entry("PK", "Pakistan"),
            Map.entry("NG", "Nigeria"), Map.entry("GH", "Ghana"),
            Map.entry("PH", "Philippines"), Map.entry("KE", "Kenya"),
            Map.entry("BD", "Bangladesh"), Map.entry("ZA", "South Africa"),
            Map.entry("NP", "Nepal"), Map.entry("AE", "UAE"),
            Map.entry("LK", "Sri Lanka"), Map.entry("SD", "Sudan"),
            Map.entry("TR", "Turkey"), Map.entry("EG", "Egypt"),
            Map.entry("SA", "Saudi Arabia"), Map.entry("QA", "Qatar"),
            Map.entry("UG", "Uganda"));

    @Async
    public void onPaymentMethodActivated(PaymentMethod paymentMethod) {
        seedKycDocumentTypes(paymentMethod.getCountryCode(), paymentMethod.getCountryName());

        String sendCurrency = paymentMethod.getCurrency();
        if (sendCurrency == null || sendCurrency.isBlank()) {
            sendCurrency = COUNTRY_TO_CURRENCY.get(paymentMethod.getCountryCode());
        }
        if (sendCurrency == null) return;

        String sendCountryIso3 = COUNTRY_TO_ISO3.getOrDefault(paymentMethod.getCountryCode(), paymentMethod.getCountryCode());

        List<PayoutType> activePayoutTypes = payoutTypeRepository.findAll().stream()
                .filter(pt -> Boolean.TRUE.equals(pt.getIsActive())).toList();

        Set<String> processed = new HashSet<>();
        for (PayoutType pt : activePayoutTypes) {
            String receiveCurrency = pt.getCurrency();
            if (receiveCurrency == null || receiveCurrency.isBlank()) receiveCurrency = COUNTRY_TO_CURRENCY.get(pt.getCountryCode());
            if (receiveCurrency == null || processed.contains(receiveCurrency)) continue;
            processed.add(receiveCurrency);
            callAutoCreate(sendCurrency, sendCountryIso3, receiveCurrency, COUNTRY_TO_ISO3.getOrDefault(pt.getCountryCode(), pt.getCountryCode()));
        }
    }

    @Async
    public void onPayoutTypeActivated(PayoutType payoutType) {
        seedKycDocumentTypes(payoutType.getCountryCode(), payoutType.getCountryName());

        String receiveCurrency = payoutType.getCurrency();
        if (receiveCurrency == null || receiveCurrency.isBlank()) receiveCurrency = COUNTRY_TO_CURRENCY.get(payoutType.getCountryCode());
        if (receiveCurrency == null) return;

        String receiveCountryIso3 = COUNTRY_TO_ISO3.getOrDefault(payoutType.getCountryCode(), payoutType.getCountryCode());

        List<PaymentMethod> activePaymentMethods = paymentMethodRepository.findAll().stream()
                .filter(pm -> Boolean.TRUE.equals(pm.getIsActive())).toList();

        Set<String> processed = new HashSet<>();
        for (PaymentMethod pm : activePaymentMethods) {
            String sendCurrency = pm.getCurrency();
            if (sendCurrency == null || sendCurrency.isBlank()) sendCurrency = COUNTRY_TO_CURRENCY.get(pm.getCountryCode());
            if (sendCurrency == null || processed.contains(sendCurrency)) continue;
            processed.add(sendCurrency);
            callAutoCreate(sendCurrency, COUNTRY_TO_ISO3.getOrDefault(pm.getCountryCode(), pm.getCountryCode()), receiveCurrency, receiveCountryIso3);
        }
    }

    @Async
    public void onPaymentMethodsForCountryActivated(List<PaymentMethod> methods) {
        for (PaymentMethod pm : methods) {
            if (Boolean.TRUE.equals(pm.getIsActive())) onPaymentMethodActivatedSync(pm);
        }
    }

    @Async
    public void onPayoutTypesForCountryActivated(List<PayoutType> types) {
        for (PayoutType pt : types) {
            if (Boolean.TRUE.equals(pt.getIsActive())) onPayoutTypeActivatedSync(pt);
        }
    }

    private void onPaymentMethodActivatedSync(PaymentMethod pm) {
        String sendCurrency = pm.getCurrency();
        if (sendCurrency == null || sendCurrency.isBlank()) sendCurrency = COUNTRY_TO_CURRENCY.get(pm.getCountryCode());
        if (sendCurrency == null) return;
        String sendCountryIso3 = COUNTRY_TO_ISO3.getOrDefault(pm.getCountryCode(), pm.getCountryCode());
        Set<String> processed = new HashSet<>();
        for (PayoutType pt : payoutTypeRepository.findAll().stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList()) {
            String rc = pt.getCurrency() != null ? pt.getCurrency() : COUNTRY_TO_CURRENCY.get(pt.getCountryCode());
            if (rc == null || processed.contains(rc)) continue;
            processed.add(rc);
            callAutoCreate(sendCurrency, sendCountryIso3, rc, COUNTRY_TO_ISO3.getOrDefault(pt.getCountryCode(), pt.getCountryCode()));
        }
    }

    private void onPayoutTypeActivatedSync(PayoutType pt) {
        String rc = pt.getCurrency() != null ? pt.getCurrency() : COUNTRY_TO_CURRENCY.get(pt.getCountryCode());
        if (rc == null) return;
        String rcIso3 = COUNTRY_TO_ISO3.getOrDefault(pt.getCountryCode(), pt.getCountryCode());
        Set<String> processed = new HashSet<>();
        for (PaymentMethod pm : paymentMethodRepository.findAll().stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList()) {
            String sc = pm.getCurrency() != null ? pm.getCurrency() : COUNTRY_TO_CURRENCY.get(pm.getCountryCode());
            if (sc == null || processed.contains(sc)) continue;
            processed.add(sc);
            callAutoCreate(sc, COUNTRY_TO_ISO3.getOrDefault(pm.getCountryCode(), pm.getCountryCode()), rc, rcIso3);
        }
    }

    private void seedKycDocumentTypes(String countryCode, String countryName) {
        if (countryCode == null || countryCode.isBlank()) return;
        try {
            String cc = countryCode.toUpperCase().trim();
            String name = (countryName != null && !countryName.isBlank())
                    ? countryName : COUNTRY_NAMES.getOrDefault(cc, cc);

            boolean hasIdentity = !kycDocumentTypeConfigRepository
                    .findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder(cc, "IDENTITY", true).isEmpty();
            boolean hasAddress = !kycDocumentTypeConfigRepository
                    .findByCountryCodeAndCategoryAndIsActiveOrderByDisplayOrder(cc, "ADDRESS", true).isEmpty();

            if (!hasIdentity) {
                String[][] idDocs = {
                        {"Passport", "1", "true", "true"}, {"National ID", "2", "true", "false"},
                        {"Driver's Licence", "2", "true", "false"}
                };
                int order = 1;
                for (String[] d : idDocs) {
                    kycDocumentTypeConfigRepository.save(KycDocumentTypeConfig.builder()
                            .countryCode(cc).countryName(name).category("IDENTITY")
                            .documentName(d[0]).sides(Integer.parseInt(d[1]))
                            .hasIdNumber(true).idNumberLabel(d[0] + " Number")
                            .hasExpiry(Boolean.parseBoolean(d[2])).hasIssueDate(Boolean.parseBoolean(d[3]))
                            .isActive(true).displayOrder(order++).build());
                }
            }
            if (!hasAddress) {
                String[] addrDocs = {"Utility Bill", "Bank Statement", "Council Tax Bill"};
                int order = 1;
                for (String doc : addrDocs) {
                    kycDocumentTypeConfigRepository.save(KycDocumentTypeConfig.builder()
                            .countryCode(cc).countryName(name).category("ADDRESS")
                            .documentName(doc).sides(1).hasIdNumber(false)
                            .hasExpiry(false).hasIssueDate(false).isActive(true).displayOrder(order++).build());
                }
            }
            log.info("KYC doc-type seed complete for {}", cc);
        } catch (Exception e) {
            log.warn("Failed to seed KYC doc types for {}: {}", countryCode, e.getMessage());
        }
    }

    private void callAutoCreate(String sendCurrency, String sendCountry, String receiveCurrency, String receiveCountry) {
        try {
            fxCorridorAutoCreateService.autoCreateCorridor(CorridorAutoCreateRequest.builder()
                    .sendCurrency(sendCurrency).sendCountry(sendCountry)
                    .receiveCurrency(receiveCurrency).receiveCountry(receiveCountry)
                    .build());
            log.info("Auto-create corridor {}->{} OK", sendCurrency, receiveCurrency);
        } catch (Exception e) {
            log.error("Failed to auto-create corridor {}->{}: {}", sendCurrency, receiveCurrency, e.getMessage());
        }
    }
}

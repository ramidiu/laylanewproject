package com.remitz.modules.transaction.service;

import com.remitz.modules.transaction.entity.PartnerLedger;
import com.remitz.modules.transaction.entity.PayinPartnerLedger;
import com.remitz.modules.transaction.repository.PartnerLedgerRepository;
import com.remitz.modules.transaction.repository.PayinPartnerLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerLedgerService {

    private final PartnerLedgerRepository partnerLedgerRepository;
    private final PayinPartnerLedgerRepository payinPartnerLedgerRepository;
    private final LedgerCurrencyConverter currencyConverter;

    /**
     * Records a payout-partner ledger entry. Amounts and the running balance are kept in GBP (the ledger
     * base currency), derived here from {@code localAmount}/{@code localCurrency}. The legacy
     * {@code usdAmount}/{@code fxRateUsed} parameters are ignored; the DB columns now hold GBP values.
     */
    @Transactional
    public PartnerLedger addPartnerEntry(Long partnerId, Long transactionId, String txnDisplayId,
                                         String entryType, BigDecimal localAmount, String localCurrency,
                                         BigDecimal usdAmount, BigDecimal fxRateUsed, String description) {
        BigDecimal gbpAmount = currencyConverter.toGbp(localAmount, localCurrency);
        BigDecimal gbpRate = currencyConverter.gbpRate(localCurrency);

        BigDecimal currentBalance = partnerLedgerRepository.findTopByPartnerIdOrderByIdDesc(partnerId)
                .map(PartnerLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal newBalance = "CREDIT".equals(entryType)
                ? currentBalance.add(gbpAmount)
                : currentBalance.subtract(gbpAmount);

        PartnerLedger entry = PartnerLedger.builder()
                .partnerId(partnerId)
                .transactionId(transactionId)
                .txnDisplayId(txnDisplayId)
                .entryType(entryType)
                .localAmount(localAmount)
                .localCurrency(localCurrency)
                .usdAmount(gbpAmount)
                .fxRateUsed(gbpRate)
                .balanceUsd(newBalance)
                .description(description)
                .build();

        PartnerLedger saved = partnerLedgerRepository.save(entry);
        log.info("Partner ledger entry: partnerId={}, type={}, gbpAmount={}, newBalance={} GBP",
                partnerId, entryType, gbpAmount, newBalance);
        return saved;
    }

    /**
     * Records a pay-in-partner ledger entry. Amounts and the running balance are kept in GBP, derived here
     * from {@code localAmount}/{@code localCurrency}. Legacy {@code usdAmount}/{@code fxRateUsed} are ignored.
     */
    @Transactional
    public PayinPartnerLedger addPayinPartnerEntry(Long partnerId, Long transactionId, String txnDisplayId,
                                                    String entryType, BigDecimal localAmount, String localCurrency,
                                                    BigDecimal usdAmount, BigDecimal fxRateUsed, String description) {
        BigDecimal gbpAmount = currencyConverter.toGbp(localAmount, localCurrency);
        BigDecimal gbpRate = currencyConverter.gbpRate(localCurrency);

        BigDecimal currentBalance = payinPartnerLedgerRepository.findTopByPartnerIdOrderByIdDesc(partnerId)
                .map(PayinPartnerLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal newBalance = "CREDIT".equals(entryType)
                ? currentBalance.add(gbpAmount)
                : currentBalance.subtract(gbpAmount);

        PayinPartnerLedger entry = PayinPartnerLedger.builder()
                .partnerId(partnerId)
                .transactionId(transactionId)
                .txnDisplayId(txnDisplayId)
                .entryType(entryType)
                .localAmount(localAmount)
                .localCurrency(localCurrency)
                .usdAmount(gbpAmount)
                .fxRateUsed(gbpRate)
                .balanceUsd(newBalance)
                .description(description)
                .build();

        PayinPartnerLedger saved = payinPartnerLedgerRepository.save(entry);
        log.info("Payin partner ledger entry: partnerId={}, type={}, gbpAmount={}, newBalance={} GBP",
                partnerId, entryType, gbpAmount, newBalance);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PartnerLedger> getPartnerLedger(Long partnerId) {
        return partnerLedgerRepository.findByPartnerIdOrderByIdAsc(partnerId);
    }

    @Transactional(readOnly = true)
    public List<PayinPartnerLedger> getPayinPartnerLedger(Long partnerId) {
        return payinPartnerLedgerRepository.findByPartnerIdOrderByIdAsc(partnerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getPartnerBalance(Long partnerId) {
        return partnerLedgerRepository.findTopByPartnerIdOrderByIdDesc(partnerId)
                .map(PartnerLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);
    }

    @Transactional(readOnly = true)
    public BigDecimal getPayinPartnerBalance(Long partnerId) {
        return payinPartnerLedgerRepository.findTopByPartnerIdOrderByIdDesc(partnerId)
                .map(PayinPartnerLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);
    }
}

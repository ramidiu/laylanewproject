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

    @Transactional
    public PartnerLedger addPartnerEntry(Long partnerId, Long transactionId, String txnDisplayId,
                                         String entryType, BigDecimal localAmount, String localCurrency,
                                         BigDecimal usdAmount, BigDecimal fxRateUsed, String description) {
        BigDecimal currentBalance = partnerLedgerRepository.findTopByPartnerIdOrderByIdDesc(partnerId)
                .map(PartnerLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal newBalance = "CREDIT".equals(entryType)
                ? currentBalance.add(usdAmount)
                : currentBalance.subtract(usdAmount);

        PartnerLedger entry = PartnerLedger.builder()
                .partnerId(partnerId)
                .transactionId(transactionId)
                .txnDisplayId(txnDisplayId)
                .entryType(entryType)
                .localAmount(localAmount)
                .localCurrency(localCurrency)
                .usdAmount(usdAmount)
                .fxRateUsed(fxRateUsed)
                .balanceUsd(newBalance)
                .description(description)
                .build();

        PartnerLedger saved = partnerLedgerRepository.save(entry);
        log.info("Partner ledger entry: partnerId={}, type={}, usdAmount={}, newBalance={}",
                partnerId, entryType, usdAmount, newBalance);
        return saved;
    }

    @Transactional
    public PayinPartnerLedger addPayinPartnerEntry(Long partnerId, Long transactionId, String txnDisplayId,
                                                    String entryType, BigDecimal localAmount, String localCurrency,
                                                    BigDecimal usdAmount, BigDecimal fxRateUsed, String description) {
        BigDecimal currentBalance = payinPartnerLedgerRepository.findTopByPartnerIdOrderByIdDesc(partnerId)
                .map(PayinPartnerLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal newBalance = "CREDIT".equals(entryType)
                ? currentBalance.add(usdAmount)
                : currentBalance.subtract(usdAmount);

        PayinPartnerLedger entry = PayinPartnerLedger.builder()
                .partnerId(partnerId)
                .transactionId(transactionId)
                .txnDisplayId(txnDisplayId)
                .entryType(entryType)
                .localAmount(localAmount)
                .localCurrency(localCurrency)
                .usdAmount(usdAmount)
                .fxRateUsed(fxRateUsed)
                .balanceUsd(newBalance)
                .description(description)
                .build();

        PayinPartnerLedger saved = payinPartnerLedgerRepository.save(entry);
        log.info("Payin partner ledger entry: partnerId={}, type={}, usdAmount={}, newBalance={}",
                partnerId, entryType, usdAmount, newBalance);
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

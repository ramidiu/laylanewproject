package com.remitz.modules.transaction.service;

import com.remitz.modules.transaction.entity.PlatformLedger;
import com.remitz.modules.transaction.repository.PlatformLedgerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlatformLedgerService {

    private final PlatformLedgerRepository platformLedgerRepository;

    @Transactional
    public PlatformLedger addEntry(Long transactionId, String txnDisplayId,
                                   String entryType, BigDecimal localAmount, String localCurrency,
                                   BigDecimal usdAmount, BigDecimal fxRateUsed, String description,
                                   String accountType) {
        BigDecimal currentBalance = platformLedgerRepository.findTopByOrderByIdDesc()
                .map(PlatformLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal newBalance = "CREDIT".equals(entryType)
                ? currentBalance.add(usdAmount)
                : currentBalance.subtract(usdAmount);

        PlatformLedger entry = PlatformLedger.builder()
                .transactionId(transactionId)
                .txnDisplayId(txnDisplayId)
                .entryType(entryType)
                .localAmount(localAmount)
                .localCurrency(localCurrency)
                .usdAmount(usdAmount)
                .fxRateUsed(fxRateUsed)
                .balanceUsd(newBalance)
                .description(description)
                .accountType(accountType)
                .build();

        PlatformLedger saved = platformLedgerRepository.save(entry);
        log.info("Platform ledger entry: accountType={}, type={}, usdAmount={}, newBalance={}",
                accountType, entryType, usdAmount, newBalance);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<PlatformLedger> getAllEntries() {
        return platformLedgerRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<PlatformLedger> getEntriesByAccountType(String accountType) {
        return platformLedgerRepository.findByAccountTypeOrderByIdAsc(accountType);
    }

    @Transactional(readOnly = true)
    public BigDecimal getCurrentBalance() {
        return platformLedgerRepository.findTopByOrderByIdDesc()
                .map(PlatformLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);
    }
}

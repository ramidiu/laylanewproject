package com.remitz.modules.transaction.service;

import com.remitz.modules.transaction.entity.LedgerEntry;
import com.remitz.modules.transaction.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public LedgerEntry createEntry(Long transactionId, String debitAccount, String creditAccount,
                                   BigDecimal amount, String currency, String entryType, String description) {
        LedgerEntry entry = LedgerEntry.builder()
                .transactionId(transactionId)
                .debitAccount(debitAccount)
                .creditAccount(creditAccount)
                .amount(amount)
                .currency(currency)
                .entryType(entryType)
                .description(description)
                .build();

        LedgerEntry saved = ledgerEntryRepository.save(entry);
        log.info("Ledger entry created: type={}, transactionId={}, amount={} {}", entryType, transactionId, amount, currency);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> getEntriesByTransactionId(Long transactionId) {
        return ledgerEntryRepository.findByTransactionId(transactionId);
    }
}

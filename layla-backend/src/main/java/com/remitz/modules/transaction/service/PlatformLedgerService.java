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
    private final LedgerCurrencyConverter currencyConverter;

    /**
     * Records a platform ledger entry. The ledger base currency is GBP: the stored amount and running
     * balance are always derived here from {@code localAmount}/{@code localCurrency}, so every entry is
     * consistent regardless of how the caller computed things. The legacy {@code usdAmount}/{@code fxRateUsed}
     * parameters are ignored (kept for signature compatibility) — the DB columns now hold GBP values.
     */
    @Transactional
    public PlatformLedger addEntry(Long transactionId, String txnDisplayId,
                                   String entryType, BigDecimal localAmount, String localCurrency,
                                   BigDecimal usdAmount, BigDecimal fxRateUsed, String description,
                                   String accountType) {
        BigDecimal gbpAmount = currencyConverter.toGbp(localAmount, localCurrency);
        BigDecimal gbpRate = currencyConverter.gbpRate(localCurrency);

        BigDecimal currentBalance = platformLedgerRepository.findTopByOrderByIdDesc()
                .map(PlatformLedger::getBalanceUsd)
                .orElse(BigDecimal.ZERO);

        BigDecimal newBalance = "CREDIT".equals(entryType)
                ? currentBalance.add(gbpAmount)
                : currentBalance.subtract(gbpAmount);

        PlatformLedger entry = PlatformLedger.builder()
                .transactionId(transactionId)
                .txnDisplayId(txnDisplayId)
                .entryType(entryType)
                .localAmount(localAmount)
                .localCurrency(localCurrency)
                .usdAmount(gbpAmount)
                .fxRateUsed(gbpRate)
                .balanceUsd(newBalance)
                .description(description)
                .accountType(accountType)
                .build();

        PlatformLedger saved = platformLedgerRepository.save(entry);
        log.info("Platform ledger entry: accountType={}, type={}, gbpAmount={}, newBalance={} GBP",
                accountType, entryType, gbpAmount, newBalance);
        return saved;
    }

    /**
     * Records a payout for a transaction in GBP double-entry:
     *   CREDIT "money in" = send_amount (written only if not already present for this txn), and
     *   DEBIT  "payout"   = send_amount - fee_amount (the GBP principal actually sent out).
     * The fee stays behind, so the running balance accumulates as profit. Foreign payout amounts
     * are NOT reconverted via settlement_rates (those rates are stale, e.g. SDG) — everything is
     * valued from the transaction's GBP fields.
     */
    @Transactional
    public void recordPayout(Long transactionId, String txnDisplayId,
                             BigDecimal sendAmount, BigDecimal feeAmount) {
        BigDecimal send = sendAmount != null ? sendAmount : BigDecimal.ZERO;
        BigDecimal fee  = feeAmount  != null ? feeAmount  : BigDecimal.ZERO;

        if (transactionId != null
                && !platformLedgerRepository.existsByTransactionIdAndAccountType(transactionId, "HOLDING")) {
            addEntry(transactionId, txnDisplayId, "CREDIT", send, "GBP",
                    send, BigDecimal.ONE, "Payment received for " + txnDisplayId, "HOLDING");
        }

        // Fee-on-top: the beneficiary receives the FULL send amount converted, so the payout
        // principal is the whole send (the fee is booked separately as REVENUE, not withheld here).
        BigDecimal payoutGbp = send.max(BigDecimal.ZERO);
        addEntry(transactionId, txnDisplayId, "DEBIT", payoutGbp, "GBP",
                payoutGbp, BigDecimal.ONE, "Payout for " + txnDisplayId, "PAYOUT");
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

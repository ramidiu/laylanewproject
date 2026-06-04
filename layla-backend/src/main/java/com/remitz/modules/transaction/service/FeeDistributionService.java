package com.remitz.modules.transaction.service;

import com.remitz.common.exception.RemitzException;
import com.remitz.modules.transaction.entity.CorridorFeeConfig;
import com.remitz.modules.transaction.entity.SettlementRate;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.CorridorFeeConfigRepository;
import com.remitz.modules.transaction.repository.SettlementRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Shared fee-split bookkeeping for the "funds received" step.
 * Behaviour is identical to the original AdminTransactionController.processFeeDistribution —
 * extracted so both the admin endpoint and the pay-in partner endpoint can call it
 * (G2: pay-in partner action was not booking fee splits).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeeDistributionService {

    private final CorridorFeeConfigRepository corridorFeeConfigRepository;
    private final SettlementRateRepository settlementRateRepository;
    private final PartnerLedgerService partnerLedgerService;
    private final PlatformLedgerService platformLedgerService;

    public BigDecimal getSettlementRate(String currency) {
        if ("USD".equalsIgnoreCase(currency)) return BigDecimal.ONE;
        return settlementRateRepository.findByCurrency(currency)
                .map(SettlementRate::getRateToUsd)
                .orElseThrow(() -> new RemitzException(
                        "Settlement rate not found for currency: " + currency, HttpStatus.INTERNAL_SERVER_ERROR));
    }

    public BigDecimal convertToUsd(BigDecimal amount, BigDecimal rateToUsd) {
        if (rateToUsd.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return amount.divide(rateToUsd, 4, RoundingMode.HALF_UP);
    }

    public void processFeeDistribution(TransactionEntity tx, BigDecimal settlementRate) {
        BigDecimal feeAmount = tx.getFeeAmount();
        if (feeAmount == null || feeAmount.compareTo(BigDecimal.ZERO) <= 0) return;
        BigDecimal feeAmountUsd = convertToUsd(feeAmount, settlementRate);

        List<CorridorFeeConfig> feeConfigs = corridorFeeConfigRepository
                .findByFromCurrencyAndToCurrency(tx.getSendCurrency(), tx.getReceiveCurrency());

        CorridorFeeConfig feeConfig = feeConfigs.stream()
                .filter(fc -> Boolean.TRUE.equals(fc.getIsActive()))
                .findFirst()
                .orElse(null);

        if (feeConfig == null) {
            platformLedgerService.addEntry(tx.getId(), tx.getReferenceNumber(),
                    "CREDIT", feeAmount, tx.getFeeCurrency(),
                    feeAmountUsd, settlementRate,
                    "Admin fee for " + tx.getReferenceNumber(), "REVENUE");
            return;
        }

        BigDecimal payinShare = BigDecimal.ZERO;
        if (feeConfig.getPayinShareValue() != null && feeConfig.getPayinShareValue().compareTo(BigDecimal.ZERO) > 0) {
            payinShare = "PERCENTAGE".equalsIgnoreCase(feeConfig.getPayinShareType())
                    ? feeAmount.multiply(feeConfig.getPayinShareValue()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    : feeConfig.getPayinShareValue();
        }

        BigDecimal payoutShare = BigDecimal.ZERO;
        if (feeConfig.getPayoutShareValue() != null && feeConfig.getPayoutShareValue().compareTo(BigDecimal.ZERO) > 0) {
            payoutShare = "PERCENTAGE".equalsIgnoreCase(feeConfig.getPayoutShareType())
                    ? feeAmount.multiply(feeConfig.getPayoutShareValue()).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                    : feeConfig.getPayoutShareValue();
        }

        BigDecimal adminShare = feeAmount.subtract(payinShare).subtract(payoutShare);
        if (adminShare.compareTo(BigDecimal.ZERO) > 0) {
            platformLedgerService.addEntry(tx.getId(), tx.getReferenceNumber(),
                    "CREDIT", adminShare, tx.getFeeCurrency(),
                    convertToUsd(adminShare, settlementRate), settlementRate,
                    "Admin fee share for " + tx.getReferenceNumber(), "REVENUE");
        }

        if (feeConfig.getPayinPartnerId() != null && payinShare.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal payinShareUsd = convertToUsd(payinShare, settlementRate);

            partnerLedgerService.addPayinPartnerEntry(
                    feeConfig.getPayinPartnerId(), tx.getId(), tx.getReferenceNumber(),
                    "CREDIT", tx.getSendAmount(), tx.getSendCurrency(),
                    convertToUsd(tx.getSendAmount(), settlementRate), settlementRate,
                    "Payment collected for " + tx.getReferenceNumber());

            partnerLedgerService.addPayinPartnerEntry(
                    feeConfig.getPayinPartnerId(), tx.getId(), tx.getReferenceNumber(),
                    "DEBIT", payinShare, tx.getFeeCurrency(),
                    payinShareUsd, settlementRate,
                    "Payin commission for " + tx.getReferenceNumber());
        }
        // Payout-partner ledger entries continue to be booked when the payout partner marks PAID.
    }
}

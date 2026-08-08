package com.remitz.modules.payout.usi.service;

import com.remitz.modules.payout.usi.dto.CollectionPointResponse;
import com.remitz.modules.payout.usi.dto.RemitterVerifyResponse;
import com.remitz.modules.payout.usi.dto.UsiAdminTransactionDto;
import com.remitz.modules.payout.usi.dto.UsiChargesResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface UsiPayoutService {

    /** Admin list — every Layla transaction with USI mirror state (if any). */
    List<UsiAdminTransactionDto> listAdminTransactions(String statusFilter, int limit);

    Map<String, Object> createRemitter(Long userId);
    RemitterVerifyResponse verifyRemitter(Long userId);

    /**
     * Push a user's current profile to USI Money and refresh their verification status.
     * Called whenever KYC-relevant user data changes (self profile edit or admin edit):
     * creates the remitter when none exists yet, otherwise sends an updateRemitter so USI
     * holds the new name/address/phone/etc, then re-runs verifyRemitter so the local
     * `verified` flag reflects USI's current status. Best-effort — never throws; returns a
     * status map (SKIPPED / MISSING_INFO / SUCCESS / FAILED, plus verified/verifyStatus).
     */
    Map<String, Object> syncRemitter(Long userId);

    /**
     * Pay-in entry point: validate the selected customer has the data USI needs for a
     * remitter and, if so, create it — keyed by the pay-in customer UUID. Returns a
     * status of READY / MISSING_INFO / FAILED / SKIPPED with a human-readable message,
     * so the operator sees any issue at customer-selection time rather than later at
     * "Create on USI".
     */
    Map<String, Object> ensureRemitter(String customerId);

    /**
     * Side-effect-free check (no USI call): which USI-required remitter fields is this
     * pay-in customer missing? Empty list = ready (or USI disabled / no linked user, in
     * which case the caller must not block). Used by the pay-in create guard so a
     * transaction is never created when its remitter could never be built.
     */
    List<String> findMissingRemitterFields(String customerId);

    Map<String, Object> createBeneficiary(Long beneficiaryId);
    String searchBeneficiary(String usiBeneficiaryId);

    /**
     * Push a beneficiary's current data to USI Money whenever it changes (recipient edit).
     * Creates the USI beneficiary when none exists yet (and the owner already has a remitter),
     * otherwise sends an updateBeneficiary so USI holds the new name/address/bank/mobile. Best-
     * effort — never throws; returns a status map (SKIPPED / SUCCESS / FAILED).
     */
    Map<String, Object> syncBeneficiary(Long beneficiaryId);

    Map<String, Object> createTransaction(String referenceNumber);

    /**
     * Read-only USI getCharges quote for a corridor + payout method. Returns the USI rate and
     * charges WITHOUT creating anything and WITHOUT touching customer quotes (Phase 1).
     */
    UsiChargesResult getCharges(String destinationCountry, String usiTransType,
                                String sourceCurrency, String destCurrency, BigDecimal sourceAmount);

    Map<String, Object> confirmTransaction(String referenceNumber);
    Map<String, Object> getTransactionStatus(String referenceNumber);

    List<Map<String, Object>> createMultipleTransactions(List<String> referenceNumbers);
    List<Map<String, Object>> confirmMultipleTransactions(List<String> referenceNumbers);
    List<Map<String, Object>> getMultipleTransactionStatus(List<String> referenceNumbers);

    List<CollectionPointResponse> getCollectionPoints(String countryName);
}

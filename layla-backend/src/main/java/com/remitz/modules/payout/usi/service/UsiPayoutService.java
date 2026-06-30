package com.remitz.modules.payout.usi.service;

import com.remitz.modules.payout.usi.dto.CollectionPointResponse;
import com.remitz.modules.payout.usi.dto.RemitterVerifyResponse;
import com.remitz.modules.payout.usi.dto.UsiAdminTransactionDto;

import java.util.List;
import java.util.Map;

public interface UsiPayoutService {

    /** Admin list — every Layla transaction with USI mirror state (if any). */
    List<UsiAdminTransactionDto> listAdminTransactions(String statusFilter, int limit);

    Map<String, Object> createRemitter(Long userId);
    RemitterVerifyResponse verifyRemitter(Long userId);

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

    Map<String, Object> createTransaction(String referenceNumber);
    Map<String, Object> confirmTransaction(String referenceNumber);
    Map<String, Object> getTransactionStatus(String referenceNumber);

    List<Map<String, Object>> createMultipleTransactions(List<String> referenceNumbers);
    List<Map<String, Object>> confirmMultipleTransactions(List<String> referenceNumbers);
    List<Map<String, Object>> getMultipleTransactionStatus(List<String> referenceNumbers);

    List<CollectionPointResponse> getCollectionPoints(String countryName);
}

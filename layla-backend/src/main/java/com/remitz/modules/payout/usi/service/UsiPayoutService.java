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

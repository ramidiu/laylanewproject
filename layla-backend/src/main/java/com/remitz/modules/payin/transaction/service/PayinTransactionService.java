package com.remitz.modules.payin.transaction.service;

import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionRequest;
import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionResponse;
import com.remitz.modules.payin.transaction.dto.PayinTransactionDto;

import java.util.List;

public interface PayinTransactionService {

    CreatePayinTransactionResponse createTransaction(CreatePayinTransactionRequest request);

    List<PayinTransactionDto> listTransactions();

    List<PayinTransactionDto> listProcessingTransactions();

    PayinTransactionDto markPaid(String transactionId);

    /** Branded PDF receipt for a PayIn transaction (resolves the linked customer transaction). */
    byte[] generateReceiptPdf(String transactionId);
}

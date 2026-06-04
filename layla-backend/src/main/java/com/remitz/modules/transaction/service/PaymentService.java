package com.remitz.modules.transaction.service;

import com.remitz.common.enums.BankStatementStatus;
import com.remitz.common.enums.PaymentMethodType;
import com.remitz.common.enums.PaymentStatus;
import com.remitz.common.exception.RemitzException;
import com.remitz.common.exception.ResourceNotFoundException;
import com.remitz.modules.transaction.dto.PaymentResponse;
import com.remitz.modules.transaction.entity.BankStatementEntity;
import com.remitz.modules.transaction.entity.PaymentEntity;
import com.remitz.modules.transaction.entity.TransactionEntity;
import com.remitz.modules.transaction.repository.BankStatementRepository;
import com.remitz.modules.transaction.repository.PaymentRepository;
import com.remitz.modules.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final BankStatementRepository bankStatementRepository;

    /**
     * Payment provider abstraction interface.
     */
    public interface PaymentProvider {
        String getName();
        PaymentMethodType getSupportedMethod();
        String initiatePayment(PaymentEntity payment);
        PaymentStatus checkStatus(String providerReference);
    }

    /**
     * Manual bank transfer provider implementation.
     */
    @Service
    @Slf4j
    public static class ManualBankTransferProvider implements PaymentProvider {

        @Override
        public String getName() {
            return "MANUAL_BANK_TRANSFER";
        }

        @Override
        public PaymentMethodType getSupportedMethod() {
            return PaymentMethodType.BANK_TRANSFER;
        }

        @Override
        public String initiatePayment(PaymentEntity payment) {
            // For manual bank transfers, generate a reference for the user to include in their payment
            String reference = "FB-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            log.info("Manual bank transfer initiated with reference={} for transactionId={}",
                    reference, payment.getTransactionId());
            return reference;
        }

        @Override
        public PaymentStatus checkStatus(String providerReference) {
            // Manual transfers are confirmed via bank statement matching
            return PaymentStatus.PENDING;
        }
    }

    @Transactional
    public PaymentResponse initiatePayment(Long transactionId, PaymentMethodType methodType) {
        TransactionEntity tx = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", transactionId));

        PaymentEntity payment = PaymentEntity.builder()
                .transactionId(transactionId)
                .userId(tx.getSenderId())
                .methodType(methodType)
                .provider(resolveProviderName(methodType))
                .amount(tx.getTotalDebitAmount())
                .currency(tx.getSendCurrency())
                .status(PaymentStatus.INITIATED)
                .build();

        // Generate provider reference based on method type
        String providerReference = generateProviderReference(methodType, payment);
        payment.setProviderReference(providerReference);

        PaymentEntity saved = paymentRepository.save(payment);

        // Update transaction with payment reference
        tx.setPaymentReference(providerReference);
        transactionRepository.save(tx);

        log.info("Payment initiated id={} for transactionId={}, method={}", saved.getId(), transactionId, methodType);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentStatus checkPaymentStatus(Long paymentId) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        return payment.getStatus();
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByTransaction(Long transactionId) {
        return paymentRepository.findByTransactionId(transactionId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void matchBankStatement(Long statementId, Long paymentId) {
        BankStatementEntity statement = bankStatementRepository.findById(statementId)
                .orElseThrow(() -> new ResourceNotFoundException("BankStatement", "id", statementId));

        PaymentEntity payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        if (statement.getStatus() == BankStatementStatus.MATCHED) {
            throw new RemitzException("Bank statement is already matched", HttpStatus.BAD_REQUEST);
        }

        // Match the statement to the payment
        statement.setStatus(BankStatementStatus.MATCHED);
        statement.setMatchedPaymentId(paymentId);
        bankStatementRepository.save(statement);

        // Update payment status
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        paymentRepository.save(payment);

        log.info("Bank statement id={} matched to payment id={}", statementId, paymentId);
    }

    private String resolveProviderName(PaymentMethodType methodType) {
        return switch (methodType) {
            case BANK_TRANSFER -> "MANUAL_BANK_TRANSFER";
            case CARD -> "CARD_PROCESSOR";
            case OPEN_BANKING -> "OPEN_BANKING_PROVIDER";
            case WALLET -> "WALLET_PROVIDER";
            case AGENT_CASH -> "AGENT_CASH_HANDLER";
        };
    }

    private String generateProviderReference(PaymentMethodType methodType, PaymentEntity payment) {
        return "FB-PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    private PaymentResponse mapToResponse(PaymentEntity entity) {
        return PaymentResponse.builder()
                .id(entity.getId())
                .transactionId(entity.getTransactionId())
                .methodType(entity.getMethodType())
                .provider(entity.getProvider())
                .providerReference(entity.getProviderReference())
                .amount(entity.getAmount())
                .currency(entity.getCurrency())
                .status(entity.getStatus())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}

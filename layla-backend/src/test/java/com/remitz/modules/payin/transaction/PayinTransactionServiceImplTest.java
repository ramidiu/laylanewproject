package com.remitz.modules.payin.transaction;

import com.remitz.common.enums.CreatedSource;
import com.remitz.common.enums.PayinTransactionStatus;
import com.remitz.modules.payin.customer.entity.PayinCustomerEntity;
import com.remitz.modules.payin.customer.repository.PayinCustomerRepository;
import com.remitz.modules.payin.transaction.dto.BeneficiaryDetailsDto;
import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionRequest;
import com.remitz.modules.payin.transaction.dto.CreatePayinTransactionResponse;
import com.remitz.modules.payin.transaction.entity.PayinBeneficiaryEntity;
import com.remitz.modules.payin.transaction.entity.PayinTransactionEntity;
import com.remitz.modules.payin.transaction.repository.PayinBeneficiaryRepository;
import com.remitz.modules.payin.transaction.repository.PayinTransactionRepository;
import com.remitz.modules.payin.transaction.service.PayinTransactionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayinTransactionServiceImplTest {

    @Mock private PayinCustomerRepository customerRepository;
    @Mock private PayinBeneficiaryRepository beneficiaryRepository;
    @Mock private PayinTransactionRepository transactionRepository;

    @InjectMocks
    private PayinTransactionServiceImpl service;

    private PayinCustomerEntity frontendCustomer;
    private PayinCustomerEntity backendCustomer;

    @BeforeEach
    void setUp() {
        frontendCustomer = PayinCustomerEntity.builder()
                .id(1L).customerId("cust-uuid-001").createdSource(CreatedSource.FRONTEND).build();
        backendCustomer = PayinCustomerEntity.builder()
                .id(2L).customerId("cust-uuid-002").createdSource(CreatedSource.BACKEND).build();
    }

    // ─── Existing Beneficiary Flow ────────────────────────────────────────────

    @Test
    void createTransaction_existingBeneficiary_success() {
        PayinBeneficiaryEntity beneficiary = beneficiary(10L, "cust-uuid-001");
        PayinTransactionEntity saved = savedTransaction("txn-001", CreatedSource.FRONTEND);

        when(transactionRepository.findByExternalReferenceId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByCustomerId("cust-uuid-001")).thenReturn(Optional.of(frontendCustomer));
        when(beneficiaryRepository.findByIdAndCustomerId(10L, "cust-uuid-001")).thenReturn(Optional.of(beneficiary));
        when(transactionRepository.save(any())).thenReturn(saved);

        CreatePayinTransactionResponse res = service.createTransaction(requestWithBeneficiaryId("cust-uuid-001", "10"));

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getTransactionId()).isEqualTo("txn-001");
        assertThat(res.getStatus()).isEqualTo("INITIATED");
        assertThat(res.getCustomerSource()).isEqualTo("FRONTEND");
        verify(beneficiaryRepository, never()).save(any());
    }

    // ─── New Beneficiary Flow ─────────────────────────────────────────────────

    @Test
    void createTransaction_newBeneficiary_createsAndLinks() {
        PayinBeneficiaryEntity newBen = beneficiary(99L, "cust-uuid-002");
        PayinTransactionEntity saved = savedTransaction("txn-002", CreatedSource.BACKEND);

        when(transactionRepository.findByExternalReferenceId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByCustomerId("cust-uuid-002")).thenReturn(Optional.of(backendCustomer));
        when(beneficiaryRepository.save(any())).thenReturn(newBen);
        when(transactionRepository.save(any())).thenReturn(saved);

        CreatePayinTransactionResponse res = service.createTransaction(requestWithNewBeneficiary("cust-uuid-002"));

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getCustomerSource()).isEqualTo("BACKEND");
        verify(beneficiaryRepository).save(any(PayinBeneficiaryEntity.class));
    }

    // ─── Invalid Customer ─────────────────────────────────────────────────────

    @Test
    void createTransaction_customerNotFound_returnsFailure() {
        when(transactionRepository.findByExternalReferenceId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByCustomerId("unknown-id")).thenReturn(Optional.empty());

        CreatePayinTransactionResponse res = service.createTransaction(requestWithBeneficiaryId("unknown-id", "10"));

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("Customer not found");
        verify(transactionRepository, never()).save(any());
    }

    // ─── Invalid Payment Mode ─────────────────────────────────────────────────

    @Test
    void createTransaction_invalidPaymentMode_returnsFailure() {
        when(transactionRepository.findByExternalReferenceId(anyString())).thenReturn(Optional.empty());

        CreatePayinTransactionRequest req = baseRequest("cust-uuid-001");
        req.setPaymentMode("CRYPTO");
        req.setBeneficiaryId("10");

        CreatePayinTransactionResponse res = service.createTransaction(req);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).isEqualTo("Invalid payment mode");
        verify(customerRepository, never()).findByCustomerId(any());
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────

    @Test
    void createTransaction_sameExternalReferenceId_returnsExisting() {
        PayinTransactionEntity existing = savedTransaction("txn-existing", CreatedSource.FRONTEND);

        when(transactionRepository.findByExternalReferenceId("EXT-REF-123")).thenReturn(Optional.of(existing));

        CreatePayinTransactionRequest req = baseRequest("cust-uuid-001");
        req.setBeneficiaryId("10");
        req.setExternalReferenceId("EXT-REF-123");

        CreatePayinTransactionResponse res = service.createTransaction(req);

        assertThat(res.isSuccess()).isTrue();
        assertThat(res.getTransactionId()).isEqualTo("txn-existing");
        verify(customerRepository, never()).findByCustomerId(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_noBeneficiaryProvided_returnsFailure() {
        when(transactionRepository.findByExternalReferenceId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByCustomerId("cust-uuid-001")).thenReturn(Optional.of(frontendCustomer));

        CreatePayinTransactionRequest req = baseRequest("cust-uuid-001");

        CreatePayinTransactionResponse res = service.createTransaction(req);

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).contains("beneficiary");
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void createTransaction_beneficiaryNotOwnedByCustomer_returnsFailure() {
        when(transactionRepository.findByExternalReferenceId(anyString())).thenReturn(Optional.empty());
        when(customerRepository.findByCustomerId("cust-uuid-001")).thenReturn(Optional.of(frontendCustomer));
        when(beneficiaryRepository.findByIdAndCustomerId(10L, "cust-uuid-001")).thenReturn(Optional.empty());

        CreatePayinTransactionResponse res = service.createTransaction(requestWithBeneficiaryId("cust-uuid-001", "10"));

        assertThat(res.isSuccess()).isFalse();
        assertThat(res.getMessage()).contains("Beneficiary not found");
        verify(transactionRepository, never()).save(any());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private CreatePayinTransactionRequest baseRequest(String customerId) {
        CreatePayinTransactionRequest req = new CreatePayinTransactionRequest();
        req.setCustomerId(customerId);
        req.setAmount(new BigDecimal("100.00"));
        req.setCurrency("GBP");
        req.setPaymentMode("CASH_COLLECTION");
        req.setExternalReferenceId("EXT-" + System.nanoTime());
        return req;
    }

    private CreatePayinTransactionRequest requestWithBeneficiaryId(String customerId, String beneficiaryId) {
        CreatePayinTransactionRequest req = baseRequest(customerId);
        req.setBeneficiaryId(beneficiaryId);
        return req;
    }

    private CreatePayinTransactionRequest requestWithNewBeneficiary(String customerId) {
        CreatePayinTransactionRequest req = baseRequest(customerId);
        req.setBeneficiaryDetails(BeneficiaryDetailsDto.builder()
                .name("John Doe").bankName("HDFC Bank")
                .accountNumber("1234567890").ifscCode("HDFC0001234")
                .build());
        return req;
    }

    private PayinBeneficiaryEntity beneficiary(Long id, String customerId) {
        return PayinBeneficiaryEntity.builder().id(id).customerId(customerId)
                .name("Test Ben").bankName("Test Bank").accountNumber("123").build();
    }

    private PayinTransactionEntity savedTransaction(String txnId, CreatedSource source) {
        return PayinTransactionEntity.builder()
                .id(1L).transactionId(txnId)
                .customerId("cust-uuid-001").customerSource(source)
                .beneficiaryId(10L).amount(new BigDecimal("100.00"))
                .currency("GBP").status(PayinTransactionStatus.INITIATED)
                .build();
    }
}

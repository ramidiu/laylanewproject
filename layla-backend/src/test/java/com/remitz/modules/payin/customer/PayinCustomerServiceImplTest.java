package com.remitz.modules.payin.customer;

import com.remitz.common.enums.CreatedSource;
import com.remitz.modules.payin.customer.dto.CreateCustomerRequest;
import com.remitz.modules.payin.customer.dto.CreateCustomerResponse;
import com.remitz.modules.payin.customer.entity.PayinCustomerEntity;
import com.remitz.modules.payin.customer.mapper.PayinCustomerMapper;
import com.remitz.modules.payin.customer.repository.PayinCustomerRepository;
import com.remitz.modules.payin.customer.service.PayinCustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PayinCustomerServiceImplTest {

    @Mock
    private PayinCustomerRepository repository;

    @Mock
    private PayinCustomerMapper mapper;

    @InjectMocks
    private PayinCustomerServiceImpl service;

    private CreateCustomerRequest validRequest;

    @BeforeEach
    void setUp() {
        validRequest = CreateCustomerRequest.builder()
                .firstName("John")
                .lastName("Doe")
                .email("john.doe@example.com")
                .phone("+441234567890")
                .dob(LocalDate.of(1990, 5, 15))
                .nationality("British")
                .addressLine1("10 Downing Street")
                .city("London")
                .country("GB")
                .postalCode("SW1A 2AA")
                .build();
    }

    @Test
    void createCustomer_success() {
        PayinCustomerEntity entity = PayinCustomerEntity.builder()
                .customerId("test-uuid-1234")
                .isVerified(true)
                .createdSource(CreatedSource.BACKEND)
                .build();

        when(repository.existsByEmail("john.doe@example.com")).thenReturn(false);
        when(mapper.toEntity(validRequest)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);

        CreateCustomerResponse response = service.createCustomer(validRequest);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getCustomerId()).isEqualTo("test-uuid-1234");
        assertThat(response.getIsVerified()).isTrue();
        assertThat(response.getCreatedSource()).isEqualTo("BACKEND");
        assertThat(response.getMessage()).isEqualTo("Customer created successfully");

        verify(repository).existsByEmail("john.doe@example.com");
        verify(repository).save(entity);
    }

    @Test
    void createCustomer_duplicateEmail_returnsFailure() {
        when(repository.existsByEmail("john.doe@example.com")).thenReturn(true);

        CreateCustomerResponse response = service.createCustomer(validRequest);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Email already registered");
        assertThat(response.getCustomerId()).isNull();

        verify(repository).existsByEmail("john.doe@example.com");
        verify(repository, never()).save(any());
        verify(mapper, never()).toEntity(any());
    }

    @Test
    void createCustomer_emailNormalisedToLowercase() {
        CreateCustomerRequest requestWithUpperEmail = CreateCustomerRequest.builder()
                .firstName("Jane")
                .lastName("Smith")
                .email("Jane.Smith@Example.COM")
                .phone("+441234567891")
                .dob(LocalDate.of(1985, 3, 20))
                .nationality("British")
                .addressLine1("1 Test Street")
                .city("Manchester")
                .country("GB")
                .postalCode("M1 1AA")
                .build();

        PayinCustomerEntity entity = PayinCustomerEntity.builder()
                .customerId("uuid-5678")
                .isVerified(true)
                .createdSource(CreatedSource.BACKEND)
                .build();

        when(repository.existsByEmail("jane.smith@example.com")).thenReturn(false);
        when(mapper.toEntity(requestWithUpperEmail)).thenReturn(entity);
        when(repository.save(entity)).thenReturn(entity);

        CreateCustomerResponse response = service.createCustomer(requestWithUpperEmail);

        assertThat(response.isSuccess()).isTrue();
        verify(repository).existsByEmail("jane.smith@example.com");
    }
}

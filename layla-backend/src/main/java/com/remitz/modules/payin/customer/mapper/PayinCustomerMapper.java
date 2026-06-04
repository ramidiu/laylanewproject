package com.remitz.modules.payin.customer.mapper;

import com.remitz.common.enums.CreatedSource;
import com.remitz.modules.payin.customer.dto.CreateCustomerRequest;
import com.remitz.modules.payin.customer.entity.PayinCustomerEntity;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PayinCustomerMapper {

    public PayinCustomerEntity toEntity(CreateCustomerRequest request) {
        return PayinCustomerEntity.builder()
                .customerId(UUID.randomUUID().toString())
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .phone(request.getPhone().trim())
                .dob(request.getDob())
                .nationality(request.getNationality().trim())
                .addressLine1(request.getAddressLine1().trim())
                .city(request.getCity().trim())
                .country(request.getCountry().trim())
                .postalCode(request.getPostalCode().trim())
                .isVerified(true)
                .createdSource(parseSource(request.getCreatedSource()))
                .build();
    }

    private CreatedSource parseSource(String raw) {
        if (raw != null) {
            try { return CreatedSource.valueOf(raw.trim().toUpperCase()); } catch (IllegalArgumentException ignored) {}
        }
        return CreatedSource.BACKEND;
    }
}

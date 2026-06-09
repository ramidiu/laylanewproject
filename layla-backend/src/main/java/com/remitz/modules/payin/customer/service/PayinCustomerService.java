package com.remitz.modules.payin.customer.service;

import com.remitz.modules.payin.customer.dto.CreateCustomerRequest;
import com.remitz.modules.payin.customer.dto.CreateCustomerResponse;
import com.remitz.modules.payin.customer.dto.PayinCustomerDto;

import java.time.LocalDate;
import java.util.List;

public interface PayinCustomerService {

    CreateCustomerResponse createCustomer(CreateCustomerRequest request);

    List<PayinCustomerDto> listCustomers();

    /** Returns payin_customers + UK frontend users combined. */
    List<PayinCustomerDto> listAllCustomers();

    /** Toggles users.payin_enabled for a frontend user. Returns updated flag value. */
    boolean toggleFrontendUserPayin(Long userId);

    /** Update an existing payin customer's profile (currently DOB + isVerified). */
    PayinCustomerDto updateProfile(String customerId, LocalDate dob, Boolean isVerified);

    /** One-off backfill: (re)provision login accounts with the default password +
     *  force-change flag for every existing pay-in customer. Returns the number processed. */
    int backfillLoginAccounts();
}

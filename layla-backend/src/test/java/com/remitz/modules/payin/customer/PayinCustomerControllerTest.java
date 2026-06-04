package com.remitz.modules.payin.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.remitz.modules.payin.customer.controller.PayinCustomerController;
import com.remitz.modules.payin.customer.dto.CreateCustomerRequest;
import com.remitz.modules.payin.customer.dto.CreateCustomerResponse;
import com.remitz.modules.payin.customer.service.PayinCustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayinCustomerController.class)
class PayinCustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PayinCustomerService service;

    @Test
    @WithMockUser(roles = "PAYIN_PARTNER")
    void createCustomer_validRequest_returns200() throws Exception {
        CreateCustomerRequest request = validRequest();
        when(service.createCustomer(any())).thenReturn(CreateCustomerResponse.success("uuid-abc-123"));

        mockMvc.perform(post("/api/payin/customer/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.customerId").value("uuid-abc-123"))
                .andExpect(jsonPath("$.isVerified").value(true))
                .andExpect(jsonPath("$.createdSource").value("BACKEND"))
                .andExpect(jsonPath("$.message").value("Customer created successfully"));
    }

    @Test
    @WithMockUser(roles = "PAYIN_PARTNER")
    void createCustomer_missingFirstName_returns400() throws Exception {
        CreateCustomerRequest request = validRequest();
        request.setFirstName("");

        mockMvc.perform(post("/api/payin/customer/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @WithMockUser(roles = "PAYIN_PARTNER")
    void createCustomer_invalidEmail_returns400() throws Exception {
        CreateCustomerRequest request = validRequest();
        request.setEmail("not-a-valid-email");

        mockMvc.perform(post("/api/payin/customer/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email format"));
    }

    @Test
    @WithMockUser(roles = "PAYIN_PARTNER")
    void createCustomer_duplicateEmail_returnsFailureWithSuccess200() throws Exception {
        CreateCustomerRequest request = validRequest();
        when(service.createCustomer(any())).thenReturn(CreateCustomerResponse.failure("Email already registered"));

        mockMvc.perform(post("/api/payin/customer/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"))
                .andExpect(jsonPath("$.customerId").doesNotExist());
    }

    @Test
    void createCustomer_unauthenticated_returns401or403() throws Exception {
        mockMvc.perform(post("/api/payin/customer/create")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().is4xxClientError());
    }

    private CreateCustomerRequest validRequest() {
        return CreateCustomerRequest.builder()
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
}

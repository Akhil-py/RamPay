package com.rampay.paymentservice.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rampay.paymentservice.dto.CreatePaymentRequest;
import com.rampay.paymentservice.dto.FailPaymentRequest;
import com.rampay.paymentservice.dto.PaymentResponse;
import com.rampay.paymentservice.exceptions.DuplicatePaymentException;
import com.rampay.paymentservice.exceptions.InvalidPaymentStatusException;
import com.rampay.paymentservice.exceptions.PaymentNotFoundException;
import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
import com.rampay.paymentservice.services.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for PaymentController.
 * Tests all REST endpoints with various scenarios.
 */
@WebMvcTest(PaymentController.class)
@DisplayName("PaymentController Tests")
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private UUID paymentId;
    private UUID fromAccountId;
    private UUID toAccountId;
    private Payment payment;
    private CreatePaymentRequest createPaymentRequest;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();
        fromAccountId = UUID.randomUUID();
        toAccountId = UUID.randomUUID();

        payment = new Payment();
        payment.setId(paymentId.toString());
        payment.setFromAccountId(fromAccountId.toString());
        payment.setToAccountId(toAccountId.toString());
        payment.setAmount("100.00");
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING.name());
        payment.setCreatedAt(Instant.now().toString());
        payment.setUpdatedAt(Instant.now().toString());

        createPaymentRequest = new CreatePaymentRequest();
        createPaymentRequest.setFromAccountId(fromAccountId);
        createPaymentRequest.setToAccountId(toAccountId);
        createPaymentRequest.setAmount(new BigDecimal("100.00"));
        createPaymentRequest.setCurrency("USD");
    }

    @Test
    @DisplayName("POST /payments - Should create payment successfully")
    void testCreatePayment_Success() throws Exception {
        when(paymentService.createPayment(any(CreatePaymentRequest.class), isNull()))
                .thenReturn(payment);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPaymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.fromAccountId").value(fromAccountId.toString()))
                .andExpect(jsonPath("$.toAccountId").value(toAccountId.toString()))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(paymentService, times(1)).createPayment(any(CreatePaymentRequest.class), isNull());
    }

    @Test
    @DisplayName("POST /payments - Should create payment with idempotency key")
    void testCreatePayment_WithIdempotencyKey() throws Exception {
        String idempotencyKey = "test-idempotency-key-123";
        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey)))
                .thenReturn(payment);

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPaymentRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(paymentId.toString()));

        verify(paymentService, times(1)).createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey));
    }

    @Test
    @DisplayName("POST /payments - Should return 409 when duplicate idempotency key")
    void testCreatePayment_DuplicateIdempotencyKey() throws Exception {
        String idempotencyKey = "test-idempotency-key-123";
        when(paymentService.createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey)))
                .thenThrow(new DuplicatePaymentException("Payment already processed with this idempotency key"));

        mockMvc.perform(post("/payments")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPaymentRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Payment already processed with this idempotency key"));

        verify(paymentService, times(1)).createPayment(any(CreatePaymentRequest.class), eq(idempotencyKey));
    }

    @Test
    @DisplayName("POST /payments - Should return 400 when validation fails")
    void testCreatePayment_ValidationError() throws Exception {
        CreatePaymentRequest invalidRequest = new CreatePaymentRequest();
        // Missing required fields
        invalidRequest.setAmount(new BigDecimal("-10.00")); // Invalid amount
        invalidRequest.setCurrency("INVALID"); // Invalid currency

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).createPayment(any(), any());
    }

    @Test
    @DisplayName("POST /payments - Should return 400 when fromAccountId equals toAccountId")
    void testCreatePayment_SameAccounts() throws Exception {
        UUID accountId = UUID.randomUUID();
        createPaymentRequest.setFromAccountId(accountId);
        createPaymentRequest.setToAccountId(accountId);

        mockMvc.perform(post("/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createPaymentRequest)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).createPayment(any(), any());
    }

    @Test
    @DisplayName("GET /payments - Should return paginated list of payments")
    void testGetAllPayments_Success() throws Exception {
        Payment payment2 = new Payment();
        payment2.setId(UUID.randomUUID().toString());
        payment2.setFromAccountId(fromAccountId.toString());
        payment2.setToAccountId(toAccountId.toString());
        payment2.setAmount("200.00");
        payment2.setCurrency("EUR");
        payment2.setStatus(PaymentStatus.APPROVED.name());
        payment2.setCreatedAt(Instant.now().toString());
        payment2.setUpdatedAt(Instant.now().toString());

        List<Payment> payments = List.of(payment, payment2);
        Page<Payment> page = new PageImpl<>(payments, PageRequest.of(0, 20), 2);

        when(paymentService.getAllPayments(0, 20)).thenReturn(page);

        mockMvc.perform(get("/payments")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.pageable.pageNumber").value(0))
                .andExpect(jsonPath("$.pageable.pageSize").value(20))
                .andExpect(jsonPath("$.pageable.totalElements").value(2));

        verify(paymentService, times(1)).getAllPayments(0, 20);
    }

    @Test
    @DisplayName("GET /payments - Should return empty list when no payments")
    void testGetAllPayments_Empty() throws Exception {
        Page<Payment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(paymentService.getAllPayments(0, 20)).thenReturn(emptyPage);

        mockMvc.perform(get("/payments")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.pageable.totalElements").value(0));

        verify(paymentService, times(1)).getAllPayments(0, 20);
    }

    @Test
    @DisplayName("GET /payments/{id} - Should return payment by ID")
    void testGetPaymentById_Success() throws Exception {
        when(paymentService.getPayment(paymentId)).thenReturn(payment);

        mockMvc.perform(get("/payments/{id}", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.fromAccountId").value(fromAccountId.toString()))
                .andExpect(jsonPath("$.toAccountId").value(toAccountId.toString()))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.status").value("PENDING"));

        verify(paymentService, times(1)).getPayment(paymentId);
    }

    @Test
    @DisplayName("GET /payments/{id} - Should return 404 when payment not found")
    void testGetPaymentById_NotFound() throws Exception {
        UUID nonExistentId = UUID.randomUUID();
        when(paymentService.getPayment(nonExistentId)).thenReturn(null);

        mockMvc.perform(get("/payments/{id}", nonExistentId))
                .andExpect(status().isNotFound());

        verify(paymentService, times(1)).getPayment(nonExistentId);
    }

    @Test
    @DisplayName("PUT /payments/{id}/approve - Should approve payment successfully")
    void testApprovePayment_Success() throws Exception {
        Payment approvedPayment = new Payment();
        approvedPayment.setId(paymentId.toString());
        approvedPayment.setFromAccountId(fromAccountId.toString());
        approvedPayment.setToAccountId(toAccountId.toString());
        approvedPayment.setAmount("100.00");
        approvedPayment.setCurrency("USD");
        approvedPayment.setStatus(PaymentStatus.APPROVED.name());
        approvedPayment.setCreatedAt(Instant.now().toString());
        approvedPayment.setUpdatedAt(Instant.now().toString());

        when(paymentService.approvePayment(paymentId)).thenReturn(approvedPayment);

        mockMvc.perform(put("/payments/{id}/approve", paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        verify(paymentService, times(1)).approvePayment(paymentId);
    }

    @Test
    @DisplayName("PUT /payments/{id}/approve - Should return 400 when payment status is invalid")
    void testApprovePayment_InvalidStatus() throws Exception {
        when(paymentService.approvePayment(paymentId))
                .thenThrow(new InvalidPaymentStatusException("Cannot approve payment with status: APPROVED"));

        mockMvc.perform(put("/payments/{id}/approve", paymentId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Cannot approve payment with status: APPROVED"));

        verify(paymentService, times(1)).approvePayment(paymentId);
    }

    @Test
    @DisplayName("PUT /payments/{id}/fail - Should fail payment successfully")
    void testFailPayment_Success() throws Exception {
        Payment failedPayment = new Payment();
        failedPayment.setId(paymentId.toString());
        failedPayment.setFromAccountId(fromAccountId.toString());
        failedPayment.setToAccountId(toAccountId.toString());
        failedPayment.setAmount("100.00");
        failedPayment.setCurrency("USD");
        failedPayment.setStatus(PaymentStatus.FAILED.name());
        failedPayment.setFailureReason("Insufficient funds");
        failedPayment.setCreatedAt(Instant.now().toString());
        failedPayment.setUpdatedAt(Instant.now().toString());

        FailPaymentRequest failRequest = new FailPaymentRequest();
        failRequest.setReason("Insufficient funds");

        when(paymentService.failPayment(paymentId, "Insufficient funds")).thenReturn(failedPayment);

        mockMvc.perform(put("/payments/{id}/fail", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Insufficient funds"));

        verify(paymentService, times(1)).failPayment(paymentId, "Insufficient funds");
    }

    @Test
    @DisplayName("PUT /payments/{id}/fail - Should return 400 when validation fails")
    void testFailPayment_ValidationError() throws Exception {
        FailPaymentRequest failRequest = new FailPaymentRequest();
        // Missing required reason

        mockMvc.perform(put("/payments/{id}/fail", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(failRequest)))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).failPayment(any(), any());
    }

    @Test
    @DisplayName("POST /payments/{id}/refund - Should refund payment successfully")
    void testRefundPayment_Success() throws Exception {
        Payment refundedPayment = new Payment();
        refundedPayment.setId(paymentId.toString());
        refundedPayment.setFromAccountId(fromAccountId.toString());
        refundedPayment.setToAccountId(toAccountId.toString());
        refundedPayment.setAmount("100.00");
        refundedPayment.setCurrency("USD");
        refundedPayment.setStatus(PaymentStatus.REFUNDED.name());
        refundedPayment.setRefundAmount("50.00");
        refundedPayment.setCreatedAt(Instant.now().toString());
        refundedPayment.setUpdatedAt(Instant.now().toString());

        when(paymentService.refundPayment(paymentId, new BigDecimal("50.00"))).thenReturn(refundedPayment);

        mockMvc.perform(post("/payments/{id}/refund", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAmount\": 50.00}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.status").value("REFUNDED"))
                .andExpect(jsonPath("$.refundAmount").value(50.00));

        verify(paymentService, times(1)).refundPayment(paymentId, new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("POST /payments/{id}/refund - Should return 400 when validation fails")
    void testRefundPayment_ValidationError() throws Exception {
        mockMvc.perform(post("/payments/{id}/refund", paymentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundAmount\": -10.00}"))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).refundPayment(any(), any());
    }

    @Test
    @DisplayName("GET /payments/account/{accountId} - Should return payments by account")
    void testGetPaymentsByAccount_Success() throws Exception {
        List<Payment> payments = List.of(payment);
        Page<Payment> page = new PageImpl<>(payments, PageRequest.of(0, 20), 1);

        when(paymentService.getPaymentsByAccount(fromAccountId, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/payments/account/{accountId}", fromAccountId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].fromAccountId").value(fromAccountId.toString()));

        verify(paymentService, times(1)).getPaymentsByAccount(fromAccountId, 0, 20);
    }

    @Test
    @DisplayName("GET /payments/account/{accountId} - Should return empty list when no payments for account")
    void testGetPaymentsByAccount_Empty() throws Exception {
        UUID accountId = UUID.randomUUID();
        Page<Payment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(paymentService.getPaymentsByAccount(accountId, 0, 20)).thenReturn(emptyPage);

        mockMvc.perform(get("/payments/account/{accountId}", accountId)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(paymentService, times(1)).getPaymentsByAccount(accountId, 0, 20);
    }

    @Test
    @DisplayName("GET /payments/status/{status} - Should return payments by status")
    void testGetPaymentsByStatus_Success() throws Exception {
        List<Payment> payments = List.of(payment);
        Page<Payment> page = new PageImpl<>(payments, PageRequest.of(0, 20), 1);

        when(paymentService.getPaymentsByStatus(PaymentStatus.PENDING, 0, 20)).thenReturn(page);

        mockMvc.perform(get("/payments/status/{status}", "PENDING")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));

        verify(paymentService, times(1)).getPaymentsByStatus(PaymentStatus.PENDING, 0, 20);
    }

    @Test
    @DisplayName("GET /payments/status/{status} - Should return empty list when no payments with status")
    void testGetPaymentsByStatus_Empty() throws Exception {
        Page<Payment> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);

        when(paymentService.getPaymentsByStatus(PaymentStatus.REFUNDED, 0, 20)).thenReturn(emptyPage);

        mockMvc.perform(get("/payments/status/{status}", "REFUNDED")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0));

        verify(paymentService, times(1)).getPaymentsByStatus(PaymentStatus.REFUNDED, 0, 20);
    }

    @Test
    @DisplayName("GET /payments - Should limit size to maximum of 100")
    void testGetAllPayments_MaxSize() throws Exception {
        List<Payment> payments = List.of(payment);
        Page<Payment> page = new PageImpl<>(payments, PageRequest.of(0, 100), 1);

        when(paymentService.getAllPayments(0, 200)).thenReturn(page);

        mockMvc.perform(get("/payments")
                        .param("page", "0")
                        .param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageable.pageSize").value(100));

        verify(paymentService, times(1)).getAllPayments(0, 200);
    }
}

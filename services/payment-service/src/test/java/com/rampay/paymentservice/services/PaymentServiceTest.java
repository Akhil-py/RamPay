package com.rampay.paymentservice.services;

import com.rampay.paymentservice.dto.CreatePaymentRequest;
import com.rampay.paymentservice.exceptions.DuplicatePaymentException;
import com.rampay.paymentservice.exceptions.InvalidPaymentAmountException;
import com.rampay.paymentservice.exceptions.InvalidPaymentStatusException;
import com.rampay.paymentservice.exceptions.PaymentNotFoundException;
import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
import com.rampay.paymentservice.repositories.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentService.
 * Tests business logic with various scenarios.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private RedisCacheService redisCacheService;

    @Mock
    private EventPublisherService eventPublisherService;

    @InjectMocks
    private PaymentService paymentService;

    private UUID paymentUuid;
    private String paymentId;
    private String fromAccountId;
    private String toAccountId;
    private Payment payment;
    private CreatePaymentRequest createPaymentRequest;

    @BeforeEach
    void setUp() {
        paymentUuid = UUID.randomUUID();
        paymentId = paymentUuid.toString();
        fromAccountId = UUID.randomUUID().toString();
        toAccountId = UUID.randomUUID().toString();

        payment = new Payment();
        payment.setId(paymentId);
        payment.setFromAccountId(fromAccountId);
        payment.setToAccountId(toAccountId);
        payment.setAmount("100.00");
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING.name());
        payment.setCreatedAt(Instant.now().toString());
        payment.setUpdatedAt(Instant.now().toString());

        createPaymentRequest = new CreatePaymentRequest();
        createPaymentRequest.setFromAccountId(UUID.fromString(fromAccountId));
        createPaymentRequest.setToAccountId(UUID.fromString(toAccountId));
        createPaymentRequest.setAmount(new BigDecimal("100.00"));
        createPaymentRequest.setCurrency("USD");
    }

    @Test
    @DisplayName("createPayment - Should create payment successfully without idempotency key")
    void testCreatePayment_Success() {
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        Payment result = paymentService.createPayment(createPaymentRequest, null);

        assertNotNull(result);
        assertEquals(paymentId, result.getId());
        assertEquals(fromAccountId, result.getFromAccountId());
        assertEquals(toAccountId, result.getToAccountId());
        assertEquals("100.00", result.getAmount());
        assertEquals("USD", result.getCurrency());
        assertEquals(PaymentStatus.PENDING.name(), result.getStatus());

        verify(redisCacheService, never()).checkIdempotency(anyString());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(redisCacheService, times(1)).cachePayment(any(Payment.class));
        verify(redisCacheService, never()).recordIdempotency(anyString(), anyString());
        verify(eventPublisherService, times(1)).publishPaymentCreated(
                anyString(), anyString(), anyString(), any(BigDecimal.class), eq("USD"));
    }

    @Test
    @DisplayName("createPayment - Should create payment with idempotency key")
    void testCreatePayment_WithIdempotencyKey() {
        String idempotencyKey = "test-idempotency-key-123";
        when(redisCacheService.checkIdempotency(idempotencyKey)).thenReturn(null);
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        Payment result = paymentService.createPayment(createPaymentRequest, idempotencyKey);

        assertNotNull(result);
        assertEquals(paymentId, result.getId());

        verify(redisCacheService, times(1)).checkIdempotency(idempotencyKey);
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(redisCacheService, times(1)).cachePayment(any(Payment.class));
        verify(redisCacheService, times(1)).recordIdempotency(eq(idempotencyKey), anyString());
        verify(eventPublisherService, times(1)).publishPaymentCreated(
                anyString(), anyString(), anyString(), any(BigDecimal.class), eq("USD"));
    }

    @Test
    @DisplayName("createPayment - Should throw DuplicatePaymentException when idempotency key exists")
    void testCreatePayment_DuplicateIdempotencyKey() {
        String idempotencyKey = "test-idempotency-key-123";

        when(redisCacheService.checkIdempotency(idempotencyKey)).thenReturn(paymentId);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        DuplicatePaymentException exception = assertThrows(
                DuplicatePaymentException.class,
                () -> paymentService.createPayment(createPaymentRequest, idempotencyKey)
        );

        assertEquals("Payment already processed with this idempotency key", exception.getMessage());

        verify(redisCacheService, times(1)).checkIdempotency(idempotencyKey);
        verify(paymentRepository, times(1)).findById(paymentId);
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(redisCacheService, never()).cachePayment(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentCreated(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createPayment - Should handle blank idempotency key")
    void testCreatePayment_BlankIdempotencyKey() {
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);

        Payment result = paymentService.createPayment(createPaymentRequest, "   ");

        assertNotNull(result);
        assertEquals(paymentId, result.getId());

        verify(redisCacheService, never()).checkIdempotency(anyString());
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(redisCacheService, times(1)).cachePayment(any(Payment.class));
        verify(redisCacheService, never()).recordIdempotency(anyString(), anyString());
    }

    @Test
    @DisplayName("approvePayment - Should approve payment successfully")
    void testApprovePayment_Success() {
        Payment approvedPayment = new Payment();
        approvedPayment.setId(paymentId);
        approvedPayment.setFromAccountId(fromAccountId);
        approvedPayment.setToAccountId(toAccountId);
        approvedPayment.setAmount("100.00");
        approvedPayment.setCurrency("USD");
        approvedPayment.setStatus(PaymentStatus.APPROVED.name());
        approvedPayment.setCreatedAt(Instant.now().toString());
        approvedPayment.setUpdatedAt(Instant.now().toString());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(approvedPayment);

        Payment result = paymentService.approvePayment(paymentUuid);

        assertNotNull(result);
        assertEquals(PaymentStatus.APPROVED.name(), result.getStatus());

        verify(paymentRepository, times(1)).findById(paymentId);
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(redisCacheService, times(1)).cachePayment(any(Payment.class));
        verify(eventPublisherService, times(1)).publishPaymentApproved(paymentId);
    }

    @Test
    @DisplayName("approvePayment - Should throw InvalidPaymentStatusException when payment is not PENDING")
    void testApprovePayment_InvalidStatus() {
        payment.setStatus(PaymentStatus.APPROVED.name());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        InvalidPaymentStatusException exception = assertThrows(
                InvalidPaymentStatusException.class,
                () -> paymentService.approvePayment(paymentUuid)
        );

        assertEquals("Cannot approve payment with status: APPROVED", exception.getMessage());

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentApproved(any());
    }

    @Test
    @DisplayName("failPayment - Should fail payment successfully")
    void testFailPayment_Success() {
        Payment failedPayment = new Payment();
        failedPayment.setId(paymentId);
        failedPayment.setFromAccountId(fromAccountId);
        failedPayment.setToAccountId(toAccountId);
        failedPayment.setAmount("100.00");
        failedPayment.setCurrency("USD");
        failedPayment.setStatus(PaymentStatus.FAILED.name());
        failedPayment.setFailureReason("Insufficient funds");
        failedPayment.setCreatedAt(Instant.now().toString());
        failedPayment.setUpdatedAt(Instant.now().toString());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);

        Payment result = paymentService.failPayment(paymentUuid, "Insufficient funds");

        assertNotNull(result);
        assertEquals(PaymentStatus.FAILED.name(), result.getStatus());
        assertEquals("Insufficient funds", result.getFailureReason());

        verify(paymentRepository, times(1)).findById(paymentId);
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(redisCacheService, times(1)).cachePayment(any(Payment.class));
        verify(eventPublisherService, times(1)).publishPaymentFailed(paymentId, "Insufficient funds");
    }

    @Test
    @DisplayName("failPayment - Should throw InvalidPaymentStatusException when payment is not PENDING")
    void testFailPayment_InvalidStatus() {
        payment.setStatus(PaymentStatus.APPROVED.name());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        InvalidPaymentStatusException exception = assertThrows(
                InvalidPaymentStatusException.class,
                () -> paymentService.failPayment(paymentUuid, "Test reason")
        );

        assertEquals("Cannot fail payment with status: APPROVED", exception.getMessage());

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentFailed(any(), any());
    }

    @Test
    @DisplayName("refundPayment - Should refund payment successfully")
    void testRefundPayment_Success() {
        payment.setStatus(PaymentStatus.APPROVED.name());

        Payment refundedPayment = new Payment();
        refundedPayment.setId(paymentId);
        refundedPayment.setStatus(PaymentStatus.REFUNDED.name());
        refundedPayment.setRefundAmount("50.00");
        refundedPayment.setAmount("100.00");
        refundedPayment.setCreatedAt(Instant.now().toString());
        refundedPayment.setUpdatedAt(Instant.now().toString());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(refundedPayment);

        Payment result = paymentService.refundPayment(paymentUuid, new BigDecimal("50.00"));

        assertNotNull(result);
        assertEquals(PaymentStatus.REFUNDED.name(), result.getStatus());
        assertEquals("50.00", result.getRefundAmount());

        verify(paymentRepository, times(1)).findById(paymentId);
        verify(paymentRepository, times(1)).save(any(Payment.class));
        verify(redisCacheService, times(1)).cachePayment(any(Payment.class));
        verify(eventPublisherService, times(1)).publishPaymentRefunded(paymentId, new BigDecimal("50.00"));
    }

    @Test
    @DisplayName("refundPayment - Should throw InvalidPaymentStatusException when payment is not APPROVED")
    void testRefundPayment_InvalidStatus() {
        payment.setStatus(PaymentStatus.PENDING.name());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        InvalidPaymentStatusException exception = assertThrows(
                InvalidPaymentStatusException.class,
                () -> paymentService.refundPayment(paymentUuid, new BigDecimal("50.00"))
        );

        assertEquals("Cannot refund payment with status: PENDING", exception.getMessage());

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentRefunded(any(), any());
    }

    @Test
    @DisplayName("refundPayment - Should throw InvalidPaymentAmountException when refund exceeds original amount")
    void testRefundPayment_ExceedsOriginalAmount() {
        payment.setStatus(PaymentStatus.APPROVED.name());

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        InvalidPaymentAmountException exception = assertThrows(
                InvalidPaymentAmountException.class,
                () -> paymentService.refundPayment(paymentUuid, new BigDecimal("150.00"))
        );

        assertEquals("Refund amount cannot exceed original amount", exception.getMessage());

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentRefunded(any(), any());
    }

    @Test
    @DisplayName("getPayment - Should return payment from cache (cache hit)")
    void testGetPayment_CacheHit() {
        when(redisCacheService.getPayment(paymentId)).thenReturn(payment);

        Payment result = paymentService.getPayment(paymentUuid);

        assertNotNull(result);
        assertEquals(paymentId, result.getId());

        verify(redisCacheService, times(1)).getPayment(paymentId);
        verify(paymentRepository, never()).findById(any());
        verify(redisCacheService, never()).cachePayment(any());
    }

    @Test
    @DisplayName("getPayment - Should return payment from database (cache miss)")
    void testGetPayment_CacheMiss() {
        when(redisCacheService.getPayment(paymentId)).thenReturn(null);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        Payment result = paymentService.getPayment(paymentUuid);

        assertNotNull(result);
        assertEquals(paymentId, result.getId());

        verify(redisCacheService, times(1)).getPayment(paymentId);
        verify(paymentRepository, times(1)).findById(paymentId);
        verify(redisCacheService, times(1)).cachePayment(payment);
    }

    @Test
    @DisplayName("getPayment - Should throw PaymentNotFoundException when payment not found")
    void testGetPayment_NotFound() {
        when(redisCacheService.getPayment(paymentId)).thenReturn(null);
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class, () -> paymentService.getPayment(paymentUuid));

        verify(redisCacheService, times(1)).getPayment(paymentId);
        verify(paymentRepository, times(1)).findById(paymentId);
        verify(redisCacheService, never()).cachePayment(any());
    }

    @Test
    @DisplayName("getAllPayments - Should return page of payments via scan")
    void testGetAllPayments_Success() {
        when(paymentRepository.scan(anyInt())).thenReturn(List.of(payment));

        Page<Payment> result = paymentService.getAllPayments(0, 20);

        assertNotNull(result);
        assertFalse(result.getContent().isEmpty());
        verify(paymentRepository, times(1)).scan(anyInt());
    }

    @Test
    @DisplayName("getPaymentsByAccount - Should return payments for account")
    void testGetPaymentsByAccount_Success() {
        when(paymentRepository.findByFromAccountId(eq(fromAccountId), anyInt(), isNull()))
                .thenReturn(List.of(payment));

        Page<Payment> result = paymentService.getPaymentsByAccount(
                UUID.fromString(fromAccountId), 0, 20);

        assertNotNull(result);
        assertFalse(result.getContent().isEmpty());
        verify(paymentRepository, times(1))
                .findByFromAccountId(eq(fromAccountId), anyInt(), isNull());
    }

    @Test
    @DisplayName("getPaymentsByStatus - Should return payments for status")
    void testGetPaymentsByStatus_Success() {
        when(paymentRepository.findByStatus(eq(PaymentStatus.PENDING), anyInt(), isNull()))
                .thenReturn(List.of(payment));

        Page<Payment> result = paymentService.getPaymentsByStatus(PaymentStatus.PENDING, 0, 20);

        assertNotNull(result);
        assertFalse(result.getContent().isEmpty());
        verify(paymentRepository, times(1))
                .findByStatus(eq(PaymentStatus.PENDING), anyInt(), isNull());
    }

    @Test
    @DisplayName("approvePayment - Should throw PaymentNotFoundException when payment not found")
    void testApprovePayment_NotFound() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.approvePayment(paymentUuid));

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentApproved(any());
    }

    @Test
    @DisplayName("failPayment - Should throw PaymentNotFoundException when payment not found")
    void testFailPayment_NotFound() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.failPayment(paymentUuid, "Test reason"));

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentFailed(any(), any());
    }

    @Test
    @DisplayName("refundPayment - Should throw PaymentNotFoundException when payment not found")
    void testRefundPayment_NotFound() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());

        assertThrows(PaymentNotFoundException.class,
                () -> paymentService.refundPayment(paymentUuid, new BigDecimal("50.00")));

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(eventPublisherService, never()).publishPaymentRefunded(any(), any());
    }
}

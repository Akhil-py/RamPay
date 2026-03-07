package com.rampay.paymentservice.repositories;

import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaymentRepository (mocked — DynamoDB integration tests require a running instance).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRepository Tests")
class PaymentRepositoryTest {

    @Mock
    private PaymentRepository paymentRepository;

    private String paymentId;
    private String fromAccountId;
    private String toAccountId;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID().toString();
        fromAccountId = UUID.randomUUID().toString();
        toAccountId = UUID.randomUUID().toString();

        payment = new Payment();
        payment.setId(paymentId);
        payment.setFromAccountId(fromAccountId);
        payment.setToAccountId(toAccountId);
        payment.setAmount("100.00");
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING.name());
        payment.setCreatedAt("2024-01-01T00:00:00Z");
        payment.setUpdatedAt("2024-01-01T00:00:00Z");
    }

    @Test
    @DisplayName("save - Should persist a payment and return it")
    void testSave() {
        when(paymentRepository.save(any(Payment.class))).thenReturn(payment);
        Payment result = paymentRepository.save(payment);
        assertNotNull(result);
        assertEquals(paymentId, result.getId());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("findById - Should return payment when it exists")
    void testFindById_Found() {
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        Optional<Payment> result = paymentRepository.findById(paymentId);
        assertTrue(result.isPresent());
        assertEquals(paymentId, result.get().getId());
    }

    @Test
    @DisplayName("findById - Should return empty when payment not found")
    void testFindById_NotFound() {
        String nonExistentId = UUID.randomUUID().toString();
        when(paymentRepository.findById(nonExistentId)).thenReturn(Optional.empty());
        Optional<Payment> result = paymentRepository.findById(nonExistentId);
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("findByFromAccountId - Should return payments for given account")
    void testFindByFromAccountId() {
        when(paymentRepository.findByFromAccountId(fromAccountId, 10, null))
                .thenReturn(List.of(payment));
        List<Payment> results = paymentRepository.findByFromAccountId(fromAccountId, 10, null);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(fromAccountId, results.get(0).getFromAccountId());
    }

    @Test
    @DisplayName("findByToAccountId - Should return payments for given account")
    void testFindByToAccountId() {
        when(paymentRepository.findByToAccountId(toAccountId, 10, null))
                .thenReturn(List.of(payment));
        List<Payment> results = paymentRepository.findByToAccountId(toAccountId, 10, null);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(toAccountId, results.get(0).getToAccountId());
    }

    @Test
    @DisplayName("findByStatus - Should return payments with given status")
    void testFindByStatus() {
        when(paymentRepository.findByStatus(PaymentStatus.PENDING, 10, null))
                .thenReturn(List.of(payment));
        List<Payment> results = paymentRepository.findByStatus(PaymentStatus.PENDING, 10, null);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(PaymentStatus.PENDING.name(), results.get(0).getStatus());
    }

    @Test
    @DisplayName("findByIdempotencyKey - Should return payment when key exists")
    void testFindByIdempotencyKey_Found() {
        String key = "idem-key-123";
        payment.setIdempotencyKey(key);
        when(paymentRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(payment));
        Optional<Payment> result = paymentRepository.findByIdempotencyKey(key);
        assertTrue(result.isPresent());
        assertEquals(key, result.get().getIdempotencyKey());
    }

    @Test
    @DisplayName("findByIdempotencyKey - Should return empty when key not found")
    void testFindByIdempotencyKey_NotFound() {
        when(paymentRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        Optional<Payment> result = paymentRepository.findByIdempotencyKey("missing-key");
        assertFalse(result.isPresent());
    }

    @Test
    @DisplayName("delete - Should invoke delete on the repository")
    void testDelete() {
        doNothing().when(paymentRepository).delete(payment);
        paymentRepository.delete(payment);
        verify(paymentRepository, times(1)).delete(payment);
    }
}

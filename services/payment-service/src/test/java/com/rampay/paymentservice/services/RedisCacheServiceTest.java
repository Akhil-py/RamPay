package com.rampay.paymentservice.services;

import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RedisCacheService.
 * Tests Redis caching operations for payments and idempotency keys.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RedisCacheService Tests")
class RedisCacheServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private RedisCacheService redisCacheService;

    private String paymentId;
    private Payment payment;
    private String idempotencyKey;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID().toString();
        idempotencyKey = "test-idempotency-key-123";

        payment = new Payment();
        payment.setId(paymentId);
        payment.setFromAccountId(UUID.randomUUID().toString());
        payment.setToAccountId(UUID.randomUUID().toString());
        payment.setAmount("100.00");
        payment.setCurrency("USD");
        payment.setStatus(PaymentStatus.PENDING.name());
        payment.setCreatedAt(Instant.now().toString());
        payment.setUpdatedAt(Instant.now().toString());
    }

    @Test
    @DisplayName("getPayment - Should retrieve payment from cache")
    void testGetPayment_Success() {
        String expectedKey = "payment:" + paymentId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(payment);

        Payment result = redisCacheService.getPayment(paymentId);

        assertNotNull(result);
        assertEquals(paymentId, result.getId());
        assertEquals(PaymentStatus.PENDING.name(), result.getStatus());

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get(expectedKey);
    }

    @Test
    @DisplayName("getPayment - Should return null when payment not in cache")
    void testGetPayment_NotInCache() {
        String expectedKey = "payment:" + paymentId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(expectedKey)).thenReturn(null);

        Payment result = redisCacheService.getPayment(paymentId);

        assertNull(result);

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get(expectedKey);
    }

    @Test
    @DisplayName("cachePayment - Should cache payment with TTL")
    void testCachePayment_Success() {
        String expectedKey = "payment:" + paymentId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisCacheService.cachePayment(payment);

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).set(expectedKey, payment, 3600L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("invalidatePayment - Should delete payment from cache")
    void testInvalidatePayment_Success() {
        String expectedKey = "payment:" + paymentId;
        when(redisTemplate.delete(expectedKey)).thenReturn(true);

        redisCacheService.invalidatePayment(paymentId);

        verify(redisTemplate, times(1)).delete(expectedKey);
    }

    @Test
    @DisplayName("checkIdempotency - Should return payment ID string when idempotency key exists")
    void testCheckIdempotency_KeyExists() {
        String cacheKey = "idempotency:" + idempotencyKey;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(paymentId);

        String result = redisCacheService.checkIdempotency(idempotencyKey);

        assertNotNull(result);
        assertEquals(paymentId, result);

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get(cacheKey);
    }

    @Test
    @DisplayName("checkIdempotency - Should return null when idempotency key does not exist")
    void testCheckIdempotency_KeyNotExists() {
        String cacheKey = "idempotency:" + idempotencyKey;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(cacheKey)).thenReturn(null);

        String result = redisCacheService.checkIdempotency(idempotencyKey);

        assertNull(result);

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).get(cacheKey);
    }

    @Test
    @DisplayName("recordIdempotency - Should record idempotency key with payment ID string")
    void testRecordIdempotency_Success() {
        String cacheKey = "idempotency:" + idempotencyKey;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisCacheService.recordIdempotency(idempotencyKey, paymentId);

        verify(redisTemplate, times(1)).opsForValue();
        verify(valueOperations, times(1)).set(cacheKey, paymentId, 86400L, TimeUnit.SECONDS);
    }

    @Test
    @DisplayName("recordIdempotency - Should use correct TTL of 86400 seconds")
    void testRecordIdempotency_CorrectTTL() {
        String cacheKey = "idempotency:" + idempotencyKey;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisCacheService.recordIdempotency(idempotencyKey, paymentId);

        verify(valueOperations, times(1))
                .set(eq(cacheKey), eq(paymentId), eq(86400L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("cachePayment - Should use correct TTL of 3600 seconds")
    void testCachePayment_CorrectTTL() {
        String expectedKey = "payment:" + paymentId;
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisCacheService.cachePayment(payment);

        verify(valueOperations, times(1))
                .set(eq(expectedKey), eq(payment), eq(3600L), eq(TimeUnit.SECONDS));
    }
}

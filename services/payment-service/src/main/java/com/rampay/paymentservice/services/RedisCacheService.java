package com.rampay.paymentservice.services;

import com.rampay.paymentservice.models.Payment;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService {

    private static final long PAYMENT_TTL = 3600; // 1 hour
    private static final long IDEMPOTENCY_TTL = 86400; // 24 hours

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Payment getPayment(String id) {
        String key = "payment:" + id;
        return (Payment) redisTemplate.opsForValue().get(key);
    }

    public void cachePayment(Payment payment) {
        String key = "payment:" + payment.getId();
        redisTemplate.opsForValue().set(key, payment, PAYMENT_TTL, TimeUnit.SECONDS);
    }

    public void invalidatePayment(String id) {
        String key = "payment:" + id;
        redisTemplate.delete(key);
    }

    /**
     * Returns the previously stored payment ID string for this idempotency key,
     * or null if not found.
     */
    public String checkIdempotency(String key) {
        String cacheKey = "idempotency:" + key;
        Object value = redisTemplate.opsForValue().get(cacheKey);
        return value != null ? value.toString() : null;
    }

    public void recordIdempotency(String key, String paymentId) {
        String cacheKey = "idempotency:" + key;
        redisTemplate.opsForValue().set(cacheKey, paymentId, IDEMPOTENCY_TTL, TimeUnit.SECONDS);
    }
}

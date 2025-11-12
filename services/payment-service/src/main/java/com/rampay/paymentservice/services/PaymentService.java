package com.rampay.paymentservice.services;

import com.rampay.paymentservice.models.*;
import com.rampay.paymentservice.repositories.PaymentRepository;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    public PaymentService(PaymentRepository paymentRepository, RedisTemplate<String, Object> redisTemplate) {
        this.paymentRepository = paymentRepository;
        this.redisTemplate = redisTemplate;
    }

    public Payment createPayment(Payment payment) {
        payment.setStatus(PaymentStatus.PENDING);
        Payment saved = paymentRepository.save(payment);
        redisTemplate.opsForValue().set("payment:" + saved.getId(), saved);
        return saved;
    }

    public List<Payment> getAllPayments() {
        return paymentRepository.findAll();
    }

    public Payment getPayment(UUID id) {
        // Try Redis first
        Payment cached = (Payment) redisTemplate.opsForValue().get("payment:" + id);
        if (cached != null) return cached;

        // Fallback to DB
        return paymentRepository.findById(id).orElse(null);
    }
}

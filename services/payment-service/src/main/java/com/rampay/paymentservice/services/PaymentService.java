package com.rampay.paymentservice.services;

import com.rampay.paymentservice.dto.CreatePaymentRequest;
import com.rampay.paymentservice.dto.PaymentMapper;
import com.rampay.paymentservice.dto.PaymentResponse;
import com.rampay.paymentservice.exceptions.*;
import com.rampay.paymentservice.models.*;
import com.rampay.paymentservice.repositories.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RedisCacheService redisCacheService;
    private final EventPublisherService eventPublisherService;

    public PaymentService(PaymentRepository paymentRepository,
                           RedisCacheService redisCacheService,
                           EventPublisherService eventPublisherService) {
        this.paymentRepository = paymentRepository;
        this.redisCacheService = redisCacheService;
        this.eventPublisherService = eventPublisherService;
    }

    public Payment createPayment(CreatePaymentRequest request, String idempotencyKey) {
        // Check idempotency if key provided
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            UUID existingPaymentId = redisCacheService.checkIdempotency(idempotencyKey);
            if (existingPaymentId != null) {
                Payment existing = paymentRepository.findById(existingPaymentId)
                        .orElseThrow(() -> new PaymentNotFoundException("Payment not found with id: " + existingPaymentId));
                throw new DuplicatePaymentException("Payment already processed with this idempotency key");
            }
        }

        Payment payment = PaymentMapper.toEntity(request);
        payment.setIdempotencyKey(idempotencyKey);
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        // Record idempotency key
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisCacheService.recordIdempotency(idempotencyKey, saved.getId());
        }

        // Publish event
        eventPublisherService.publishPaymentCreated(
                saved.getId(),
                saved.getFromAccountId(),
                saved.getToAccountId(),
                saved.getAmount(),
                saved.getCurrency()
        );

        return saved;
    }

    public Page<Payment> getAllPayments(int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return paymentRepository.findAll(pageable);
    }

    public Payment getPayment(UUID id) {
        // Try Redis first
        Payment cached = redisCacheService.getPayment(id);
        if (cached != null) return cached;

        // Fallback to DB
        Payment payment = paymentRepository.findById(id).orElse(null);
        if (payment == null) {
            throw new PaymentNotFoundException("Payment not found with id: " + id);
        }
        redisCacheService.cachePayment(payment);
        return payment;
    }

    public Payment approvePayment(UUID id) {
        Payment payment = getPaymentOrThrow(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusException("Cannot approve payment with status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.APPROVED);
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        // Publish event
        eventPublisherService.publishPaymentApproved(saved.getId());

        return saved;
    }

    public Payment failPayment(UUID id, String reason) {
        Payment payment = getPaymentOrThrow(id);
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new InvalidPaymentStatusException("Cannot fail payment with status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        // Publish event
        eventPublisherService.publishPaymentFailed(saved.getId(), reason);

        return saved;
    }

    public Payment refundPayment(UUID id, BigDecimal refundAmount) {
        Payment payment = getPaymentOrThrow(id);
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw new InvalidPaymentStatusException("Cannot refund payment with status: " + payment.getStatus());
        }
        if (refundAmount.compareTo(payment.getAmount()) > 0) {
            throw new InvalidPaymentAmountException("Refund amount cannot exceed original amount");
        }
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundAmount(refundAmount);
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        // Publish event
        eventPublisherService.publishPaymentRefunded(saved.getId(), refundAmount);

        return saved;
    }

    public Page<Payment> getPaymentsByAccount(UUID accountId, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return paymentRepository.findByFromAccountId(accountId, pageable);
    }

    public Page<Payment> getPaymentsByStatus(PaymentStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        return paymentRepository.findByStatus(status, pageable);
    }

    private Payment getPaymentOrThrow(UUID id) {
        Payment payment = getPayment(id);
        if (payment == null) {
            throw new PaymentNotFoundException("Payment not found with id: " + id);
        }
        return payment;
    }
}

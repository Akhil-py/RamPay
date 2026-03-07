package com.rampay.paymentservice.services;

import com.rampay.paymentservice.dto.CreatePaymentRequest;
import com.rampay.paymentservice.dto.PaymentMapper;
import com.rampay.paymentservice.exceptions.*;
import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
import com.rampay.paymentservice.repositories.PaymentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
            String existingPaymentId = redisCacheService.checkIdempotency(idempotencyKey);
            if (existingPaymentId != null) {
                paymentRepository.findById(existingPaymentId)
                        .orElseThrow(() -> new PaymentNotFoundException(
                                "Payment not found with id: " + existingPaymentId));
                throw new DuplicatePaymentException("Payment already processed with this idempotency key");
            }
        }

        Payment payment = PaymentMapper.toEntity(request);
        payment.setId(UUID.randomUUID().toString());
        payment.setIdempotencyKey(idempotencyKey);
        String now = Instant.now().toString();
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

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
                new BigDecimal(saved.getAmount()),
                saved.getCurrency()
        );

        return saved;
    }

    public Page<Payment> getAllPayments(int page, int size) {
        int effectiveSize = Math.min(size, 100);
        // DynamoDB does not support offset-based pagination natively;
        // page 0 returns first effectiveSize items via scan.
        List<Payment> results = paymentRepository.scan(effectiveSize);
        return new PageImpl<>(results, PageRequest.of(page, effectiveSize), results.size());
    }

    public Payment getPayment(UUID id) {
        String idStr = id.toString();
        // Try Redis first
        Payment cached = redisCacheService.getPayment(idStr);
        if (cached != null) return cached;

        // Fallback to DB
        Payment payment = paymentRepository.findById(idStr).orElse(null);
        if (payment == null) {
            throw new PaymentNotFoundException("Payment not found with id: " + id);
        }
        redisCacheService.cachePayment(payment);
        return payment;
    }

    public Payment approvePayment(UUID id) {
        Payment payment = getPaymentOrThrow(id);
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new InvalidPaymentStatusException(
                    "Cannot approve payment with status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.APPROVED.name());
        payment.setUpdatedAt(Instant.now().toString());
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        eventPublisherService.publishPaymentApproved(saved.getId());

        return saved;
    }

    public Payment failPayment(UUID id, String reason) {
        Payment payment = getPaymentOrThrow(id);
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new InvalidPaymentStatusException(
                    "Cannot fail payment with status: " + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.FAILED.name());
        payment.setFailureReason(reason);
        payment.setUpdatedAt(Instant.now().toString());
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        eventPublisherService.publishPaymentFailed(saved.getId(), reason);

        return saved;
    }

    public Payment refundPayment(UUID id, BigDecimal refundAmount) {
        Payment payment = getPaymentOrThrow(id);
        if (!PaymentStatus.APPROVED.name().equals(payment.getStatus())) {
            throw new InvalidPaymentStatusException(
                    "Cannot refund payment with status: " + payment.getStatus());
        }
        BigDecimal originalAmount = new BigDecimal(payment.getAmount());
        if (refundAmount.compareTo(originalAmount) > 0) {
            throw new InvalidPaymentAmountException("Refund amount cannot exceed original amount");
        }
        payment.setStatus(PaymentStatus.REFUNDED.name());
        payment.setRefundAmount(refundAmount.toPlainString());
        payment.setUpdatedAt(Instant.now().toString());
        Payment saved = paymentRepository.save(payment);
        redisCacheService.cachePayment(saved);

        eventPublisherService.publishPaymentRefunded(saved.getId(), refundAmount);

        return saved;
    }

    public Page<Payment> getPaymentsByAccount(UUID accountId, int page, int size) {
        int effectiveSize = Math.min(size, 100);
        List<Payment> results = paymentRepository.findByFromAccountId(
                accountId.toString(), effectiveSize, null);
        return new PageImpl<>(results, PageRequest.of(page, effectiveSize), results.size());
    }

    public Page<Payment> getPaymentsByStatus(PaymentStatus status, int page, int size) {
        int effectiveSize = Math.min(size, 100);
        List<Payment> results = paymentRepository.findByStatus(status, effectiveSize, null);
        return new PageImpl<>(results, PageRequest.of(page, effectiveSize), results.size());
    }

    private Payment getPaymentOrThrow(UUID id) {
        Payment payment = paymentRepository.findById(id.toString()).orElse(null);
        if (payment == null) {
            throw new PaymentNotFoundException("Payment not found with id: " + id);
        }
        return payment;
    }
}

package com.rampay.paymentservice.controllers;

import com.rampay.paymentservice.dto.*;
import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
import com.rampay.paymentservice.services.PaymentService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;
    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Payment payment = paymentService.createPayment(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentMapper.toResponse(payment));
    }

    @GetMapping
    public ResponseEntity<?> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Payment> payments = paymentService.getAllPayments(page, size);
        return ResponseEntity.ok(createPaginatedResponse(payments));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getOne(@PathVariable UUID id) {
        Payment payment = paymentService.getPayment(id);
        if (payment == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PaymentMapper.toResponse(payment));
    }

    @PutMapping("/{id}/approve")
    public ResponseEntity<PaymentResponse> approve(@PathVariable UUID id) {
        Payment payment = paymentService.approvePayment(id);
        return ResponseEntity.ok(PaymentMapper.toResponse(payment));
    }

    @PutMapping("/{id}/fail")
    public ResponseEntity<PaymentResponse> fail(
            @PathVariable UUID id,
            @Valid @RequestBody FailPaymentRequest request) {
        Payment payment = paymentService.failPayment(id, request.getReason());
        return ResponseEntity.ok(PaymentMapper.toResponse(payment));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<PaymentResponse> refund(
            @PathVariable UUID id,
            @Valid @RequestBody RefundPaymentRequest request) {
        Payment payment = paymentService.refundPayment(id, request.getRefundAmount());
        return ResponseEntity.ok(PaymentMapper.toResponse(payment));
    }

    @GetMapping("/account/{accountId}")
    public ResponseEntity<?> getPaymentsByAccount(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Payment> payments = paymentService.getPaymentsByAccount(accountId, page, size);
        return ResponseEntity.ok(createPaginatedResponse(payments));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<?> getPaymentsByStatus(
            @PathVariable PaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<Payment> payments = paymentService.getPaymentsByStatus(status, page, size);
        return ResponseEntity.ok(createPaginatedResponse(payments));
    }

    private Object createPaginatedResponse(Page<Payment> payments) {
        List<PaymentResponse> content = payments.getContent().stream()
                .map(PaymentMapper::toResponse)
                .collect(Collectors.toList());
        return new Object() {
            public final List<PaymentResponse> content = content;
            public final Pageable pageable = new Pageable(
                    payments.getNumber(),
                    payments.getSize(),
                    payments.getTotalPages(),
                    payments.getTotalElements(),
                    payments.isFirst(),
                    payments.isLast()
            );
        };
    }

    private record Pageable(int pageNumber, int pageSize, int totalPages, long totalElements, boolean first, boolean last) {}
}

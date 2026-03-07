package com.rampay.paymentservice.dto;

import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class PaymentMapper {

    public static PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId() != null ? UUID.fromString(payment.getId()) : null)
                .fromAccountId(payment.getFromAccountId() != null
                        ? UUID.fromString(payment.getFromAccountId()) : null)
                .toAccountId(payment.getToAccountId() != null
                        ? UUID.fromString(payment.getToAccountId()) : null)
                .amount(payment.getAmount() != null
                        ? new BigDecimal(payment.getAmount()) : null)
                .currency(payment.getCurrency())
                .status(payment.getStatus() != null
                        ? PaymentStatus.valueOf(payment.getStatus()) : null)
                .createdAt(payment.getCreatedAt() != null
                        ? Instant.parse(payment.getCreatedAt()) : null)
                .updatedAt(payment.getUpdatedAt() != null
                        ? Instant.parse(payment.getUpdatedAt()) : null)
                .failureReason(payment.getFailureReason())
                .refundAmount(payment.getRefundAmount() != null
                        ? new BigDecimal(payment.getRefundAmount()) : null)
                .build();
    }

    public static Payment toEntity(CreatePaymentRequest request) {
        Payment payment = new Payment();
        payment.setFromAccountId(request.getFromAccountId() != null
                ? request.getFromAccountId().toString() : null);
        payment.setToAccountId(request.getToAccountId() != null
                ? request.getToAccountId().toString() : null);
        payment.setAmount(request.getAmount() != null
                ? request.getAmount().toPlainString() : null);
        payment.setCurrency(request.getCurrency());
        payment.setStatus(PaymentStatus.PENDING.name());
        return payment;
    }
}

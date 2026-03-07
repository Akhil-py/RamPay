package com.rampay.paymentservice.dto;

import com.rampay.paymentservice.models.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private UUID id;
    private UUID fromAccountId;
    private UUID toAccountId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private String failureReason;
    private BigDecimal refundAmount;
}

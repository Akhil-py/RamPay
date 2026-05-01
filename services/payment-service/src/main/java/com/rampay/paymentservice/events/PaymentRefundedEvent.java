package com.rampay.paymentservice.events;

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
public class PaymentRefundedEvent {
    private String eventType = "PaymentRefunded";
    private UUID paymentId;
    private BigDecimal refundAmount;
    private Instant timestamp = Instant.now();
}

package com.rampay.paymentservice.events;

import com.rampay.paymentservice.models.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentApprovedEvent {
    private String eventType = "PaymentApproved";
    private UUID paymentId;
    private PaymentStatus status = PaymentStatus.APPROVED;
    private Instant timestamp = Instant.now();
}

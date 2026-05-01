package com.rampay.paymentservice.events;

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
public class AnomalyDetectedEvent {
    private String eventType = "AnomalyDetected";
    private UUID paymentId;
    private double riskScore;
    private String message;
    private Instant timestamp = Instant.now();
}

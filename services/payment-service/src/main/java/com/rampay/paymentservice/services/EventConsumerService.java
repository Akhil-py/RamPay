package com.rampay.paymentservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rampay.paymentservice.events.AnomalyDetectedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class EventConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(EventConsumerService.class);
    private final ObjectMapper objectMapper;
    private final PaymentService paymentService;

    public EventConsumerService(ObjectMapper objectMapper, PaymentService paymentService) {
        this.objectMapper = objectMapper;
        this.paymentService = paymentService;
    }

    @KafkaListener(topics = "anomaly-detected", groupId = "payment-service-group")
    public void consumeAnomalyDetected(String message) {
        AnomalyDetectedEvent event;
        try {
            event = objectMapper.readValue(message, AnomalyDetectedEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize AnomalyDetected event: " + message, e);
        }
        logger.info("Received AnomalyDetected event for paymentId: {}, riskScore: {}",
                event.getPaymentId(), event.getRiskScore());
        paymentService.failPayment(event.getPaymentId(), "Flagged by fraud service");
    }
}

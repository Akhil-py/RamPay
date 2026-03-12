package com.rampay.paymentservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rampay.paymentservice.events.*;
import com.rampay.paymentservice.models.OutboxEvent;
import com.rampay.paymentservice.models.OutboxStatus;
import com.rampay.paymentservice.repositories.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class EventPublisherService {

    private static final Logger logger = LoggerFactory.getLogger(EventPublisherService.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public EventPublisherService(OutboxRepository outboxRepository,
                               KafkaTemplate<String, String> kafkaTemplate,
                               ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishPaymentCreated(String paymentId, String fromAccountId, String toAccountId,
                                      BigDecimal amount, String currency) {
        PaymentCreatedEvent event = PaymentCreatedEvent.builder()
                .paymentId(UUID.fromString(paymentId))
                .fromAccountId(UUID.fromString(fromAccountId))
                .toAccountId(UUID.fromString(toAccountId))
                .amount(amount)
                .currency(currency)
                .build();

        saveOutboxEvent(paymentId, "Payment", "PaymentCreated", event);
    }

    public void publishPaymentApproved(String paymentId) {
        PaymentApprovedEvent event = PaymentApprovedEvent.builder()
                .paymentId(UUID.fromString(paymentId))
                .build();

        saveOutboxEvent(paymentId, "Payment", "PaymentApproved", event);
    }

    public void publishPaymentFailed(String paymentId, String reason) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .paymentId(UUID.fromString(paymentId))
                .reason(reason)
                .build();

        saveOutboxEvent(paymentId, "Payment", "PaymentFailed", event);
    }

    public void publishPaymentRefunded(String paymentId, BigDecimal refundAmount) {
        PaymentRefundedEvent event = PaymentRefundedEvent.builder()
                .paymentId(UUID.fromString(paymentId))
                .refundAmount(refundAmount)
                .build();

        saveOutboxEvent(paymentId, "Payment", "PaymentRefunded", event);
    }

    private void saveOutboxEvent(String aggregateId, String aggregateType,
                               String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setId(UUID.randomUUID().toString());
            outboxEvent.setAggregateId(aggregateId);
            outboxEvent.setAggregateType(aggregateType);
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(payload);
            outboxEvent.setStatus(OutboxStatus.PENDING.name());
            outboxEvent.setCreatedAt(Instant.now().toString());
            outboxEvent.setRetryCount(0);
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void processOutboxEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findPendingEvents(100);

        for (OutboxEvent event : pendingEvents) {
            try {
                String topic = getTopicForEventType(event.getEventType());
                String aggregateId = event.getAggregateId();

                kafkaTemplate.executeInTransaction(ops -> {
                    ops.send(topic, aggregateId, event.getPayload());
                    return null;
                });

                event.setStatus(OutboxStatus.PUBLISHED.name());
                event.setProcessedAt(Instant.now().toString());
                outboxRepository.save(event);
            } catch (Exception e) {
                int retries = event.getRetryCount() != null ? event.getRetryCount() : 0;
                event.setRetryCount(retries + 1);
                event.setErrorMessage(e.getMessage());

                if (retries + 1 >= 3) {
                    event.setStatus(OutboxStatus.FAILED.name());
                }
                outboxRepository.save(event);
                logger.warn("Failed to publish outbox event id={}, retryCount={}",
                        event.getId(), event.getRetryCount(), e);
            }
        }
    }

    private String getTopicForEventType(String eventType) {
        return switch (eventType) {
            case "PaymentCreated" -> "payment-created";
            case "PaymentApproved" -> "payment-approved";
            case "PaymentFailed" -> "payment-failed";
            case "PaymentRefunded" -> "payment-refunded";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}

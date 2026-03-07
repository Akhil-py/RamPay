package com.rampay.paymentservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rampay.paymentservice.models.OutboxEvent;
import com.rampay.paymentservice.models.OutboxStatus;
import com.rampay.paymentservice.repositories.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventPublisherService.
 * Tests event publishing and outbox processing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisherService Tests")
class EventPublisherServiceTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private EventPublisherService eventPublisherService;

    private String paymentId;
    private String fromAccountId;
    private String toAccountId;
    private OutboxEvent outboxEvent;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID().toString();
        fromAccountId = UUID.randomUUID().toString();
        toAccountId = UUID.randomUUID().toString();

        outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID().toString());
        outboxEvent.setAggregateId(paymentId);
        outboxEvent.setAggregateType("Payment");
        outboxEvent.setEventType("PaymentCreated");
        outboxEvent.setPayload("{\"paymentId\":\"" + paymentId + "\"}");
        outboxEvent.setStatus(OutboxStatus.PENDING.name());
        outboxEvent.setCreatedAt(Instant.now().toString());
        outboxEvent.setRetryCount(0);
    }

    @Test
    @DisplayName("publishPaymentCreated - Should save outbox event successfully")
    void testPublishPaymentCreated_Success() throws Exception {
        String expectedPayload = "{\"paymentId\":\"" + paymentId + "\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedPayload);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.publishPaymentCreated(
                paymentId, fromAccountId, toAccountId, new BigDecimal("100.00"), "USD");

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(paymentId, savedEvent.getAggregateId());
        assertEquals("Payment", savedEvent.getAggregateType());
        assertEquals("PaymentCreated", savedEvent.getEventType());
        assertEquals(expectedPayload, savedEvent.getPayload());
        assertEquals(OutboxStatus.PENDING.name(), savedEvent.getStatus());
    }

    @Test
    @DisplayName("publishPaymentApproved - Should save outbox event successfully")
    void testPublishPaymentApproved_Success() throws Exception {
        String expectedPayload = "{\"paymentId\":\"" + paymentId + "\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedPayload);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.publishPaymentApproved(paymentId);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(paymentId, savedEvent.getAggregateId());
        assertEquals("PaymentApproved", savedEvent.getEventType());
        assertEquals(OutboxStatus.PENDING.name(), savedEvent.getStatus());
    }

    @Test
    @DisplayName("publishPaymentFailed - Should save outbox event successfully")
    void testPublishPaymentFailed_Success() throws Exception {
        String reason = "Insufficient funds";
        String expectedPayload = "{\"paymentId\":\"" + paymentId + "\",\"reason\":\"" + reason + "\"}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedPayload);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.publishPaymentFailed(paymentId, reason);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(paymentId, savedEvent.getAggregateId());
        assertEquals("PaymentFailed", savedEvent.getEventType());
        assertEquals(OutboxStatus.PENDING.name(), savedEvent.getStatus());
    }

    @Test
    @DisplayName("publishPaymentRefunded - Should save outbox event successfully")
    void testPublishPaymentRefunded_Success() throws Exception {
        BigDecimal refundAmount = new BigDecimal("50.00");
        String expectedPayload = "{\"paymentId\":\"" + paymentId + "\",\"refundAmount\":50.00}";
        when(objectMapper.writeValueAsString(any())).thenReturn(expectedPayload);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.publishPaymentRefunded(paymentId, refundAmount);

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(paymentId, savedEvent.getAggregateId());
        assertEquals("PaymentRefunded", savedEvent.getEventType());
        assertEquals(OutboxStatus.PENDING.name(), savedEvent.getStatus());
    }

    @Test
    @DisplayName("publishPaymentCreated - Should throw RuntimeException when JSON serialization fails")
    void testPublishPaymentCreated_JsonSerializationFailure() throws Exception {
        when(objectMapper.writeValueAsString(any()))
                .thenThrow(new RuntimeException("JSON serialization failed"));

        assertThrows(RuntimeException.class, () ->
                eventPublisherService.publishPaymentCreated(
                        paymentId, fromAccountId, toAccountId, new BigDecimal("100.00"), "USD"));

        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("processOutboxEvents - Should process and publish pending events")
    @SuppressWarnings("unchecked")
    void testProcessOutboxEvents_Success() {
        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.executeInTransaction(any(Function.class))).thenReturn(null);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.processOutboxEvents();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent updatedEvent = eventCaptor.getValue();
        assertEquals(OutboxStatus.PUBLISHED.name(), updatedEvent.getStatus());
        assertNotNull(updatedEvent.getProcessedAt());
    }

    @Test
    @DisplayName("processOutboxEvents - Should handle Kafka send failure and increment retry count")
    @SuppressWarnings("unchecked")
    void testProcessOutboxEvents_KafkaSendFailure() {
        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.executeInTransaction(any(Function.class)))
                .thenThrow(new RuntimeException("Kafka send failed"));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.processOutboxEvents();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent updatedEvent = eventCaptor.getValue();
        assertEquals(1, updatedEvent.getRetryCount());
        assertEquals("Kafka send failed", updatedEvent.getErrorMessage());
        assertEquals(OutboxStatus.PENDING.name(), updatedEvent.getStatus());
    }

    @Test
    @DisplayName("processOutboxEvents - Should mark event as FAILED after max retries")
    @SuppressWarnings("unchecked")
    void testProcessOutboxEvents_MaxRetriesExceeded() {
        outboxEvent.setRetryCount(3);

        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of(outboxEvent));
        when(kafkaTemplate.executeInTransaction(any(Function.class)))
                .thenThrow(new RuntimeException("Kafka send failed"));
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);

        eventPublisherService.processOutboxEvents();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());

        OutboxEvent updatedEvent = eventCaptor.getValue();
        assertEquals(4, updatedEvent.getRetryCount());
        assertEquals(OutboxStatus.FAILED.name(), updatedEvent.getStatus());
    }

    @Test
    @DisplayName("processOutboxEvents - Should handle empty pending events list")
    void testProcessOutboxEvents_NoPendingEvents() {
        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of());

        eventPublisherService.processOutboxEvents();

        verify(kafkaTemplate, never()).executeInTransaction(any());
        verify(outboxRepository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("processOutboxEvents - Should throw IllegalArgumentException for unknown event type")
    @SuppressWarnings("unchecked")
    void testProcessOutboxEvents_UnknownEventType() {
        outboxEvent.setEventType("UnknownEventType");
        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of(outboxEvent));

        // IllegalArgumentException is caught by the catch block and increments retryCount
        eventPublisherService.processOutboxEvents();

        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository, times(1)).save(eventCaptor.capture());
        assertEquals(1, eventCaptor.getValue().getRetryCount());
    }
}

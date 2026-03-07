package com.rampay.paymentservice.repositories;

import com.rampay.paymentservice.models.OutboxEvent;
import com.rampay.paymentservice.models.OutboxStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OutboxRepository (mocked — DynamoDB integration tests require a running instance).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxRepository Tests")
class OutboxRepositoryTest {

    @Mock
    private OutboxRepository outboxRepository;

    private OutboxEvent outboxEvent;

    @BeforeEach
    void setUp() {
        outboxEvent = new OutboxEvent();
        outboxEvent.setId(UUID.randomUUID().toString());
        outboxEvent.setAggregateId(UUID.randomUUID().toString());
        outboxEvent.setAggregateType("Payment");
        outboxEvent.setEventType("PaymentCreated");
        outboxEvent.setPayload("{\"paymentId\":\"" + UUID.randomUUID() + "\"}");
        outboxEvent.setStatus(OutboxStatus.PENDING.name());
        outboxEvent.setCreatedAt(Instant.now().toString());
        outboxEvent.setRetryCount(0);
    }

    @Test
    @DisplayName("save - Should persist an outbox event and return it")
    void testSave() {
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(outboxEvent);
        OutboxEvent result = outboxRepository.save(outboxEvent);
        assertNotNull(result);
        assertEquals(outboxEvent.getId(), result.getId());
        verify(outboxRepository, times(1)).save(outboxEvent);
    }

    @Test
    @DisplayName("findPendingEvents - Should return PENDING events up to limit")
    void testFindPendingEvents() {
        when(outboxRepository.findPendingEvents(100)).thenReturn(List.of(outboxEvent));
        List<OutboxEvent> results = outboxRepository.findPendingEvents(100);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals(OutboxStatus.PENDING.name(), results.get(0).getStatus());
    }

    @Test
    @DisplayName("findPendingEvents - Should return empty list when no pending events")
    void testFindPendingEvents_Empty() {
        when(outboxRepository.findPendingEvents(anyInt())).thenReturn(List.of());
        List<OutboxEvent> results = outboxRepository.findPendingEvents(100);
        assertNotNull(results);
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("updateStatus - Should update event status to PUBLISHED")
    void testUpdateStatus_Published() {
        doNothing().when(outboxRepository)
                .updateStatus(any(OutboxEvent.class), eq(OutboxStatus.PUBLISHED), isNull());
        outboxRepository.updateStatus(outboxEvent, OutboxStatus.PUBLISHED, null);
        verify(outboxRepository, times(1))
                .updateStatus(outboxEvent, OutboxStatus.PUBLISHED, null);
    }

    @Test
    @DisplayName("updateStatus - Should update event status to FAILED with error message")
    void testUpdateStatus_Failed() {
        String errorMsg = "Kafka send failed";
        doNothing().when(outboxRepository)
                .updateStatus(any(OutboxEvent.class), eq(OutboxStatus.FAILED), eq(errorMsg));
        outboxRepository.updateStatus(outboxEvent, OutboxStatus.FAILED, errorMsg);
        verify(outboxRepository, times(1))
                .updateStatus(outboxEvent, OutboxStatus.FAILED, errorMsg);
    }
}

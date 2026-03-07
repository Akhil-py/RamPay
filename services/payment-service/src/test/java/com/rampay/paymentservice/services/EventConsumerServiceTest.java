package com.rampay.paymentservice.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rampay.paymentservice.events.AnomalyDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EventConsumerService.
 * Tests Kafka event consumption and fraud detection handling.
 *
 * Note: deserialization failures now propagate as RuntimeException so that
 * Spring Kafka's DefaultErrorHandler can retry and route to the DLT.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EventConsumerService Tests")
class EventConsumerServiceTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private EventConsumerService eventConsumerService;

    private UUID paymentId;
    private AnomalyDetectedEvent anomalyDetectedEvent;
    private String eventMessage;

    @BeforeEach
    void setUp() {
        paymentId = UUID.randomUUID();

        anomalyDetectedEvent = AnomalyDetectedEvent.builder()
                .paymentId(paymentId)
                .riskScore(0.95)
                .message("Unusual transaction pattern")
                .build();

        eventMessage = "{\"paymentId\":\"" + paymentId + "\",\"riskScore\":0.95,"
                + "\"message\":\"Unusual transaction pattern\"}";
    }

    @Test
    @DisplayName("consumeAnomalyDetected - Should process AnomalyDetected event and fail payment")
    void testConsumeAnomalyDetected_Success() throws Exception {
        when(objectMapper.readValue(eventMessage, AnomalyDetectedEvent.class))
                .thenReturn(anomalyDetectedEvent);

        eventConsumerService.consumeAnomalyDetected(eventMessage);

        verify(objectMapper, times(1)).readValue(eventMessage, AnomalyDetectedEvent.class);
        verify(paymentService, times(1)).failPayment(paymentId, "Flagged by fraud service");
    }

    @Test
    @DisplayName("consumeAnomalyDetected - Should propagate exception on JSON deserialization error")
    void testConsumeAnomalyDetected_JsonDeserializationError() throws Exception {
        String invalidJson = "{invalid json}";

        when(objectMapper.readValue(anyString(), eq(AnomalyDetectedEvent.class)))
                .thenThrow(new RuntimeException("JSON parsing failed"));

        assertThrows(RuntimeException.class,
                () -> eventConsumerService.consumeAnomalyDetected(invalidJson));

        verify(objectMapper, times(1)).readValue(anyString(), eq(AnomalyDetectedEvent.class));
        verify(paymentService, never()).failPayment(any(), anyString());
    }

    @Test
    @DisplayName("consumeAnomalyDetected - Should propagate exception on null/empty message")
    void testConsumeAnomalyDetected_NullMessage() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(AnomalyDetectedEvent.class)))
                .thenThrow(new RuntimeException("Cannot parse null"));

        assertThrows(RuntimeException.class,
                () -> eventConsumerService.consumeAnomalyDetected(null));

        verify(paymentService, never()).failPayment(any(), anyString());
    }

    @Test
    @DisplayName("consumeAnomalyDetected - Should propagate exception on malformed JSON")
    void testConsumeAnomalyDetected_MalformedJson() throws Exception {
        String malformedJson = "{\"paymentId\":\"" + paymentId + "\",\"riskScore\":not_a_number}";

        when(objectMapper.readValue(malformedJson, AnomalyDetectedEvent.class))
                .thenThrow(new RuntimeException("Malformed JSON"));

        assertThrows(RuntimeException.class,
                () -> eventConsumerService.consumeAnomalyDetected(malformedJson));

        verify(paymentService, never()).failPayment(any(), anyString());
    }

    @Test
    @DisplayName("consumeAnomalyDetected - Should handle high risk score and fail payment")
    void testConsumeAnomalyDetected_HighRiskScore() throws Exception {
        AnomalyDetectedEvent highRiskEvent = AnomalyDetectedEvent.builder()
                .paymentId(paymentId)
                .riskScore(0.99)
                .message("Suspicious activity detected")
                .build();

        String highRiskMessage = "{\"paymentId\":\"" + paymentId + "\",\"riskScore\":0.99}";

        when(objectMapper.readValue(highRiskMessage, AnomalyDetectedEvent.class))
                .thenReturn(highRiskEvent);

        eventConsumerService.consumeAnomalyDetected(highRiskMessage);

        verify(paymentService, times(1)).failPayment(paymentId, "Flagged by fraud service");
    }
}

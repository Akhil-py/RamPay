package com.rampay.paymentservice.controllers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;
import software.amazon.awssdk.services.dynamodb.model.ListTablesResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for HealthController.
 * Tests health check endpoints with various dependency statuses.
 */
@WebMvcTest(HealthController.class)
@DisplayName("HealthController Tests")
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DynamoDbClient dynamoDbClient;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        reset(dynamoDbClient, redisTemplate, kafkaTemplate);
    }

    @Test
    @DisplayName("GET /health - Should return UP status when all dependencies are healthy")
    void testHealthCheck_AllDependenciesUp() throws Exception {
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenReturn(ListTablesResponse.builder().build());
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(kafkaTemplate.getProducerFactory())
                .thenReturn(mock(org.springframework.kafka.core.ProducerFactory.class));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.redis.status").value("UP"))
                .andExpect(jsonPath("$.kafka.status").value("UP"));

        verify(dynamoDbClient, times(1)).listTables(any(ListTablesRequest.class));
    }

    @Test
    @DisplayName("GET /health - Should return DOWN for database when DynamoDB is down")
    void testHealthCheck_DatabaseDown() throws Exception {
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenThrow(new RuntimeException("DynamoDB connection failed"));
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(kafkaTemplate.getProducerFactory())
                .thenReturn(mock(org.springframework.kafka.core.ProducerFactory.class));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("DOWN"))
                .andExpect(jsonPath("$.database.error").exists())
                .andExpect(jsonPath("$.redis.status").value("UP"))
                .andExpect(jsonPath("$.kafka.status").value("UP"));

        verify(dynamoDbClient, times(1)).listTables(any(ListTablesRequest.class));
    }

    @Test
    @DisplayName("GET /health - Should return DOWN for Redis when Redis is down")
    void testHealthCheck_RedisDown() throws Exception {
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenReturn(ListTablesResponse.builder().build());
        when(redisTemplate.opsForValue())
                .thenThrow(new RuntimeException("Redis connection failed"));
        when(kafkaTemplate.getProducerFactory())
                .thenReturn(mock(org.springframework.kafka.core.ProducerFactory.class));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.redis.status").value("DOWN"))
                .andExpect(jsonPath("$.redis.error").exists())
                .andExpect(jsonPath("$.kafka.status").value("UP"));
    }

    @Test
    @DisplayName("GET /health - Should return DOWN for Kafka when Kafka is down")
    void testHealthCheck_KafkaDown() throws Exception {
        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenReturn(ListTablesResponse.builder().build());
        when(redisTemplate.opsForValue()).thenReturn(mock(org.springframework.data.redis.core.ValueOperations.class));
        when(kafkaTemplate.getProducerFactory())
                .thenThrow(new RuntimeException("Kafka connection failed"));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database.status").value("UP"))
                .andExpect(jsonPath("$.redis.status").value("UP"))
                .andExpect(jsonPath("$.kafka.status").value("DOWN"))
                .andExpect(jsonPath("$.kafka.error").exists());
    }

    @Test
    @DisplayName("GET /health - Should return health status with specific error messages")
    void testHealthCheck_SpecificErrorMessages() throws Exception {
        String dbError = "Connection refused";
        String redisError = "Timeout";
        String kafkaError = "Broker not available";

        when(dynamoDbClient.listTables(any(ListTablesRequest.class)))
                .thenThrow(new RuntimeException(dbError));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException(redisError));
        when(kafkaTemplate.getProducerFactory()).thenThrow(new RuntimeException(kafkaError));

        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.database.error").value(dbError))
                .andExpect(jsonPath("$.redis.error").value(redisError))
                .andExpect(jsonPath("$.kafka.error").value(kafkaError));
    }
}

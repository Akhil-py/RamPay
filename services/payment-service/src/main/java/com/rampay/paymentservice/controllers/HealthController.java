package com.rampay.paymentservice.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.ListTablesRequest;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HealthController {

    @Autowired(required = false)
    private DynamoDbClient dynamoDbClient;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping("/health")
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");

        // Check DynamoDB
        try {
            dynamoDbClient.listTables(ListTablesRequest.builder().limit(1).build());
            health.put("database", Map.of("status", "UP"));
        } catch (Exception e) {
            health.put("database", Map.of("status", "DOWN", "error", e.getMessage()));
        }

        // Check Redis
        try {
            redisTemplate.opsForValue().get("health-check");
            health.put("redis", Map.of("status", "UP"));
        } catch (Exception e) {
            health.put("redis", Map.of("status", "DOWN", "error", e.getMessage()));
        }

        // Check Kafka
        try {
            kafkaTemplate.getProducerFactory();
            health.put("kafka", Map.of("status", "UP"));
        } catch (Exception e) {
            health.put("kafka", Map.of("status", "DOWN", "error", e.getMessage()));
        }

        return health;
    }
}

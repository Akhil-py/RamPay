package com.rampay.paymentservice.models;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB-backed outbox event entity.
 *
 * Table: PaymentOutbox
 *   Partition key : id      (S)
 *   Sort key      : createdAt (S, ISO-8601) — enables efficient range scans per aggregate
 *
 * GSI:
 *   status-createdAt-index — PK: status, SK: createdAt
 *     Used by the outbox poller to query PENDING events ordered by creation time.
 *
 * All timestamps are ISO-8601 strings.
 * OutboxStatus is stored as its name() string.
 */
@DynamoDbBean
public class OutboxEvent {

    private String id;
    private String createdAt;
    private String aggregateId;
    private String aggregateType;
    private String eventType;
    private String payload;
    private String status;
    private String processedAt;
    private Integer retryCount;
    private String errorMessage;

    // --- primary key ---

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    @DynamoDbSortKey
    @DynamoDbSecondarySortKey(indexNames = {"status-createdAt-index"})
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // --- GSI partition key ---

    @DynamoDbSecondaryPartitionKey(indexNames = {"status-createdAt-index"})
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // --- plain attributes ---

    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public String getProcessedAt() { return processedAt; }
    public void setProcessedAt(String processedAt) { this.processedAt = processedAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}

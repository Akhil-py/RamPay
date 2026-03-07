package com.rampay.paymentservice.models;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

/**
 * DynamoDB-backed Payment entity.
 *
 * Table: Payments
 *   Partition key : id (S)
 *
 * GSIs:
 *   fromAccountId-index   — PK: fromAccountId
 *   toAccountId-index     — PK: toAccountId
 *   status-index          — PK: status, SK: createdAt
 *   idempotencyKey-index  — PK: idempotencyKey
 *
 * All monetary values are stored as plain-string BigDecimal representations.
 * All timestamps are stored as ISO-8601 strings.
 * PaymentStatus is stored as its name() string.
 */
@DynamoDbBean
public class Payment {

    private String id;
    private String fromAccountId;
    private String toAccountId;
    private String amount;
    private String currency;
    private String status;
    private String createdAt;
    private String updatedAt;
    private String failureReason;
    private String refundAmount;
    private String idempotencyKey;

    // --- partition key ---

    @DynamoDbPartitionKey
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    // --- GSI partition keys ---

    @DynamoDbSecondaryPartitionKey(indexNames = {"fromAccountId-index"})
    public String getFromAccountId() { return fromAccountId; }
    public void setFromAccountId(String fromAccountId) { this.fromAccountId = fromAccountId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"toAccountId-index"})
    public String getToAccountId() { return toAccountId; }
    public void setToAccountId(String toAccountId) { this.toAccountId = toAccountId; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"status-index"})
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    @DynamoDbSecondaryPartitionKey(indexNames = {"idempotencyKey-index"})
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    // --- GSI sort key ---

    @DynamoDbSecondarySortKey(indexNames = {"status-index"})
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    // --- plain attributes ---

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public String getRefundAmount() { return refundAmount; }
    public void setRefundAmount(String refundAmount) { this.refundAmount = refundAmount; }
}

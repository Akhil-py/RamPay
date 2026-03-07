package com.rampay.paymentservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.List;

/**
 * Creates DynamoDB tables (and their GSIs) on application startup if they do not already exist.
 * Safe to run on every restart — ResourceInUseException is swallowed silently.
 */
@Component
public class DynamoDbTableInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DynamoDbTableInitializer.class);

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.tables.payments:Payments}")
    private String paymentsTable;

    @Value("${aws.dynamodb.tables.outbox:PaymentOutbox}")
    private String outboxTable;

    public DynamoDbTableInitializer(DynamoDbClient dynamoDbClient) {
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void run(String... args) {
        createPaymentsTable();
        createOutboxTable();
    }

    private void createPaymentsTable() {
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(paymentsTable)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            attr("id", ScalarAttributeType.S),
                            attr("fromAccountId", ScalarAttributeType.S),
                            attr("toAccountId", ScalarAttributeType.S),
                            attr("status", ScalarAttributeType.S),
                            attr("idempotencyKey", ScalarAttributeType.S),
                            attr("createdAt", ScalarAttributeType.S)
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build()
                    )
                    .globalSecondaryIndexes(
                            gsiHashOnly("fromAccountId-index", "fromAccountId"),
                            gsiHashOnly("toAccountId-index", "toAccountId"),
                            gsiHashOnly("idempotencyKey-index", "idempotencyKey"),
                            gsiHashRange("status-index", "status", "createdAt")
                    )
                    .build());
            logger.info("Created DynamoDB table: {}", paymentsTable);
        } catch (ResourceInUseException e) {
            logger.debug("DynamoDB table already exists: {}", paymentsTable);
        }
    }

    private void createOutboxTable() {
        try {
            dynamoDbClient.createTable(CreateTableRequest.builder()
                    .tableName(outboxTable)
                    .billingMode(BillingMode.PAY_PER_REQUEST)
                    .attributeDefinitions(
                            attr("id", ScalarAttributeType.S),
                            attr("createdAt", ScalarAttributeType.S),
                            attr("status", ScalarAttributeType.S)
                    )
                    .keySchema(
                            KeySchemaElement.builder().attributeName("id").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("createdAt").keyType(KeyType.RANGE).build()
                    )
                    .globalSecondaryIndexes(
                            gsiHashRange("status-createdAt-index", "status", "createdAt")
                    )
                    .build());
            logger.info("Created DynamoDB table: {}", outboxTable);
        } catch (ResourceInUseException e) {
            logger.debug("DynamoDB table already exists: {}", outboxTable);
        }
    }

    private static AttributeDefinition attr(String name, ScalarAttributeType type) {
        return AttributeDefinition.builder()
                .attributeName(name)
                .attributeType(type)
                .build();
    }

    private static GlobalSecondaryIndex gsiHashOnly(String indexName, String hashKey) {
        return GlobalSecondaryIndex.builder()
                .indexName(indexName)
                .keySchema(
                        KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build()
                )
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }

    private static GlobalSecondaryIndex gsiHashRange(String indexName, String hashKey, String rangeKey) {
        return GlobalSecondaryIndex.builder()
                .indexName(indexName)
                .keySchema(
                        KeySchemaElement.builder().attributeName(hashKey).keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName(rangeKey).keyType(KeyType.RANGE).build()
                )
                .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                .build();
    }
}

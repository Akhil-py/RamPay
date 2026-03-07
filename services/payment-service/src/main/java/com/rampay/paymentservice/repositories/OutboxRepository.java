package com.rampay.paymentservice.repositories;

import com.rampay.paymentservice.models.OutboxEvent;
import com.rampay.paymentservice.models.OutboxStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OutboxRepository {

    private final DynamoDbTable<OutboxEvent> table;

    public OutboxRepository(DynamoDbEnhancedClient enhancedClient,
                            @Value("${aws.dynamodb.tables.outbox:PaymentOutbox}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OutboxEvent.class));
    }

    public OutboxEvent save(OutboxEvent event) {
        table.putItem(event);
        return event;
    }

    /**
     * Query the status-createdAt-index GSI for PENDING events, ordered by createdAt ascending.
     */
    public List<OutboxEvent> findPendingEvents(int limit) {
        DynamoDbIndex<OutboxEvent> index = table.index("status-createdAt-index");
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(OutboxStatus.PENDING.name()).build()))
                .limit(limit)
                .scanIndexForward(true) // oldest first
                .build();

        List<OutboxEvent> results = new ArrayList<>();
        for (Page<OutboxEvent> page : index.query(request)) {
            results.addAll(page.items());
            if (results.size() >= limit) break;
        }
        return results;
    }

    public void updateStatus(OutboxEvent event, OutboxStatus newStatus, String errorMessage) {
        event.setStatus(newStatus.name());
        if (errorMessage != null) {
            event.setErrorMessage(errorMessage);
        }
        if (newStatus == OutboxStatus.PUBLISHED || newStatus == OutboxStatus.FAILED) {
            event.setProcessedAt(Instant.now().toString());
        }
        table.putItem(event);
    }
}

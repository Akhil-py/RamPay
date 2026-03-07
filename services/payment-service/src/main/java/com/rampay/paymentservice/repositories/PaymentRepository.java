package com.rampay.paymentservice.repositories;

import com.rampay.paymentservice.models.Payment;
import com.rampay.paymentservice.models.PaymentStatus;
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
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class PaymentRepository {

    private final DynamoDbTable<Payment> table;

    public PaymentRepository(DynamoDbEnhancedClient enhancedClient,
                             @Value("${aws.dynamodb.tables.payments:Payments}") String tableName) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Payment.class));
    }

    public Payment save(Payment payment) {
        table.putItem(payment);
        return payment;
    }

    public Optional<Payment> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<Payment> findByFromAccountId(String accountId, int limit, String lastEvaluatedKey) {
        return queryGsi("fromAccountId-index", accountId, limit, lastEvaluatedKey);
    }

    public List<Payment> findByToAccountId(String accountId, int limit, String lastEvaluatedKey) {
        return queryGsi("toAccountId-index", accountId, limit, lastEvaluatedKey);
    }

    public List<Payment> findByStatus(PaymentStatus status, int limit, String lastEvaluatedKey) {
        return queryGsi("status-index", status.name(), limit, lastEvaluatedKey);
    }

    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        DynamoDbIndex<Payment> index = table.index("idempotencyKey-index");
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(idempotencyKey).build()))
                .limit(1)
                .build();
        List<Payment> results = new ArrayList<>();
        index.query(request).forEach(page -> results.addAll(page.items()));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public void delete(Payment payment) {
        table.deleteItem(payment);
    }

    /**
     * Returns up to {@code limit} items via a full-table scan.
     * Use sparingly — full scans are expensive on large tables.
     */
    public List<Payment> scan(int limit) {
        List<Payment> results = new ArrayList<>();
        for (Page<Payment> page : table.scan(r -> r.limit(limit))) {
            results.addAll(page.items());
            if (results.size() >= limit) break;
        }
        return results;
    }

    // --- helpers ---

    private List<Payment> queryGsi(String indexName, String partitionValue,
                                    int limit, String lastEvaluatedKey) {
        DynamoDbIndex<Payment> index = table.index(indexName);
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(partitionValue).build()))
                .limit(limit);

        if (lastEvaluatedKey != null && !lastEvaluatedKey.isBlank()) {
            requestBuilder.exclusiveStartKey(
                    Map.of("id", AttributeValue.builder().s(lastEvaluatedKey).build()));
        }

        List<Payment> results = new ArrayList<>();
        for (Page<Payment> page : index.query(requestBuilder.build())) {
            results.addAll(page.items());
            break; // one page only — caller controls pagination via lastEvaluatedKey
        }
        return results;
    }
}

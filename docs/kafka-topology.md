# RamPay Kafka Topology

## Overview

RamPay uses Apache Kafka as its event backbone. All inter-service communication goes
through topics defined here. The payment service is the sole producer of payment-lifecycle
events; the fraud service (not yet deployed) is the producer of `anomaly-detected`. Both
services use the consumer group `payment-service-group`.

---

## Topics

### Core Topics

| Topic | Partitions | Replication | Retention | Purpose |
|---|---|---|---|---|
| `payment-created` | 3 | 1 | 7 days (Kafka default) | Emitted when a new payment is persisted. Consumed by fraud/analytics services. |
| `payment-approved` | 3 | 1 | 7 days | Emitted when a payment transitions to APPROVED. Consumed by notification/analytics services. |
| `payment-failed` | 3 | 1 | 7 days | Emitted when a payment transitions to FAILED (manual or fraud-triggered). |
| `payment-refunded` | 3 | 1 | 7 days | Emitted when a refund is processed. |
| `anomaly-detected` | 3 | 1 | 7 days | Published by the fraud service when a payment's risk score exceeds threshold. Consumed by the payment service to trigger automatic failure. |

### Dead-Letter Queue (DLT) Topics

DLT records are routed here automatically by `DefaultErrorHandler` after all retries are
exhausted. Each DLT topic is a 1:1 mirror of its source topic with a `.DLT` suffix.

| Topic | Partitions | Replication | Purpose |
|---|---|---|---|
| `payment-created.DLT` | 1 | 1 | Unprocessable `payment-created` messages after 3 retries. |
| `payment-approved.DLT` | 1 | 1 | Unprocessable `payment-approved` messages after 3 retries. |
| `payment-failed.DLT` | 1 | 1 | Unprocessable `payment-failed` messages after 3 retries. |
| `payment-refunded.DLT` | 1 | 1 | Unprocessable `payment-refunded` messages after 3 retries. |
| `anomaly-detected.DLT` | 1 | 1 | Unprocessable `anomaly-detected` messages after 3 retries. |

DLT topics intentionally use 1 partition to preserve ordering of dead-lettered records and
to keep the error stream easy to inspect and replay.

---

## Consumer Groups

| Consumer Group | Service | Topics Consumed | Description |
|---|---|---|---|
| `payment-service-group` | payment-service | `anomaly-detected` | Listens for fraud signals and calls `PaymentService.failPayment()`. |
| `fraud-service-group` | fraud-service _(planned)_ | `payment-created` | Scores each new payment for anomalies. |
| `notification-service-group` | notification-service _(planned)_ | `payment-approved`, `payment-failed`, `payment-refunded` | Sends user-facing notifications. |
| `analytics-service-group` | analytics-service _(planned)_ | all core topics | Feeds the analytics pipeline. |

---

## Dead-Letter Queue Strategy

1. **Retry policy** — `DefaultErrorHandler` applies exponential back-off: 3 attempts, 1 s
   initial delay, 2x multiplier (1 s → 2 s → 4 s before the final failure).
2. **Recovery** — `DeadLetterPublishingRecoverer` publishes the failed record to
   `<source-topic>.DLT` using the non-transactional producer so the DLT write succeeds
   even when no Kafka transaction is active.
3. **DLT record headers** — Spring Kafka automatically adds the following headers to every
   DLT record: `kafka_dlt-original-topic`, `kafka_dlt-original-partition`,
   `kafka_dlt-original-offset`, `kafka_dlt-exception-fqcn`, `kafka_dlt-exception-message`.
4. **Replay** — dead-lettered records can be replayed by re-consuming from the DLT topic
   with a dedicated consumer, correcting the root cause, and republishing to the original
   topic. No tooling is wired yet; this is a manual operation.

---

## Exactly-Once Semantics

RamPay implements exactly-once delivery via the **transactional outbox pattern** combined
with an **idempotent/transactional Kafka producer**.

### Transactional Outbox Pattern

1. When `PaymentService` creates or transitions a payment, `EventPublisherService` writes an
   `OutboxEvent` row to the `outbox_events` table **in the same database transaction** as the
   payment state change. The payment never changes state without a corresponding outbox record.
2. A scheduled poller (`EventPublisherService.processOutboxEvents`) reads `PENDING` outbox
   records and publishes them to Kafka using the transactional `KafkaTemplate`.
3. On success the row is marked `PUBLISHED`. On failure the retry counter is incremented; at
   `max-retries` (3) the row transitions to `FAILED` for manual inspection.

### Idempotent and Transactional Producer

The primary `ProducerFactory` is configured with:

| Property | Value | Reason |
|---|---|---|
| `enable.idempotence` | `true` | Prevents duplicate records caused by producer retries. |
| `acks` | `all` | Required for idempotence; ensures the leader and all in-sync replicas acknowledge the write. |
| `retries` | `Integer.MAX_VALUE` | Required for idempotence; the broker deduplicates using sequence numbers. |
| `max.in.flight.requests.per.connection` | `5` | Maximum allowed with idempotence enabled. |
| `transactional.id` prefix | `rampay-payment-tx-` | Enables transactional publishing; the broker tracks which PID+sequence was committed so duplicate sends from restarted producers are idempotent. |

### read_committed Isolation

All consumers set `isolation.level=read_committed`. This prevents consumers from seeing
messages that were written as part of a Kafka transaction that has not yet been committed
(or was aborted). Without this, an aborted outbox flush could deliver a payment event to
downstream services before the database transaction rolls back.

### Single-Broker Configuration

The following broker settings are required for exactly-once to work with a single Kafka
broker (replication factor = 1). They are set in `infra/docker-compose.yml`:

| Setting | Value |
|---|---|
| `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | 1 |
| `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR` | 1 |
| `KAFKA_MIN_INSYNC_REPLICAS` | 1 |

---

## Event Schema References

Event payloads are serialized as JSON strings. Schema definitions live in the `events`
package of the payment service.

| Event Class | Topic | Key Fields |
|---|---|---|
| `PaymentCreatedEvent` | `payment-created` | `paymentId`, `fromAccountId`, `toAccountId`, `amount`, `currency` |
| `PaymentApprovedEvent` | `payment-approved` | `paymentId` |
| `PaymentFailedEvent` | `payment-failed` | `paymentId`, `reason` |
| `PaymentRefundedEvent` | `payment-refunded` | `paymentId`, `refundAmount` |
| `AnomalyDetectedEvent` | `anomaly-detected` | `paymentId`, `riskScore` |

All events use the `paymentId` (UUID) as the Kafka message key, which ensures that all
events for a given payment are always routed to the same partition and consumed in order.

---

## Configuration Reference

| Property | Location | Default |
|---|---|---|
| `spring.kafka.bootstrap-servers` | `application.yaml` / env `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `spring.kafka.producer.transaction-id-prefix` | `application.yaml` | `rampay-payment-tx-` |
| `spring.kafka.producer.enable-idempotence` | `application.yaml` | `true` |
| `spring.kafka.producer.acks` | `application.yaml` | `all` |
| `spring.kafka.consumer.isolation-level` | `application.yaml` | `read_committed` |
| `spring.kafka.consumer.max-poll-interval-ms` | `application.yaml` | `300000` |
| `spring.kafka.consumer.session-timeout-ms` | `KafkaConfig.java` | `30000` |

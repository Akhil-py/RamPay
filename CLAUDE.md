# RamPay — Claude Code Context

Cloud-native payments and fraud detection platform. Event-driven microservices on AWS.

---

## Repo layout

```
RamPay/
├── services/
│   ├── payment-service/     # Java 17 / Spring Boot 3.5.7 — the core service
│   ├── fraud-service/       # Python 3.12 / FastAPI — rule-based fraud engine
│   ├── notification-service/ # Empty stub — intentionally deferred
│   └── analytics-service/   # Empty stub — intentionally deferred
├── infra/
│   ├── docker-compose.yml   # Full local stack (run with `make demo`)
│   ├── terraform/           # AWS IaC — 11 .tf files, single terraform apply
│   ├── prometheus.yml
│   ├── otel-collector-config.yaml
│   └── grafana/provisioning/
├── demo/
│   ├── demo.sh              # Single-command demo script
│   └── README.md
├── docs/                    # Architecture, API reference, event catalogue
├── Makefile                 # make demo / demo-down / build / logs / ps
└── CLAUDE.md                # This file
```

---

## Key architectural decisions (non-negotiable — do not reverse)

**Database: DynamoDB, not PostgreSQL.** The service was migrated from JPA/PostgreSQL to AWS DynamoDB Enhanced Client SDK. This is deliberate and resume-critical. Never suggest reverting to JPA or adding Hibernate.

**Fraud service: rule-based Python, no ML.** FastAPI + Redis rules only. No PyTorch, no scikit-learn.

**Outbox pattern for exactly-once Kafka delivery.** Payments are written to a `PaymentOutbox` DynamoDB table first; a scheduled poller flushes them to Kafka inside `kafkaTemplate.executeInTransaction()`. Do not bypass this with direct `kafkaTemplate.send()` calls.

**Fail-open fraud detection.** If Redis is unavailable, the fraud service returns NONE risk — it never blocks payments. This is intentional.

---

## Payment service (`services/payment-service/`)

**Stack:** Java 17, Spring Boot 3.5.7, Maven

**Persistence:** AWS DynamoDB Enhanced Client (`software.amazon.awssdk:dynamodb-enhanced:2.25.16`). No Spring Data JPA, no PostgreSQL driver.

**DynamoDB tables:**

| Table | PK | SK | GSIs |
|---|---|---|---|
| `Payments` | `id` (S) | — | `fromAccountId-index`, `toAccountId-index`, `status-index` (status+createdAt), `idempotencyKey-index` |
| `PaymentOutbox` | `id` (S) | `createdAt` (S) | `status-createdAt-index` |

All fields stored as `String` — UUIDs as strings, `BigDecimal` as `toPlainString()`, `Instant` as ISO-8601. Type conversion happens in `PaymentMapper` and `PaymentService`.

**Key source files:**

| File | Role |
|---|---|
| `config/DynamoDbConfig.java` | DynamoDB client bean; `DYNAMODB_LOCAL=true` uses `http://dynamodb-local:8000` with dummy credentials |
| `config/DynamoDbTableInitializer.java` | `CommandLineRunner` — creates both tables on startup, catches `ResourceInUseException` silently |
| `config/KafkaConfig.java` | Exactly-once: transactional producer, `read_committed` consumer, `DefaultErrorHandler` with exponential backoff → DLT |
| `models/Payment.java` | `@DynamoDbBean` — getters carry partition/GSI key annotations |
| `models/OutboxEvent.java` | `@DynamoDbBean` — composite PK (id HASH + createdAt RANGE) |
| `repositories/PaymentRepository.java` | Concrete `@Repository` — not a JPA interface |
| `repositories/OutboxRepository.java` | Concrete `@Repository`; `findPendingEvents(int limit)` queries `status-createdAt-index` |
| `services/PaymentService.java` | No `@Transactional` — DynamoDB SDK does not use Spring's transaction manager |
| `services/EventPublisherService.java` | Outbox poller (`@Scheduled` every 5s); wraps each Kafka send in `kafkaTemplate.executeInTransaction()` with `correlationId` header |
| `services/EventConsumerService.java` | `@KafkaListener` on `anomaly-detected`; reads `correlationId` header into MDC; re-throws exceptions so `DefaultErrorHandler` routes to DLT |
| `services/RedisCacheService.java` | Redis cache for payments + idempotency keys; all IDs as `String` |

**Kafka topics (auto-created by `KafkaConfig` via `KafkaAdmin`):**

| Topic | Partitions | Purpose |
|---|---|---|
| `payment-created` | 3 | Published on payment creation |
| `payment-approved` | 3 | Published on approval |
| `payment-failed` | 3 | Published on failure |
| `payment-refunded` | 3 | Published on refund |
| `anomaly-detected` | 3 | Published by fraud-service, consumed by payment-service |
| `*.DLT` (5 topics) | 1 each | Dead letter queues — exponential backoff: 1s, 2s, 4s (3 retries) |

**Configuration env vars:**

| Var | Default | Notes |
|---|---|---|
| `DYNAMODB_ENDPOINT` | `http://localhost:8000` | Override for DynamoDB Local |
| `DYNAMODB_LOCAL` | `true` | `false` in AWS — uses `DefaultCredentialsProvider` |
| `DYNAMODB_TABLE_PAYMENTS` | `Payments` | |
| `DYNAMODB_TABLE_OUTBOX` | `PaymentOutbox` | |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://localhost:4317` | |

**Observability:** Prometheus at `/actuator/prometheus`, health at `/actuator/health`, structured JSON logging via LogstashEncoder (`logback-spring.xml`) with `traceId`, `spanId`, `paymentId`, `correlationId` MDC fields. OTel auto-config via Spring Boot (`management.tracing`).

**Known limitations:**
- `getAllPayments` uses a DynamoDB scan — no cursor pagination yet
- Integration tests use Mockito mocks for repos, not DynamoDB Local
- `contextLoads()` test needs a mock `DynamoDbClient` bean or DynamoDB Local running

---

## Fraud service (`services/fraud-service/`)

**Stack:** Python 3.12, FastAPI, `confluent-kafka` (not `kafka-python`), Redis, structlog, prometheus_client

**Ports:** 8001

**Kafka:** Consumes `payment-created` (group: `fraud-service-group`), publishes `anomaly-detected`

**Fraud rules (evaluated in this order):**

| Rule | Trigger | Risk | Redis key pattern |
|---|---|---|---|
| Blocklist | Account in Redis | HIGH | `blocklist:{fromAccountId}` |
| Velocity | >5 payments in 60s | HIGH | `velocity:{fromAccountId}` (sorted set) |
| Large amount | >$10,000 | MEDIUM | — |
| Very large amount | >$50,000 | HIGH | — |
| Daily limit | >$100,000 cumulative | HIGH | `daily_total:{fromAccountId}` |
| Geo anomaly | Country change within 1h | HIGH | `last_country:{fromAccountId}`, `last_payment_time:{fromAccountId}` |

Only HIGH_RISK results publish `anomaly-detected`. MEDIUM_RISK is logged and counted in Prometheus but does not trigger a Kafka event.

**Key files:**

| File | Role |
|---|---|
| `app/fraud_engine.py` | `evaluate(event) -> FraudDecision` — all 4 rules, returns evaluation_ms |
| `app/redis_client.py` | Redis helpers; every function catches `RedisError`, increments `redis_errors_total`, returns safe default |
| `app/kafka_consumer.py` | Poll loop in daemon thread; binds `payment_id` + `correlation_id` to structlog contextvars per message |
| `app/kafka_producer.py` | `publish_anomaly()` with `acks=all`, `enable.idempotence=true`, `paymentId` as message key |
| `app/metrics.py` | Prometheus counters/histograms: `payments_evaluated_total`, `fraud_evaluation_duration_seconds`, `anomalies_detected_total`, `redis_errors_total` |
| `app/main.py` | FastAPI app, lifespan starts consumer thread, `/health`, `/rules`, `/metrics` |

**API endpoints:** `GET /health`, `GET /rules`, `GET /metrics` (Prometheus)

---

## Local dev stack

Run everything: `make demo` (or `chmod +x demo/demo.sh && ./demo/demo.sh`)

Tear down: `make demo-down`

**Service URLs when running locally:**

| Service | URL |
|---|---|
| payment-service | http://localhost:8080 |
| fraud-service | http://localhost:8001 |
| DynamoDB Local | http://localhost:8000 |
| Kafka | localhost:29092 (host), kafka:9092 (internal) |
| Redis | localhost:6379 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |
| Jaeger | http://localhost:16686 |
| OTel Collector | localhost:4317 (gRPC), localhost:4318 (HTTP) |

**Startup order** (enforced via docker-compose healthchecks):
zookeeper → kafka → redis + dynamodb-local → payment-service + fraud-service → observability stack

**To add a blocklisted account manually:**
```bash
docker compose -f infra/docker-compose.yml exec redis redis-cli SET "blocklist:ACC-001" "true"
```

---

## Terraform (`infra/terraform/`)

**Target:** Single `terraform apply` provisions the full AWS stack.

**Resources:**

| File | What it creates |
|---|---|
| `vpc.tf` | VPC (10.0.0.0/16), 2 public + 2 private subnets, IGW, NAT GW |
| `eks.tf` | EKS 1.30 cluster, managed node group (t3.medium, min=2/max=5), OIDC provider |
| `dynamodb.tf` | `Payments` + `PaymentOutbox` tables — schema matches service exactly, PITR + SSE |
| `elasticache.tf` | Redis 7.1 (cache.t3.micro) in private subnets |
| `msk.tf` | MSK Kafka 3.6.0 (kafka.t3.small, 2 brokers), exactly-once broker config |
| `sns_sqs.tf` | SNS topic → SQS queue, DLQ with redrive (maxReceiveCount=3) |
| `iam.tf` | EKS node SG, EKS roles, IRSA roles for payment-service (DynamoDB) and fraud-service |
| `ecr.tf` | `rampay/payment-service` and `rampay/fraud-service` repos, lifecycle: keep last 10 |

**Key variables:** `aws_region` (default: us-east-1), `environment` (default: dev), `project_name` (default: rampay)

**Deploy:**
```bash
cd infra/terraform
terraform init
terraform plan -var="environment=dev"
terraform apply -var="environment=dev"
```

Note: MSK takes 15–20 minutes to provision.

---

## Observability

**Structured logging:** Java service uses LogstashEncoder (JSON). Python service uses structlog with JSON renderer. Both propagate `correlationId` via Kafka message headers.

**Tracing flow:** payment-service → OTLP gRPC → otel-collector → Jaeger. 100% sampling in dev.

**Metrics:** Grafana dashboard `rampay-overview` (uid: `rampay-overview`) auto-provisioned with 5 panels covering payment rate, fraud detections, p99 latency (both services), and Redis errors.

**Prometheus scrape targets:**
- `payment-service:8080/actuator/prometheus`
- `fraud-service:8001/metrics`

---

## Git conventions

Conventional commits throughout: `feat:`, `fix:`, `chore:`, `docs:`, `ci:` with scope in parens e.g. `feat(fraud-service):`.

Never commit:
- `.vscode/` (in `.gitignore`)
- AWS credentials or secrets
- `.env` files other than `infra/.env` (which contains only non-sensitive local defaults)

---

## What is intentionally not built

- `services/notification-service/` — stub only, deferred
- `services/analytics-service/` — stub only, deferred
- Kubernetes manifests / Helm charts — EKS cluster is Terraform-provisioned but k8s deployments are not yet written
- Frontend / API gateway
- Auth / JWT
- Cursor-based pagination on `getAllPayments` (currently uses DynamoDB scan)

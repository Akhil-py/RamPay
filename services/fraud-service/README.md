# RamPay Fraud Detection Service

A rule-based fraud detection microservice for the RamPay payment platform. Written in Python with FastAPI.

---

## Architecture

```
payment-service
      |
      | payment-created (Kafka topic)
      v
fraud-service  ──── Redis (velocity, blocklist, geo state, daily totals)
      |
      | anomaly-detected (Kafka topic)
      v
payment-service (marks payment FAILED via AnomalyDetected consumer)
```

The service runs a single background thread that polls the `payment-created` Kafka topic. For each message it:

1. Deserialises the JSON payload into a `PaymentCreatedEvent`
2. Runs all four fraud rules (< 100 ms total, Redis is the only I/O)
3. If the payment is HIGH_RISK, publishes an `AnomalyDetectedEvent` to `anomaly-detected`
4. Commits the Kafka offset only after processing completes

**Fail-open behaviour:** if Redis is unavailable, every rule returns NONE risk so that genuine payments are never blocked by an infrastructure outage.

---

## Fraud Rules

| ID | Trigger | Risk Level |
|----|---------|------------|
| `blocklist` | `blocklist:{fromAccountId}` key exists in Redis | HIGH |
| `velocity` | > 5 payments from the same account in a 60-second window | HIGH |
| `large_amount` | Single transaction > $10,000 | MEDIUM |
| `very_large_amount` | Single transaction > $50,000 | HIGH |
| `daily_limit` | Rolling 24 h total from the same account > $100,000 | HIGH |
| `geo_anomaly` | Country change within 1 h (impossible travel) | HIGH |

**Evaluation order:** blocklist is checked first and short-circuits — blocklisted accounts skip all other rules.

**Publishing behaviour:** Only HIGH_RISK decisions publish an `anomaly-detected` event. MEDIUM_RISK decisions are logged and counted in Prometheus but do not block the payment.

### Redis key patterns

| Key | Type | TTL | Purpose |
|-----|------|-----|---------|
| `velocity:{fromAccountId}` | Sorted Set | 120 s | Payment timestamps in a 60 s sliding window |
| `daily_total:{fromAccountId}` | String (float) | 86400 s | Rolling 24 h payment total |
| `blocklist:{fromAccountId}` | Any | Admin-controlled | Account blocklist |
| `last_country:{fromAccountId}` | String | 86400 s | Last known country code |
| `last_payment_time:{fromAccountId}` | String (float) | 86400 s | Unix timestamp of last payment |

---

## Running Locally

### Prerequisites

- Python 3.12+
- Running Redis instance
- Running Kafka broker with topics `payment-created` and `anomaly-detected` created

### Setup

```bash
cd services/fraud-service

# Create and activate a virtual environment
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Configure environment
cp .env.example .env
# Edit .env with your local values

# Run
uvicorn app.main:app --host 0.0.0.0 --port 8001 --reload
```

### Running with Docker Compose

From the repo root:

```bash
docker-compose -f infra/docker-compose.yml up fraud-service
```

This starts the service alongside Kafka, Zookeeper, and Redis.

---

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Comma-separated list of Kafka brokers |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | _(empty)_ | Redis password (leave blank if not set) |

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/health` | Liveness / readiness probe. Returns `{ status, redis }` |
| `GET` | `/rules` | List all active fraud rules with descriptions and thresholds |
| `GET` | `/metrics` | Prometheus metrics (scraped by your monitoring stack) |

### Example `/health` response

```json
{ "status": "ok", "redis": "ok" }
```

### Example `/rules` response

```json
{
  "rules": [
    { "id": "blocklist", "description": "Account on Redis blocklist", "risk": "HIGH", "detail": "..." },
    ...
  ]
}
```

---

## Prometheus Metrics

| Metric | Type | Labels | Description |
|--------|------|--------|-------------|
| `payments_evaluated_total` | Counter | `risk_level` | Payments processed by risk outcome |
| `fraud_evaluation_duration_seconds` | Histogram | — | Per-payment evaluation latency |
| `anomalies_detected_total` | Counter | `reason` | Anomalies broken down by trigger reason |
| `redis_errors_total` | Counter | — | Redis operation failures |

---

## Adding a New Rule

1. Add a `_rule_<name>` function in `app/fraud_engine.py` following the existing pattern:
   - Accept `account_id`, relevant fields, and the mutable `reasons: list[str]`
   - Append to `reasons` when triggered
   - Return one of `"NONE"`, `"MEDIUM"`, or `"HIGH"`
2. Call `_merge_risk(risk, _rule_<name>(...))` in the `evaluate()` function
3. Add any Redis helpers needed to `app/redis_client.py`
4. Add a Prometheus counter label or new counter in `app/metrics.py` if required
5. Document the new rule in this README's rules table

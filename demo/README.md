# RamPay Demo

A single-command demo that shows a real-time payment processing system with
live fraud detection, distributed tracing, and Prometheus / Grafana metrics.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         RamPay Stack                                 │
│                                                                      │
│  ┌──────────────────┐   POST /payments    ┌──────────────────────┐  │
│  │   demo.sh (curl) │ ─────────────────>  │   payment-service    │  │
│  └──────────────────┘                     │   :8080              │  │
│                                           │   Spring Boot / Java │  │
│                                           └────────┬─────────────┘  │
│                                                    │                 │
│                                         PaymentCreated event        │
│                                                    │                 │
│                                           ┌────────▼─────────────┐  │
│                                           │       Kafka          │  │
│                                           │   :9092              │  │
│                                           └────────┬─────────────┘  │
│                                                    │                 │
│                                         Consumes events             │
│                                                    │                 │
│                                           ┌────────▼─────────────┐  │
│                                           │   fraud-service      │  │
│                                           │   :8001              │  │
│                                           │   FastAPI / Python   │  │
│                                           └────────┬─────────────┘  │
│                                                    │                 │
│              ┌──────────────────┐        AnomalyDetected event      │
│              │     Redis :6379  │ <──────────────  │                 │
│              │  • velocity sets │                  │                 │
│              │  • daily totals  │        ┌─────────▼──────────────┐ │
│              │  • blocklist     │        │   Kafka (reply topic)  │ │
│              └──────────────────┘        └─────────┬──────────────┘ │
│                                                    │                 │
│                                         payment-service consumes    │
│                                         → sets status = FAILED      │
│                                                    │                 │
│  ┌──────────────────┐  ┌────────────┐   ┌─────────▼──────────────┐ │
│  │  Grafana  :3000  │  │ Jaeger     │   │  DynamoDB Local :8000  │ │
│  │  Prometheus :9090│  │ :16686     │   │  (payments table)      │ │
│  └──────────────────┘  └────────────┘   └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

---

## What Each Batch Demonstrates

| Batch | Accounts | Amount | Rule Triggered | Expected Result |
|-------|----------|--------|----------------|-----------------|
| 1 | ACC-001 → ACC-002 | 3 × ($150, $250, $75) | None | PENDING / COMPLETED |
| 2 | ACC-003 → ACC-004 | $15,000 | `large_amount` (MEDIUM) | PENDING — not auto-blocked |
| 3 | ACC-BURST → ACC-002 | 7 × $50 fast | `velocity` >5/60s (HIGH) | 6th+ payment FAILED |
| 4 | ACC-BLOCKED → ACC-002 | $100 | `blocklist` (HIGH) | FAILED immediately |
| 5 | ACC-VIP → ACC-002 | $75,000 | `very_large_amount` >$50k (HIGH) | FAILED |

### Fraud rules (from `GET http://localhost:8001/rules`)

- **blocklist** — account ID in Redis key `blocklist:{id}` → HIGH risk
- **velocity** — more than 5 payments in 60 seconds → HIGH risk
- **large_amount** — single transaction > $10,000 → MEDIUM risk
- **very_large_amount** — single transaction > $50,000 → HIGH risk
- **daily_limit** — rolling 24 h total > $100,000 → HIGH risk
- **geo_anomaly** — country change within 1 hour → HIGH risk

---

## Prerequisites

| Requirement | Minimum |
|---|---|
| Docker Desktop | 4.x or later |
| RAM allocated to Docker | 4 GB |
| CPU cores allocated | 4 |
| Free disk space | 5 GB |
| `jq` | any recent version |
| `curl` | any recent version |

**Install jq:**

```bash
# macOS
brew install jq

# Ubuntu / Debian / WSL
sudo apt-get install -y jq

# Windows (Chocolatey)
choco install jq
```

---

## How to Run

```bash
# 1. Make the script executable (once)
chmod +x demo/demo.sh

# 2. Run the demo
./demo/demo.sh

# Alternatively, use the Makefile shortcut from the repo root
make demo
```

The script will:
1. Start all containers (`docker compose up -d --build`)
2. Poll each service until healthy (timeout: 120 s)
3. Seed the Redis blocklist
4. Run 5 transaction batches with colour-coded output
5. Print fraud metrics and observability links

Total runtime: approximately 3–5 minutes (first run builds images; subsequent runs are faster).

---

## What to Watch

### Grafana — http://localhost:3000
- Login: `admin` / `admin`
- Navigate to **Dashboards → RamPay Overview**
- Key panels: payment throughput, fraud rate, p99 latency, DynamoDB errors

### Jaeger Traces — http://localhost:16686
- Select service: `payment-service`
- Look for traces spanning `POST /payments` → Kafka publish → fraud evaluation → status update

### Fraud Service Metrics — http://localhost:8001/metrics
```
# Watch anomalies in real time:
watch -n2 'curl -s http://localhost:8001/metrics | grep anomalies'
```

### Fraud Service Rules — http://localhost:8001/rules
Returns a JSON description of every active rule and its risk level.

### Live Logs
```bash
# All services
docker compose -f infra/docker-compose.yml logs -f

# Fraud service only (shows rule evaluations as JSON)
docker compose -f infra/docker-compose.yml logs -f fraud-service

# Payment service only
docker compose -f infra/docker-compose.yml logs -f payment-service
```

---

## How to Teardown

```bash
./demo/demo.sh --teardown

# Or via Make
make demo-down
```

This stops all containers and removes all named volumes (DynamoDB data, Prometheus
data, Grafana dashboards). The next `./demo/demo.sh` run starts fresh.

---

## Tips for Recording a LinkedIn Demo

### Suggested Screen Layout

```
┌────────────────────────────┬────────────────────────────┐
│                            │                            │
│   Terminal (full height)   │   Browser — Grafana        │
│   running demo.sh          │   (RamPay Overview dash)   │
│                            │                            │
│   • colour-coded output    │  ┌──────────────────────┐  │
│   • fraud alerts in red    │  │  payment rate graph  │  │
│                            │  ├──────────────────────┤  │
│                            │  │  fraud rate counter  │  │
│                            │  └──────────────────────┘  │
└────────────────────────────┴────────────────────────────┘
```

### Narration Cues

1. **Stack start** — "One command starts the entire distributed system."
2. **Batch 1** — "Normal payments flow through in milliseconds."
3. **Batch 3 (velocity)** — "Seven rapid payments trigger the velocity rule —
   the fraud engine detects the burst in real time over Kafka."
4. **Batch 4 (blocklist)** — "Blocklisted accounts are rejected the moment
   the event hits the fraud service — no polling, pure event-driven."
5. **Grafana** — "Every metric is live in Grafana. The fraud rate panel spiked
   the moment the burst started."
6. **Jaeger** — "Full distributed traces span from the REST endpoint, through
   Kafka, into the fraud engine, and back."

### Recording Tips

- Use a **16:9** window at 1920×1080 — terminal on the left half, browser on the right.
- Set terminal font size to 18–20 pt so text is readable in the video.
- Run the demo once beforehand so images are cached; the second run will be faster and cleaner.
- Pause at the Grafana dashboard after the velocity burst — the fraud rate spike makes a great freeze-frame.
- Keep the demo under 90 seconds for LinkedIn (cut the Prometheus panel if needed).

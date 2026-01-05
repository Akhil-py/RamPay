# 🏗️ RamPay Architecture Overview

## 1. System Summary
RamPay (Rapid Access Money Platform) is a cloud-native, event-driven payments system that processes, verifies, and analyzes transactions in real time.  
The system uses microservices communicating through an event bus (Kafka or AWS SNS/SQS), ensuring scalability and fault tolerance.

---

## 2. Core Services

### 🧾 Payment Service
- Handles payment creation, approval, and refund operations.
- Persists transaction data in PostgreSQL.
- Publishes events such as `PaymentCreated`, `PaymentApproved`, and `PaymentFailed`.
- Uses Redis for caching and idempotency checks.

### 🤖 Fraud Detection Service
- Consumes `PaymentCreated` events.
- Uses a trained ML model (PyTorch/TensorFlow) to flag anomalies.
- Publishes an `AnomalyDetected` event.
- Can request manual review or auto-hold suspicious transactions.

### 💬 Notification Service
- Listens to `PaymentCreated`, `PaymentApproved`, and `AnomalyDetected`.
- Sends email/SMS updates via AWS SES or Twilio.
- Logs all outbound messages for audit purposes.

### 📊 Analytics Service
- Subscribes to all events.
- Aggregates metrics such as transaction volume, fraud rate, and latency.
- Exposes REST endpoints and dashboards powered by Grafana.

---

## 3. Infrastructure Components
- **Database:** PostgreSQL (primary)  
- **Cache:** Redis  
- **Messaging:** Kafka or AWS SNS/SQS  
- **Orchestration:** Docker + Kubernetes (EKS)  
- **CI/CD:** GitHub Actions  
- **Infrastructure as Code:** Terraform  
- **Monitoring:** Prometheus, Grafana, CloudWatch  
- **Authentication:** JWT / OAuth2  

---

## 4. High-Level Flow

1. User sends a payment request → Payment Service.  
2. Payment Service validates input → saves to Postgres → emits `PaymentCreated`.  
3. Fraud Detection Service consumes `PaymentCreated`, runs ML check, emits `AnomalyDetected` if needed.  
4. Notification Service sends updates to user.  
5. Analytics Service logs every event for metrics.  

```mermaid
flowchart LR
  %% Clients
  subgraph Client
    U[User / Frontend (React)]
  end

  %% API Layer
  subgraph API[API Layer]
    G[API Gateway<br/>Spring Boot]
  end

  %% Event Bus
  subgraph Bus[Event Bus]
    K[(Kafka / AWS SNS/SQS)]
  end

  %% Backend Services
  subgraph Srv[Backend Services]
    P[Payment Service]
    F[Fraud Detection Service]
    N[Notification Service]
    A[Analytics Service]
  end

  %% Data Stores
  subgraph Data[Data Stores]
    PG[(PostgreSQL)]
    R[(Redis Cache)]
  end

  %% Infra & Monitoring
  subgraph Infra[Infra & Monitoring]
    T[Terraform / IaC]
    M[Prometheus / Grafana]
    CW[CloudWatch]
  end

  %% Flows
  U -->|REST| G
  G -->|REST/gRPC| P

  P --> PG
  P --> R

  P -->|publish events| K
  K -->|consume events| F
  K -->|consume events| N
  K -->|consume events| A

  F --> PG
  F -->|AnomalyDetected| K

  N -->|email/SMS| U

  A --> PG

  P --> M
  F --> M
  N --> M
  A --> M

  T --> API
  T --> Srv
  T --> Data
  T --> Infra
  M --> CW
```

---

## 5. Deployment Topology

AWS ECS/EKS Cluster  
├── Payment Service (Dockerized)  
├── Fraud Service (Dockerized ML model)  
├── Notification Service  
├── Analytics Service  
├── Postgres (AWS RDS)  
├── Redis (AWS ElastiCache)  
└── Kafka / SNS-SQS Event Bus  

---

## 6. Scalability & Fault Tolerance
- Stateless microservices for horizontal scaling.  
- Retry + DLQ (dead-letter queue) for failed events.  
- Distributed tracing via OpenTelemetry.  
- Health checks via Spring Boot Actuator endpoints.  

---

## 7. Security
- All secrets managed via AWS Secrets Manager.  
- Encrypted database connections (TLS).  
- Role-based access control for internal APIs.  

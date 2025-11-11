# 🛠️ RamPay Development Roadmap

## Week 1 — Core Setup
- Initialize monorepo (infra, services, docs)
- Build Payment Service skeleton (Spring Boot)
- Add Postgres + Redis via Docker Compose
- Implement /health and /payments endpoints

## Week 2 — Event-Driven Layer
- Add Kafka or AWS SNS/SQS event bus
- Emit PaymentCreated and PaymentApproved events
- Stub Fraud Detection Service (consumer)
- Add basic CI pipeline

## Week 3 — ML Fraud Detection
- Build ML model microservice with FastAPI
- Integrate PyTorch anomaly detector
- Emit AnomalyDetected event
- Start implementing Notification Service

## Week 4 — Cloud Deployment
- Terraform IaC for AWS (ECS/EKS, RDS, ElastiCache)
- Deploy with GitHub Actions
- Add Prometheus + Grafana monitoring
- Write production-ready documentation

## Future Extensions
- Add user authentication service
- Support refunds, batch payouts
- Integrate real payment gateways (Stripe sandbox)
- Add distributed tracing (OpenTelemetry)

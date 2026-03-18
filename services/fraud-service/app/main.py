import threading
from contextlib import asynccontextmanager

import structlog
from fastapi import FastAPI
from prometheus_client import make_asgi_app

from app.kafka_consumer import start_consumer, stop_consumer
from app.redis_client import redis_client
from app.metrics import (  # noqa: F401 — imported to ensure counters are registered
    anomalies_detected_total,
    fraud_evaluation_duration_seconds,
    payments_evaluated_total,
    redis_errors_total,
)

logger = structlog.get_logger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("fraud_service_starting")
    thread = threading.Thread(target=start_consumer, daemon=True, name="kafka-consumer")
    thread.start()
    logger.info("kafka_consumer_thread_started")

    yield

    logger.info("fraud_service_stopping")
    stop_consumer()
    thread.join(timeout=10)
    logger.info("fraud_service_stopped")


app = FastAPI(
    title="RamPay Fraud Detection Service",
    version="1.0.0",
    lifespan=lifespan,
)

# Mount Prometheus metrics endpoint
metrics_app = make_asgi_app()
app.mount("/metrics", metrics_app)


@app.get("/health", tags=["ops"])
def health():
    """
    Liveness / readiness probe.

    Returns ``status: ok`` when Redis is reachable, ``status: degraded`` otherwise.
    The service operates in fail-open mode — it continues processing even when
    Redis is unavailable (decisions will default to NONE risk).
    """
    try:
        redis_client.ping()
        redis_ok = True
    except Exception:
        redis_ok = False

    return {
        "status": "ok" if redis_ok else "degraded",
        "redis": "ok" if redis_ok else "error",
    }


@app.get("/rules", tags=["ops"])
def list_rules():
    """
    Return a description of all active fraud rules and their risk levels.

    Useful for debugging and for operator tooling that needs to display
    which rules are in effect without reading source code.
    """
    return {
        "rules": [
            {
                "id": "blocklist",
                "description": "Account on Redis blocklist",
                "risk": "HIGH",
                "detail": "Key pattern: blocklist:{fromAccountId}. Set by an external admin process.",
            },
            {
                "id": "velocity",
                "description": ">5 payments in 60 s from the same account",
                "risk": "HIGH",
                "detail": "Uses a Redis sorted set (velocity:{fromAccountId}) with a 60-second sliding window.",
            },
            {
                "id": "large_amount",
                "description": ">$10,000 single transaction",
                "risk": "MEDIUM",
                "detail": "Amount threshold in the payment currency (USD equivalent assumed).",
            },
            {
                "id": "very_large_amount",
                "description": ">$50,000 single transaction",
                "risk": "HIGH",
                "detail": "Stricter single-transaction threshold.",
            },
            {
                "id": "daily_limit",
                "description": ">$100,000 rolling 24 h total from the same account",
                "risk": "HIGH",
                "detail": "Accumulated in Redis (daily_total:{fromAccountId}) with a 24 h TTL.",
            },
            {
                "id": "geo_anomaly",
                "description": "Country change within 1 h (impossible travel)",
                "risk": "HIGH",
                "detail": (
                    "Requires metadata.country_code on the payment event. "
                    "Compares against last_country:{fromAccountId} stored in Redis. "
                    "Triggered when country differs and last payment was < 3600 s ago."
                ),
            },
        ]
    }

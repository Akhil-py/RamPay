"""
Kafka consumer loop for the fraud service.

Subscribes to ``payment-created``, evaluates each message through the fraud
engine, and publishes ``anomaly-detected`` events for HIGH_RISK payments.

Designed to run as a daemon thread started from the FastAPI lifespan.
"""

import json
import os
import threading

from confluent_kafka import Consumer, KafkaError, KafkaException
import structlog

from app import fraud_engine
from app import kafka_producer
from app.metrics import (
    anomalies_detected_total,
    fraud_evaluation_duration_seconds,
    payments_evaluated_total,
)
from app.models import AnomalyDetectedEvent, PaymentCreatedEvent

import datetime

logger = structlog.get_logger(__name__)

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
PAYMENT_TOPIC = "payment-created"

# Shared stop event — set during shutdown to gracefully exit the poll loop.
_stop_event = threading.Event()


def stop_consumer() -> None:
    """Signal the consumer loop to exit."""
    _stop_event.set()


def _build_consumer() -> Consumer:
    return Consumer(
        {
            "bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS,
            "group.id": "fraud-service-group",
            "auto.offset.reset": "earliest",
            "enable.auto.commit": False,
        }
    )


def start_consumer() -> None:
    """
    Entry point for the background consumer thread.

    Polls ``payment-created`` indefinitely until ``stop_consumer()`` is called.
    Deserialization errors are logged and skipped — they never crash the thread.
    """
    consumer = _build_consumer()
    consumer.subscribe([PAYMENT_TOPIC])
    logger.info("kafka_consumer_started", topic=PAYMENT_TOPIC)

    try:
        while not _stop_event.is_set():
            msg = consumer.poll(timeout=1.0)

            if msg is None:
                continue

            if msg.error():
                if msg.error().code() == KafkaError._PARTITION_EOF:
                    # End of partition — not an error
                    logger.debug(
                        "kafka_partition_eof",
                        partition=msg.partition(),
                        offset=msg.offset(),
                    )
                else:
                    logger.error("kafka_consumer_error", error=str(msg.error()))
                continue

            # --- Deserialize ---
            try:
                raw = json.loads(msg.value().decode("utf-8"))
                event = PaymentCreatedEvent(**raw)
            except Exception as exc:
                logger.error(
                    "kafka_deserialization_error",
                    topic=msg.topic(),
                    partition=msg.partition(),
                    offset=msg.offset(),
                    error=str(exc),
                )
                # Commit and skip — don't let a bad message block the consumer
                consumer.commit(message=msg)
                continue

            # --- Evaluate ---
            try:
                decision = fraud_engine.evaluate(event)
            except Exception as exc:
                logger.error(
                    "fraud_evaluation_error",
                    payment_id=event.paymentId,
                    error=str(exc),
                )
                consumer.commit(message=msg)
                continue

            # --- Metrics ---
            payments_evaluated_total.labels(risk_level=decision.risk_level).inc()
            fraud_evaluation_duration_seconds.observe(decision.evaluation_ms / 1000)

            # --- Publish anomaly if HIGH_RISK ---
            if decision.flagged:
                for reason in decision.reasons:
                    anomalies_detected_total.labels(reason=reason).inc()

                anomaly = AnomalyDetectedEvent(
                    paymentId=event.paymentId,
                    riskScore=decision.risk_score,
                    message=", ".join(decision.reasons),
                    timestamp=datetime.datetime.utcnow().isoformat() + "Z",
                )
                try:
                    kafka_producer.publish_anomaly(anomaly)
                except Exception as exc:
                    logger.error(
                        "anomaly_publish_error",
                        payment_id=event.paymentId,
                        error=str(exc),
                    )
            elif decision.risk_level == "MEDIUM":
                # Log and count MEDIUM_RISK events — do not publish
                for reason in decision.reasons:
                    anomalies_detected_total.labels(reason=f"medium_{reason}").inc()
                logger.info(
                    "medium_risk_payment",
                    payment_id=event.paymentId,
                    reasons=decision.reasons,
                    evaluation_ms=decision.evaluation_ms,
                )

            # --- Commit offset only after successful processing ---
            consumer.commit(message=msg)

    except KafkaException as exc:
        logger.error("kafka_fatal_error", error=str(exc))
    finally:
        consumer.close()
        logger.info("kafka_consumer_stopped")

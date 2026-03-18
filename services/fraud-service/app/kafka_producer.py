import json
import os

from confluent_kafka import Producer
import structlog

from app.models import AnomalyDetectedEvent

logger = structlog.get_logger(__name__)

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
ANOMALY_TOPIC = "anomaly-detected"

_producer = Producer(
    {
        "bootstrap.servers": KAFKA_BOOTSTRAP_SERVERS,
        "acks": "all",
        "enable.idempotence": "true",
    }
)


def _delivery_report(err, msg):
    if err is not None:
        logger.error(
            "kafka_delivery_failed",
            topic=msg.topic(),
            key=msg.key(),
            error=str(err),
        )
    else:
        logger.debug(
            "kafka_delivery_success",
            topic=msg.topic(),
            partition=msg.partition(),
            offset=msg.offset(),
        )


def publish_anomaly(event: AnomalyDetectedEvent) -> None:
    """
    Serialize *event* and produce it to the ``anomaly-detected`` topic.

    Uses the paymentId as the Kafka message key so that all events for the
    same payment land on the same partition.
    """
    payload = event.model_dump_json().encode("utf-8")
    key = event.paymentId.encode("utf-8")

    try:
        _producer.produce(
            topic=ANOMALY_TOPIC,
            key=key,
            value=payload,
            on_delivery=_delivery_report,
        )
        _producer.flush(timeout=5)
        logger.info(
            "anomaly_published",
            payment_id=event.paymentId,
            risk_score=event.riskScore,
            topic=ANOMALY_TOPIC,
        )
    except Exception as exc:
        logger.error(
            "kafka_produce_error",
            payment_id=event.paymentId,
            topic=ANOMALY_TOPIC,
            error=str(exc),
        )

import os
import time
from typing import Optional

import redis
import structlog

from app.metrics import redis_errors_total

logger = structlog.get_logger(__name__)

REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD") or None

redis_client = redis.Redis(
    host=REDIS_HOST,
    port=REDIS_PORT,
    password=REDIS_PASSWORD,
    decode_responses=True,
    socket_connect_timeout=2,
    socket_timeout=2,
)

# ---------------------------------------------------------------------------
# Velocity (Rule 1)
# ---------------------------------------------------------------------------

VELOCITY_WINDOW_SECONDS = 60
VELOCITY_THRESHOLD = 5


def increment_velocity(account_id: str, now_ts: float) -> int:
    """
    Add ``now_ts`` to the velocity sorted set and return the number of
    payments seen in the last 60 seconds (including this one).

    Returns 0 on Redis error (fail-open).
    """
    key = f"velocity:{account_id}"
    try:
        pipe = redis_client.pipeline()
        # Remove entries older than the window
        pipe.zremrangebyscore(key, "-inf", now_ts - VELOCITY_WINDOW_SECONDS)
        # Add current payment timestamp as both score and member
        pipe.zadd(key, {str(now_ts): now_ts})
        # Count remaining entries in the window
        pipe.zcard(key)
        # Ensure the key expires so Redis memory doesn't grow unboundedly
        pipe.expire(key, VELOCITY_WINDOW_SECONDS * 2)
        results = pipe.execute()
        count: int = results[2]
        return count
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="increment_velocity", account_id=account_id, error=str(exc))
        return 0


# ---------------------------------------------------------------------------
# Daily total (Rule 2)
# ---------------------------------------------------------------------------

DAILY_TTL_SECONDS = 86400


def get_daily_total(account_id: str) -> float:
    """Return the accumulated daily total for *account_id*, or 0.0 on error."""
    key = f"daily_total:{account_id}"
    try:
        value = redis_client.get(key)
        return float(value) if value is not None else 0.0
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="get_daily_total", account_id=account_id, error=str(exc))
        return 0.0


def increment_daily_total(account_id: str, amount: float) -> float:
    """
    Atomically add *amount* to the daily total and (re)set the TTL.
    Returns the new total, or 0.0 on error.
    """
    key = f"daily_total:{account_id}"
    try:
        pipe = redis_client.pipeline()
        pipe.incrbyfloat(key, amount)
        pipe.expire(key, DAILY_TTL_SECONDS)
        results = pipe.execute()
        return float(results[0])
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="increment_daily_total", account_id=account_id, error=str(exc))
        return 0.0


# ---------------------------------------------------------------------------
# Blocklist (Rule 4)
# ---------------------------------------------------------------------------


def is_blocklisted(account_id: str) -> bool:
    """Return True if the account is on the Redis blocklist, False on error (fail-open)."""
    key = f"blocklist:{account_id}"
    try:
        return redis_client.exists(key) > 0
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="is_blocklisted", account_id=account_id, error=str(exc))
        return False


# ---------------------------------------------------------------------------
# Geographic anomaly helpers (Rule 3)
# ---------------------------------------------------------------------------

GEO_TTL_SECONDS = 86400
GEO_TRAVEL_WINDOW_SECONDS = 3600


def get_last_country(account_id: str) -> Optional[str]:
    key = f"last_country:{account_id}"
    try:
        return redis_client.get(key)
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="get_last_country", account_id=account_id, error=str(exc))
        return None


def set_last_country(account_id: str, country: str) -> None:
    key = f"last_country:{account_id}"
    try:
        redis_client.set(key, country, ex=GEO_TTL_SECONDS)
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="set_last_country", account_id=account_id, error=str(exc))


def get_last_payment_time(account_id: str) -> Optional[float]:
    key = f"last_payment_time:{account_id}"
    try:
        value = redis_client.get(key)
        return float(value) if value is not None else None
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="get_last_payment_time", account_id=account_id, error=str(exc))
        return None


def set_last_payment_time(account_id: str, ts: float) -> None:
    key = f"last_payment_time:{account_id}"
    try:
        redis_client.set(key, ts, ex=GEO_TTL_SECONDS)
    except redis.RedisError as exc:
        redis_errors_total.inc()
        logger.error("redis_error", operation="set_last_payment_time", account_id=account_id, error=str(exc))

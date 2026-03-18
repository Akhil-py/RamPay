"""
Rule-based fraud engine.

Rules are evaluated in this order:
  1. Blocklist      (short-circuits — blocklisted accounts skip all other rules)
  2. Velocity       (>5 payments / 60 s from the same account)
  3. Amount         (single-transaction and rolling 24 h daily threshold)
  4. Geographic     (country change within 1 h)

Any HIGH_RISK result sets ``flagged=True``; MEDIUM_RISK alone does not.
"""

import time
from decimal import Decimal

import structlog

from app.models import FraudDecision, PaymentCreatedEvent
from app import redis_client as rc

logger = structlog.get_logger(__name__)

# Risk level constants
NONE = "NONE"
MEDIUM = "MEDIUM"
HIGH = "HIGH"

RISK_SCORES = {
    NONE: 0.0,
    MEDIUM: 0.5,
    HIGH: 0.9,
}

# Threshold constants
VELOCITY_THRESHOLD = 5          # payments per window
LARGE_AMOUNT = Decimal("10000.00")
VERY_LARGE_AMOUNT = Decimal("50000.00")
DAILY_LIMIT = Decimal("100000.00")


def _rule_blocklist(account_id: str, reasons: list[str]) -> str:
    """Rule 4 — Redis blocklist check. Returns risk level."""
    if rc.is_blocklisted(account_id):
        reasons.append("blocklisted")
        logger.info("fraud_rule_triggered", rule="blocklist", account_id=account_id)
        return HIGH
    return NONE


def _rule_velocity(account_id: str, now_ts: float, reasons: list[str]) -> str:
    """Rule 1 — Velocity check (>5 payments in 60 s)."""
    count = rc.increment_velocity(account_id, now_ts)
    if count > VELOCITY_THRESHOLD:
        reasons.append("velocity_exceeded")
        logger.info(
            "fraud_rule_triggered",
            rule="velocity",
            account_id=account_id,
            count=count,
        )
        return HIGH
    return NONE


def _rule_amount(account_id: str, amount: Decimal, reasons: list[str]) -> str:
    """Rule 2 — Amount threshold (single transaction + rolling daily total)."""
    risk = NONE

    # Single-transaction thresholds
    if amount > VERY_LARGE_AMOUNT:
        reasons.append("very_large_amount")
        logger.info("fraud_rule_triggered", rule="very_large_amount", account_id=account_id, amount=str(amount))
        risk = HIGH
    elif amount > LARGE_AMOUNT:
        reasons.append("large_amount")
        logger.info("fraud_rule_triggered", rule="large_amount", account_id=account_id, amount=str(amount))
        risk = MEDIUM

    # Rolling daily total
    new_total = rc.increment_daily_total(account_id, float(amount))
    if Decimal(str(new_total)) > DAILY_LIMIT:
        reasons.append("daily_limit_exceeded")
        logger.info(
            "fraud_rule_triggered",
            rule="daily_limit",
            account_id=account_id,
            daily_total=new_total,
        )
        risk = HIGH

    return risk


def _rule_geo(account_id: str, now_ts: float, country_code: str | None, reasons: list[str]) -> str:
    """Rule 3 — Geographic anomaly (country change within 1 h)."""
    if not country_code:
        # No country info on this event — update last payment time, skip check
        rc.set_last_payment_time(account_id, now_ts)
        return NONE

    last_country = rc.get_last_country(account_id)
    last_payment_time = rc.get_last_payment_time(account_id)

    risk = NONE
    if (
        last_country is not None
        and last_country != country_code
        and last_payment_time is not None
        and (now_ts - last_payment_time) < rc.GEO_TRAVEL_WINDOW_SECONDS
    ):
        reasons.append("geo_anomaly")
        logger.info(
            "fraud_rule_triggered",
            rule="geo_anomaly",
            account_id=account_id,
            last_country=last_country,
            new_country=country_code,
            elapsed_seconds=now_ts - last_payment_time,
        )
        risk = HIGH

    # Always update geo state after evaluation
    rc.set_last_country(account_id, country_code)
    rc.set_last_payment_time(account_id, now_ts)

    return risk


def _merge_risk(current: str, new: str) -> str:
    """Return the higher of two risk levels."""
    order = {NONE: 0, MEDIUM: 1, HIGH: 2}
    return current if order[current] >= order[new] else new


def evaluate(event: PaymentCreatedEvent) -> FraudDecision:
    """
    Evaluate a payment event against all fraud rules.

    Completes in < 100 ms (Redis is the only I/O).
    Fails open: if Redis is unavailable, returns NONE risk.
    """
    start = time.perf_counter()
    account_id = event.fromAccountId
    now_ts = time.time()
    reasons: list[str] = []

    # --- Rule 4: Blocklist (short-circuit) ---
    risk = _rule_blocklist(account_id, reasons)
    if risk == HIGH:
        elapsed_ms = (time.perf_counter() - start) * 1000
        return FraudDecision(
            payment_id=event.paymentId,
            flagged=True,
            risk_level=HIGH,
            risk_score=RISK_SCORES[HIGH],
            reasons=reasons,
            evaluation_ms=round(elapsed_ms, 3),
        )

    # --- Rule 1: Velocity ---
    risk = _merge_risk(risk, _rule_velocity(account_id, now_ts, reasons))

    # --- Rule 2: Amount ---
    risk = _merge_risk(risk, _rule_amount(account_id, event.amount, reasons))

    # --- Rule 3: Geographic anomaly ---
    country_code = event.metadata.country_code if event.metadata else None
    risk = _merge_risk(risk, _rule_geo(account_id, now_ts, country_code, reasons))

    elapsed_ms = (time.perf_counter() - start) * 1000

    flagged = risk == HIGH

    logger.info(
        "fraud_evaluation_complete",
        payment_id=event.paymentId,
        account_id=account_id,
        risk_level=risk,
        flagged=flagged,
        reasons=reasons,
        evaluation_ms=round(elapsed_ms, 3),
    )

    return FraudDecision(
        payment_id=event.paymentId,
        flagged=flagged,
        risk_level=risk,
        risk_score=RISK_SCORES[risk],
        reasons=reasons,
        evaluation_ms=round(elapsed_ms, 3),
    )

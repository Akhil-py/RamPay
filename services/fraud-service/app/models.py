from pydantic import BaseModel
from typing import Optional
from decimal import Decimal
import datetime


class PaymentMetadata(BaseModel):
    country_code: Optional[str] = None


class PaymentCreatedEvent(BaseModel):
    eventType: str
    paymentId: str
    fromAccountId: str
    toAccountId: str
    amount: Decimal
    currency: str
    timestamp: str
    metadata: Optional[PaymentMetadata] = None


class AnomalyDetectedEvent(BaseModel):
    eventType: str = "AnomalyDetected"
    paymentId: str
    riskScore: float
    message: str
    timestamp: str


class FraudDecision(BaseModel):
    payment_id: str
    flagged: bool
    risk_level: str  # "NONE", "MEDIUM", "HIGH"
    risk_score: float
    reasons: list[str]
    evaluation_ms: float

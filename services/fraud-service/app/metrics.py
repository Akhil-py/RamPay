from prometheus_client import Counter, Histogram

payments_evaluated_total = Counter(
    "payments_evaluated_total",
    "Total number of payments evaluated for fraud",
    ["risk_level"],
)

fraud_evaluation_duration_seconds = Histogram(
    "fraud_evaluation_duration_seconds",
    "Time spent evaluating a single payment for fraud",
    buckets=[0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.15, 0.2],
)

anomalies_detected_total = Counter(
    "anomalies_detected_total",
    "Total number of anomalies detected, broken down by reason",
    ["reason"],
)

redis_errors_total = Counter(
    "redis_errors_total",
    "Total number of Redis errors encountered during fraud evaluation",
)

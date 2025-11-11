# 📡 RamPay Event Catalogue

This document defines the standard event types exchanged between microservices.

---

## 1. PaymentCreated
**Published by:** Payment Service  
**Consumed by:** Fraud Detection, Notification, Analytics  
**Payload:**
{
  "eventType": "PaymentCreated",
  "paymentId": "UUID",
  "fromAccountId": "UUID",
  "toAccountId": "UUID",
  "amount": 120.50,
  "currency": "USD",
  "timestamp": "ISO-8601"
}

---

## 2. PaymentApproved
**Published by:** Payment Service  
**Consumed by:** Notification, Analytics  
**Payload:**
{
  "eventType": "PaymentApproved",
  "paymentId": "UUID",
  "status": "APPROVED",
  "timestamp": "ISO-8601"
}

---

## 3. PaymentFailed
**Published by:** Payment Service  
**Consumed by:** Notification, Analytics  
**Payload:**
{
  "eventType": "PaymentFailed",
  "paymentId": "UUID",
  "reason": "INSUFFICIENT_FUNDS",
  "timestamp": "ISO-8601"
}

---

## 4. AnomalyDetected
**Published by:** Fraud Detection Service  
**Consumed by:** Notification, Analytics  
**Payload:**
{
  "eventType": "AnomalyDetected",
  "paymentId": "UUID",
  "riskScore": 0.87,
  "message": "High risk transaction flagged",
  "timestamp": "ISO-8601"
}

---

## 5. UserRegistered
**Published by:** Auth Service (future)  
**Consumed by:** Notification, Analytics  
**Payload:**
{
  "eventType": "UserRegistered",
  "userId": "UUID",
  "email": "string",
  "timestamp": "ISO-8601"
}

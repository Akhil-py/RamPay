# Payment Service API Reference

## Overview

This document provides detailed information about all REST API endpoints exposed by the Payment Service. Each endpoint includes the HTTP method, path, request/response structures, status codes, and examples.

## Base URL

```
http://localhost:8080
```

## Authentication

Currently, the Payment Service does not require authentication. This may change in future releases.

---

## Endpoints

### 1. Create Payment

Creates a new payment with the specified details.

#### Description

Creates a new payment in PENDING status. The service validates the request, checks for idempotency if an idempotency key is provided, saves the payment to the database, caches it in Redis, and publishes a `PaymentCreated` event.

#### HTTP Method

```
POST
```

#### Path

```
/payments
```

#### Request Headers

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `Content-Type` | string | Yes | Must be `application/json` |
| `Idempotency-Key` | string | No | Unique key for idempotency |

#### Request Body

```json
{
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD"
}
```

| Field | Type | Required | Description | Validation |
|-------|------|----------|-------------|-----------|
| `fromAccountId` | UUID | Yes | Source account ID | Must be a valid UUID |
| `toAccountId` | UUID | Yes | Destination account ID | Must be a valid UUID and different from `fromAccountId` |
| `amount` | number | Yes | Payment amount | Must be between 0.01 and 1,000,000.00 |
| `currency` | string | Yes | Currency code | Must be a valid ISO 4217 code (USD, EUR, GBP, JPY, CAD, AUD, CHF, CNY) |

#### Response Body

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `201` | Payment created successfully |
| `400` | Bad request - validation failed |
| `409` | Conflict - duplicate payment (idempotency key already used) |
| `500` | Internal server error |

#### Example Request

```bash
curl -X POST http://localhost:8080/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: unique-key-12345" \
  -d '{
    "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
    "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
    "amount": 100.50,
    "currency": "USD"
  }'
```

#### Example Response

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

#### Error Responses

**Validation Error (400)**

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Validation Error",
  "message": "Request validation failed",
  "path": "/payments",
  "details": [
    "amount: amount must be greater than 0",
    "currency: currency must be a valid ISO 4217 code"
  ]
}
```

**Duplicate Payment (409)**

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Payment already processed with this idempotency key",
  "path": "/payments",
  "details": []
}
```

---

### 2. Get All Payments

Retrieves a paginated list of all payments.

#### Description

Returns a paginated list of all payments in the system. The default page size is 20, with a maximum of 100 items per page.

#### HTTP Method

```
GET
```

#### Path

```
/payments
```

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | integer | No | 0 | Page number (0-indexed) |
| `size` | integer | No | 20 | Page size (max 100) |

#### Response Body

```json
{
  "content": [
    {
      "id": "323e4567-e89b-12d3-a456-426614174002",
      "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
      "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
      "amount": 100.50,
      "currency": "USD",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "failureReason": null,
      "refundAmount": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 5,
    "totalElements": 100,
    "first": true,
    "last": false
  }
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payments retrieved |
| `500` | Internal server error |

#### Example Request

```bash
curl -X GET "http://localhost:8080/payments?page=0&size=20"
```

#### Example Response

```json
{
  "content": [
    {
      "id": "323e4567-e89b-12d3-a456-426614174002",
      "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
      "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
      "amount": 100.50,
      "currency": "USD",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "failureReason": null,
      "refundAmount": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 5,
    "totalElements": 100,
    "first": true,
    "last": false
  }
}
```

---

### 3. Get Payment by ID

Retrieves a specific payment by its ID.

#### Description

Returns a single payment identified by its UUID. The service first checks the Redis cache before querying the database.

#### HTTP Method

```
GET
```

#### Path

```
/payments/{id}
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | Yes | Payment ID |

#### Response Body

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payment retrieved |
| `404` | Payment not found |
| `500` | Internal server error |

#### Example Request

```bash
curl -X GET http://localhost:8080/payments/323e4567-e89b-12d3-a456-426614174002
```

#### Example Response

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

#### Error Responses

**Payment Not Found (404)**

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Payment not found with id: 323e4567-e89b-12d3-a456-426614174002",
  "path": "/payments/323e4567-e89b-12d3-a456-426614174002",
  "details": []
}
```

---

### 4. Approve Payment

Approves a pending payment.

#### Description

Approves a payment that is currently in PENDING status. The service validates the payment status, updates the payment to APPROVED, caches it in Redis, and publishes a `PaymentApproved` event.

#### HTTP Method

```
PUT
```

#### Path

```
/payments/{id}/approve
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | Yes | Payment ID |

#### Response Body

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "APPROVED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payment approved |
| `400` | Bad request - invalid payment status |
| `404` | Payment not found |
| `500` | Internal server error |

#### Example Request

```bash
curl -X PUT http://localhost:8080/payments/323e4567-e89b-12d3-a456-426614174002/approve
```

#### Example Response

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "APPROVED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

#### Error Responses

**Invalid Payment Status (400)**

```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Cannot approve payment with status: APPROVED",
  "path": "/payments/323e4567-e89b-12d3-a456-426614174002/approve",
  "details": []
}
```

---

### 5. Fail Payment

Fails a pending payment with a specified reason.

#### Description

Fails a payment that is currently in PENDING status. The service validates the payment status, updates the payment to FAILED with the provided reason, caches it in Redis, and publishes a `PaymentFailed` event.

#### HTTP Method

```
PUT
```

#### Path

```
/payments/{id}/fail
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | Yes | Payment ID |

#### Request Body

```json
{
  "reason": "Insufficient funds"
}
```

| Field | Type | Required | Description | Validation |
|-------|------|----------|-------------|-----------|
| `reason` | string | Yes | Reason for failure | Must not be blank, max 255 characters |

#### Response Body

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "FAILED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z",
  "failureReason": "Insufficient funds",
  "refundAmount": null
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payment failed |
| `400` | Bad request - validation failed or invalid payment status |
| `404` | Payment not found |
| `500` | Internal server error |

#### Example Request

```bash
curl -X PUT http://localhost:8080/payments/323e4567-e89b-12d3-a456-426614174002/fail \
  -H "Content-Type: application/json" \
  -d '{
    "reason": "Insufficient funds"
  }'
```

#### Example Response

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "FAILED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:35:00Z",
  "failureReason": "Insufficient funds",
  "refundAmount": null
}
```

#### Error Responses

**Validation Error (400)**

```json
{
  "timestamp": "2024-01-15T10:35:00Z",
  "status": 400,
  "error": "Validation Error",
  "message": "Request validation failed",
  "path": "/payments/323e4567-e89b-12d3-a456-426614174002/fail",
  "details": [
    "reason: reason is required"
  ]
}
```

---

### 6. Refund Payment

Refunds an approved payment.

#### Description

Refunds a payment that is currently in APPROVED status. The service validates the payment status, ensures the refund amount does not exceed the original amount, updates the payment to REFUNDED, caches it in Redis, and publishes a `PaymentRefunded` event.

#### HTTP Method

```
POST
```

#### Path

```
/payments/{id}/refund
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `id` | UUID | Yes | Payment ID |

#### Request Body

```json
{
  "refundAmount": 100.50
}
```

| Field | Type | Required | Description | Validation |
|-------|------|----------|-------------|-----------|
| `refundAmount` | number | Yes | Amount to refund | Must be greater than 0 and not exceed original payment amount |

#### Response Body

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "REFUNDED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:40:00Z",
  "failureReason": null,
  "refundAmount": 100.50
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payment refunded |
| `400` | Bad request - validation failed or invalid payment status |
| `404` | Payment not found |
| `500` | Internal server error |

#### Example Request

```bash
curl -X POST http://localhost:8080/payments/323e4567-e89b-12d3-a456-426614174002/refund \
  -H "Content-Type: application/json" \
  -d '{
    "refundAmount": 100.50
  }'
```

#### Example Response

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "REFUNDED",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:40:00Z",
  "failureReason": null,
  "refundAmount": 100.50
}
```

#### Error Responses

**Invalid Payment Amount (400)**

```json
{
  "timestamp": "2024-01-15T10:40:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Refund amount cannot exceed original amount",
  "path": "/payments/323e4567-e89b-12d3-a456-426614174002/refund",
  "details": []
}
```

---

### 7. Get Payments by Account

Retrieves a paginated list of payments for a specific account.

#### Description

Returns a paginated list of all payments where the specified account is the sender (fromAccountId).

#### HTTP Method

```
GET
```

#### Path

```
/payments/account/{accountId}
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `accountId` | UUID | Yes | Account ID |

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | integer | No | 0 | Page number (0-indexed) |
| `size` | integer | No | 20 | Page size (max 100) |

#### Response Body

```json
{
  "content": [
    {
      "id": "323e4567-e89b-12d3-a456-426614174002",
      "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
      "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
      "amount": 100.50,
      "currency": "USD",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "failureReason": null,
      "refundAmount": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 1,
    "totalElements": 5,
    "first": true,
    "last": true
  }
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payments retrieved |
| `500` | Internal server error |

#### Example Request

```bash
curl -X GET "http://localhost:8080/payments/account/123e4567-e89b-12d3-a456-426614174000?page=0&size=20"
```

#### Example Response

```json
{
  "content": [
    {
      "id": "323e4567-e89b-12d3-a456-426614174002",
      "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
      "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
      "amount": 100.50,
      "currency": "USD",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "failureReason": null,
      "refundAmount": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 1,
    "totalElements": 5,
    "first": true,
    "last": true
  }
}
```

---

### 8. Get Payments by Status

Retrieves a paginated list of payments with a specific status.

#### Description

Returns a paginated list of all payments with the specified status.

#### HTTP Method

```
GET
```

#### Path

```
/payments/status/{status}
```

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `status` | string | Yes | Payment status (PENDING, APPROVED, FAILED, REFUNDED) |

#### Request Parameters

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `page` | integer | No | 0 | Page number (0-indexed) |
| `size` | integer | No | 20 | Page size (max 100) |

#### Response Body

```json
{
  "content": [
    {
      "id": "323e4567-e89b-12d3-a456-426614174002",
      "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
      "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
      "amount": 100.50,
      "currency": "USD",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "failureReason": null,
      "refundAmount": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 2,
    "totalElements": 25,
    "first": true,
    "last": false
  }
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Success - payments retrieved |
| `400` | Bad request - invalid status |
| `500` | Internal server error |

#### Example Request

```bash
curl -X GET "http://localhost:8080/payments/status/PENDING?page=0&size=20"
```

#### Example Response

```json
{
  "content": [
    {
      "id": "323e4567-e89b-12d3-a456-426614174002",
      "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
      "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
      "amount": 100.50,
      "currency": "USD",
      "status": "PENDING",
      "createdAt": "2024-01-15T10:30:00Z",
      "updatedAt": "2024-01-15T10:30:00Z",
      "failureReason": null,
      "refundAmount": null
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 2,
    "totalElements": 25,
    "first": true,
    "last": false
  }
}
```

---

### 9. Health Check

Performs a health check on the service and its dependencies.

#### Description

Returns the health status of the Payment Service and its dependencies (PostgreSQL, Redis, Kafka). This endpoint is useful for monitoring and load balancer health checks.

#### HTTP Method

```
GET
```

#### Path

```
/health
```

#### Response Body

```json
{
  "status": "UP",
  "database": {
    "status": "UP"
  },
  "redis": {
    "status": "UP"
  },
  "kafka": {
    "status": "UP"
  }
}
```

#### Status Codes

| Code | Description |
|------|-------------|
| `200` | Health check completed |

#### Example Request

```bash
curl -X GET http://localhost:8080/health
```

#### Example Response

```json
{
  "status": "UP",
  "database": {
    "status": "UP"
  },
  "redis": {
    "status": "UP"
  },
  "kafka": {
    "status": "UP"
  }
}
```

#### Example Response (Degraded)

```json
{
  "status": "UP",
  "database": {
    "status": "UP"
  },
  "redis": {
    "status": "DOWN",
    "error": "Connection refused"
  },
  "kafka": {
    "status": "UP"
  }
}
```

---

## Common Response Structures

### PaymentResponse

```json
{
  "id": "323e4567-e89b-12d3-a456-426614174002",
  "fromAccountId": "123e4567-e89b-12d3-a456-426614174000",
  "toAccountId": "223e4567-e89b-12d3-a456-426614174001",
  "amount": 100.50,
  "currency": "USD",
  "status": "PENDING",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:00Z",
  "failureReason": null,
  "refundAmount": null
}
```

### ErrorResponse

```json
{
  "timestamp": "2024-01-15T10:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/payments",
  "details": [
    "amount: amount must be greater than 0"
  ]
}
```

### PageableResponse

```json
{
  "content": [...],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "totalPages": 5,
    "totalElements": 100,
    "first": true,
    "last": false
  }
}
```

---

## HTTP Status Codes

| Code | Description | Common Causes |
|------|-------------|---------------|
| `200` | OK | Successful GET, PUT, POST operations |
| `201` | Created | Payment created successfully |
| `400` | Bad Request | Validation errors, invalid payment status |
| `404` | Not Found | Payment not found |
| `409` | Conflict | Duplicate payment (idempotency) |
| `500` | Internal Server Error | Unexpected server error |

---

## Related Documentation

- [Payment Service Documentation](./payment-service.md) - Main service documentation
- [Payment Service Events Reference](./payment-service-events.md) - Event schema and usage
- [Payment Service Design](./payment-service-design.md) - Design decisions and architecture

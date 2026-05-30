# Payment Orchestration Service

A simplified payment orchestration system built for Yuno's Backend Engineering Assignment.

The service provides payment creation and retrieval APIs while demonstrating key payment-processing concepts such as:

- Payment routing
- Provider integration
- Retry mechanisms
- Idempotency
- Payment status tracking
- Observability
- Error handling

---

# Technology Stack

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA
- H2 In-Memory Database
- Spring Validation
- Spring AOP
- Spring Actuator
- Maven
- JUnit 5
- Mockito
- JaCoCo

---

# Features

### Payment Creation

Creates a payment using the supplied amount, currency, and payment method.

### Payment Retrieval

Fetches payment details using a unique payment ID.

### Payment Routing

Routing is based on the payment method:

| Payment Method | Provider |
|---------------|----------|
| CARD | Provider A |
| UPI | Provider B |

### Retry Mechanism

Provider failures are retried automatically.

- Configurable retry attempts
- Configurable retry delay
- Fixed-delay retry strategy

### Idempotency

Ensures duplicate requests are not processed multiple times.

- Uses `Idempotency-Key` header
- SHA-256 request hashing
- Stores request hash and cached response
- Same key + same payload → returns cached response
- Same key + different payload → HTTP 400

### Payment Status Tracking

Payment lifecycle:

```text
PENDING
   ↓
PROCESSING
   ↓
SUCCESS / FAILED
```

### Observability

- Correlation ID support
- Structured logging
- Request tracing
- Execution-time monitoring

---

# Architecture

```text
Client
  |
  v
PaymentController
  |
  v
PaymentService
  |
  +--> IdempotencyService
  |
  +--> PaymentRoutingEngine
           |
           +--> ProviderAConnector
           |
           +--> ProviderBConnector
  |
  v
PaymentRepository
```

---

# Key Design Decisions

### Why synchronous processing?

For the assignment, synchronous processing keeps the flow simple and easy to reason about while still demonstrating routing, retries, status tracking, and idempotency.

In production, asynchronous processing using Kafka or queues would improve scalability and resilience.

### Why H2?

H2 provides a lightweight embedded database that makes the application self-contained and easy to run locally.

### Why store idempotency responses?

Returning the original response guarantees consistent behavior for duplicate requests and mirrors how payment providers such as Stripe implement idempotency.

---

# Design Patterns Used

### Strategy Pattern

Used for provider integrations.

```text
PaymentProviderConnector
        |
        +--> ProviderAConnector
        |
        +--> ProviderBConnector
```

Allows new providers to be added without changing business logic.

### Registry / Factory Lookup

Used in `PaymentRoutingEngine`.

Provider connectors are discovered automatically and stored in a lookup map for O(1) resolution.

### Repository Pattern

Used through Spring Data JPA repositories.

### Dependency Injection

Managed by Spring Framework.

---

# Project Structure

```text
src/main/java
├── controller
├── service
│   ├── impl
├── provider
├── routing
├── repository
├── entity
├── dto
│   ├── request
│   └── response
├── idempotency
├── exception
├── aspect
├── filter
└── config
```

---

# Getting Started

## Prerequisites

- Java 21+
- Maven 3.8+

---

## Clone Repository

```bash
git clone https://github.com/sagarbhadouria/payment-service.git
cd payment-service
```

---

## Run Application

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Application starts at:

```text
http://localhost:8080/api/payment-service
```

---

# API Documentation

## Create Payment

### Request

```http
POST /api/payment-service/v1/payments
```

### Headers

```http
Idempotency-Key: payment-123
Content-Type: application/json
```

### Request Body

```json
{
  "amount": 99.99,
  "currency": "USD",
  "paymentMethod": "CARD"
}
```

### Success Response

```http
HTTP 201 Created
```

```json
{
  "paymentId": "a4db2df1-b7b6-4d55-8f2a-4fdcfdb7f33",
  "status": "SUCCESS",
  "createdAt": "2026-05-30T12:00:00"
}
```

### Failed Response

```http
HTTP 201 Created

note: “Payment resource is always created, status indicates final outcome.”
```

```json
{
  "paymentId": "a4db2df1-b7b6-4d55-8f2a-4fdcfdb7f33",
  "status": "FAILED",
  "createdAt": "2026-05-30T12:00:00"
}
```

---

## Fetch Payment

### Request

```http
GET /api/payment-service/v1/payments/{paymentId}
```

### Success Response

```http
HTTP 200 OK
```

```json
{
  "paymentId": "a4db2df1-b7b6-4d55-8f2a-4fdcfdb7f33",
  "amount": 99.99,
  "currency": "USD",
  "paymentMethod": "CARD",
  "provider": "PROVIDER_A",
  "providerTransactionId": "PROVA-12345",
  "status": "SUCCESS",
  "createdAt": "2026-05-30T12:00:00",
  "updatedAt": "2026-05-30T12:00:02"
}
```

---

# Error Handling

## Validation Error

```http
HTTP 400 Bad Request
```

```json
{
  "timestamp": "2026-05-30T12:00:00",
  "message": "Validation Failed: amount must be greater than zero",
  "details": "uri=/api/payment-service/v1/payments"
}
```

---

## Idempotency Conflict

```http
HTTP 400 Bad Request
```

```json
{
  "timestamp": "2026-05-30T12:00:00",
  "message": "Different payload for same key",
  "details": "uri=/api/payment-service/v1/payments"
}
```

---

## Payment Not Found

```http
HTTP 404 Not Found
```

```json
{
  "timestamp": "2026-05-30T12:00:00",
  "message": "Payment not found with ID: xxx",
  "details": "uri=/api/payment-service/v1/payments/xxx"
}
```

---

## Provider Failure

```http
HTTP 502 Bad Gateway
```

```json
{
  "timestamp": "2026-05-30T12:00:00",
  "message": "Provider PROVIDER_A error: Payment processing failed",
  "details": "uri=/api/payment-service/v1/payments"
}
```

---

# Configuration

Profiles available:

```text
dev
test
```

Retry configuration:

```yaml
payment:
  retry:
    max-attempts: 3
    delay-ms: 1000
```

---

# Running Tests

Execute unit tests:

```bash
./mvnw test
```

---

# Generate Coverage Report

```bash
./mvnw clean verify
```

Coverage report location:

```text
target/site/jacoco/index.html
```

---

# Internal Processing Flow

```text
1. Validate Request
2. Check Idempotency Store
3. Create Payment (PENDING)
4. Resolve Provider
5. Update Status → PROCESSING
6. Execute Provider Call
7. Retry on Failure
8. Update Status → SUCCESS / FAILED
9. Save Idempotency Record
10. Return Response
```

---

# Database Tables

## payments

Stores payment transactions.

Key fields:

```text
payment_id
amount
currency
payment_method
provider
provider_transaction_id
status
created_at
updated_at
```

---

## idempotency_records

Stores idempotency information.

Key fields:

```text
idempotency_key
request_hash
payment_id
response_body
created_at
```

---

# Non-Functional Requirements

### Idempotency

Guarantees that duplicate requests with the same idempotency key and payload are processed only once and return a consistent response.

### Reliability

Configurable retry mechanism for transient provider failures.

### Observability

- Correlation IDs
- Structured logs
- Execution time metrics

### Maintainability

- Layered architecture
- SOLID principles
- Separation of concerns

### Extensibility

Adding a new provider requires:

1. Create a new connector implementation.
2. Register routing rule.

No service-layer modifications required.

---

# Simulated Provider Integrations

## Provider A

- Handles CARD payments
- Success rate: 80%
- Generates transaction IDs:

```text
PROVA-xxxxxxxx
```

---

## Provider B

- Handles UPI payments
- Success rate: 85%
- Generates transaction IDs:

```text
PROVB-xxxxxxxx
```

---

# Future Improvements

- Replace H2 with PostgreSQL
- Redis-backed idempotency storage
- Circuit Breaker using Resilience4j
- Provider health monitoring
- Dynamic routing based on provider health
- Routing rules are currently hardcoded but can be externalised for production
- Asynchronous payment processing
- Event-driven architecture using Kafka
- Distributed tracing using OpenTelemetry
- Outbox Pattern for reliable event publishing
- Rate limiting and API throttling

---

# Assumptions

- Payment processing is synchronous.
- Provider integrations are mocked.
- Retry strategy uses fixed delay.
- H2 database is used for local development only.
- Payment status is determined immediately after provider processing.

---
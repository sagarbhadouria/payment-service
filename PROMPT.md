# AI-Assisted Development Log

This document records how AI tools were used during the development of the Payment Orchestration Service.

The purpose of this document is to provide transparency into the development process and demonstrate how AI-assisted development was leveraged to accelerate implementation, refactoring, testing, debugging, and documentation.

---

# AI Usage Disclaimer

AI tools were used to assist with:

- Initial code generation
- Architecture brainstorming
- Refactoring suggestions
- Test generation
- Documentation generation
- Debugging support

All generated code was manually reviewed, validated, and adapted to fit the project's architecture and requirements.

Final design decisions, implementation choices, debugging, testing, and code reviews were performed by the author.

---

# Development Process

## Phase 1 – Project Setup & Core Domain Modeling

### Prompt

> Create a Spring Boot 3 project with Java 21, using Maven. Add dependencies: web, validation, data-jpa, h2, actuator, aop, and test. Generate a simple Payment entity with fields: paymentId (UUID), amount (BigDecimal), currency (String), paymentMethod (enum CARD/UPI), status (enum PENDING/PROCESSING/SUCCESS/FAILED), createdAt/updatedAt (LocalDateTime). Also create IdempotencyRecord entity with idempotencyKey (PK), requestHash, paymentId, responseBody, createdAt.

### Outcome

Generated the initial project structure and domain entities used throughout the application.

---

## Phase 2 – Provider Integration Layer

### Prompt

> Implement PaymentProviderConnector interface with processPayment(Payment) and getProviderName(). Create two implementations: ProviderAConnector (handles CARD, 80% success rate, simulates 100-300ms latency) and ProviderBConnector (handles UPI, 85% success rate). Throw PaymentProviderException on unexpected errors.

### Outcome

Generated provider-specific connector implementations using the Strategy Pattern.

---

## Phase 3 – Payment Routing

### Prompt

> Create a PaymentRoutingEngine that injects all PaymentProviderConnector beans and builds a Map<PaymentProvider, PaymentProviderConnector>. Define routing rules: CARD → PROVIDER_A, UPI → PROVIDER_B.

### Outcome

Implemented provider resolution using a registry-style lookup for O(1) connector selection.

---

## Phase 4 – Idempotency Support

### Prompt

> Implement IdempotencyService with SHA-256 hashing of request body using Jackson serialization. Store records in database using IdempotencyRecordRepository. Handle duplicate requests and provide response deserialization.

### Follow-Up Prompt

> Add request hash comparison logic in PaymentServiceImpl. If the same idempotency key is reused with a different payload, throw IdempotencyConflictException.

### Outcome

Implemented idempotency handling using:

- SHA-256 request hashing
- Database-backed storage
- Cached response retrieval
- Payload conflict detection

---

## Phase 5 – Payment Orchestration Service

### Prompt

> Implement PaymentService. In createPayment: generate request hash, validate idempotency, persist payment, resolve provider, update payment status, execute provider call with retry logic, update final status, save idempotency record, and return response.

### Retry Logic Prompt

> Create executeWithRetry() that retries provider processing using configurable attempts and delay settings.

### Outcome

Implemented the core orchestration workflow responsible for:

- Idempotency validation
- Payment lifecycle management
- Routing
- Provider execution
- Retry handling
- Response generation

---

## Phase 6 – REST API Layer

### Prompt

> Create PaymentController with POST /v1/payments and GET /v1/payments/{paymentId}. Validate request body and Idempotency-Key header.

### Outcome

Implemented REST APIs for payment creation and retrieval.

---

## Phase 7 – Global Exception Handling

### Prompt

> Build GlobalExceptionHandler that maps custom exceptions to appropriate HTTP status codes and response bodies.

### Outcome

Implemented centralized exception handling for:

- Validation failures
- Idempotency conflicts
- Missing resources
- Provider failures
- Malformed requests
- Unexpected system errors

---

## Phase 8 – Observability

### Prompt

> Create CorrelationIdFilter that propagates X-Correlation-Id through request processing and logging.

### Prompt

> Create LoggingAspect for request logging, exception logging, and execution-time measurement.

### Outcome

Implemented:

- Correlation ID propagation
- Structured logging
- Request tracing
- Execution-time monitoring

---

## Phase 9 – Testing

### Prompt

> Write unit tests for PaymentServiceImpl covering success paths, retry logic, provider failures, idempotency scenarios, and payment retrieval.

### Prompt

> Write controller integration tests using MockMvc covering validation, error handling, and success paths.

### Outcome

Generated test scaffolding which was subsequently reviewed, refined, and expanded.

---

## Phase 10 – Configuration & Build

### Prompt

> Create application-dev.yml, application-test.yml, application-prod.yml with datasource, logging, and retry configuration. Configure JaCoCo coverage reporting.

### Outcome

Implemented environment-specific configuration and code coverage reporting.

---

# Key Engineering Decisions

While AI assisted with implementation, several architectural and design decisions were made and refined manually during development.

### Separation of Provider and Domain Status

Provider responses are intentionally separated from internal payment status.

```text
ProviderResponse
      ↓
Service Layer
      ↓
PaymentStatus
```

This prevents external provider contracts from leaking into the domain model.

---

### Strategy Pattern for Provider Integrations

Provider implementations are hidden behind a common interface:

```text
PaymentProviderConnector
        |
        +--> ProviderAConnector
        |
        +--> ProviderBConnector
```

This allows new providers to be added without modifying orchestration logic.

---

### Routing Through Registry Lookup

The routing engine stores provider connectors in a lookup map:

```java
Map<PaymentProvider, PaymentProviderConnector>
```

Benefits:

- O(1) provider resolution
- Open/Closed Principle
- Simplified extensibility

---

### Provider Transaction Tracking

External provider transaction IDs are stored separately from internal payment IDs.

Benefits:

- Easier reconciliation
- Better auditability
- Separation of internal and external identifiers

---

### Request Hash Validation

Idempotency validation is based on:

```text
Idempotency-Key
+
Request Hash
```

This prevents accidental reuse of the same key with a different request payload.

---

### Configuration-Driven Retry Logic

Retry behavior is externalized through application properties.

Benefits:

- Environment-specific tuning
- No code changes required
- Improved operational flexibility

---

# Debugging & Refactoring Assistance

AI was also used during troubleshooting and refactoring activities, including:

- Entity timestamp initialization issues
- Retry implementation refinements
- Provider connector design improvements
- Controller endpoint redesign
- Exception handling improvements
- Test failures and Mockito configuration issues
- Idempotency workflow improvements
- Documentation refinement

---

# Summary of AI Usage

AI was used as an engineering productivity tool rather than a source of final code.

Typical workflow:

1. Generate an initial implementation.
2. Review architecture and design.
3. Refactor generated code.
4. Add validations and edge-case handling.
5. Write and improve tests.
6. Verify behavior through manual review and execution.

AI accelerated implementation by generating initial drafts, test scaffolding, and refactoring suggestions. All outputs were reviewed and refined before inclusion in the final solution.

The resulting implementation follows a layered architecture with clear separation of concerns, maintainability, testability, and extensibility.
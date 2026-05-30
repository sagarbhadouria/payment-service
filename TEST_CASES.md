# Test Cases for Payment Orchestration Service

This document lists all test scenarios (positive, negative, sanity, regression, integration) implemented in the project.

## Classification Legend

- **Sanity** – basic smoke tests (create/fetch work)
- **Regression** – features that must not break (idempotency, retry, routing)
- **Integration** – end-to-end flows across layers

---

## Unit Tests

### PaymentServiceImplTest

| Test Name | Scenario | Type |
|-----------|----------|------|
| shouldCreateCardPaymentSuccessfully | CARD payment success | Sanity |
| shouldCreateUpiPaymentSuccessfully | UPI payment success | Sanity |
| shouldReturnCachedResponseWhenIdempotencyKeyExistsWithSamePayload | Idempotency – cache hit | Regression |
| shouldThrowIdempotencyConflictWhenSameKeyDifferentPayload | Idempotency – different payload → conflict | Negative |
| shouldRetryOnFailureAndSucceedOnSecondAttempt | Retry – succeed after 1 failure | Regression |
| shouldFailAfterAllRetriesExhausted | Retry – all attempts fail | Negative |
| shouldRetryWhenProviderThrowsException | Retry – provider exception handled | Regression |
| shouldFetchExistingPaymentSuccessfully | Fetch payment by ID | Sanity |
| shouldThrowPaymentNotFoundWhenIdDoesNotExist | Fetch non‑existent ID → 404 | Negative |
| shouldPersistWithPendingStatusEvenIfProviderFailsLater | Ensure PENDING saved before provider call | Regression |

### PaymentRoutingEngineTest

| Test Name | Scenario | Type |
|-----------|----------|------|
| shouldResolveConnectorForCardToProviderA | CARD → Provider A | Sanity |
| shouldResolveConnectorForUpiToProviderB | UPI → Provider B | Sanity |
| shouldResolveProviderForCardAsProviderA | resolveProvider(CARD) → PROVIDER_A | Sanity |
| shouldResolveProviderForUpiAsProviderB | resolveProvider(UPI) → PROVIDER_B | Sanity |
| shouldThrowNullPointerExceptionForNullMethod | null payment method → NPE (guarded by @NotNull) | Negative |
| shouldThrowPaymentProviderExceptionWhenConnectorNotAvailable | Missing connector → exception | Negative |
| shouldInitializeProviderMapCorrectly | Constructor builds correct provider→connector map | Regression |

### IdempotencyServiceImplTest

| Test Name | Scenario | Type |
|-----------|----------|------|
| shouldGenerateConsistentHashForSameRequest | SHA‑256 hash deterministic | Regression |
| shouldGenerateDifferentHashForDifferentRequests | Different payloads → different hashes | Regression |
| shouldThrowIllegalArgumentExceptionWhenSerializationFails | Serialization error handled | Negative |
| shouldReturnEmptyOptionalWhenKeyNotFound | Idempotency key not found | Sanity |
| shouldReturnRecordWhenKeyExists | Idempotency key found | Sanity |
| shouldSaveRecordSuccessfully | Save idempotency record | Regression |
| shouldThrowIdempotencyConflictOnDataIntegrityViolation | Duplicate key → conflict | Negative |
| shouldThrowRuntimeExceptionWhenResponseSerializationFails | Response serialization error | Negative |
| shouldDeserializeResponseSuccessfully | Cached response deserialisation | Regression |
| shouldThrowRuntimeExceptionWhenDeserializationFails | Malformed cached response → error | Negative |

### ProviderAConnectorTest / ProviderBConnectorTest

| Test Name | Scenario | Type |
|-----------|----------|------|
| shouldReturnSuccessResponseWhenRandomNumberWithinSuccessRate | Random success path | Sanity |
| shouldReturnFailureResponseWhenRandomNumberExceedsSuccessRate | Random failure path | Sanity |
| shouldThrowPaymentProviderExceptionWhenGenericExceptionOccurs | Unexpected provider error → exception | Negative |
| shouldReturnProviderAName / shouldReturnProviderBName | getProviderName() returns correct enum | Sanity |

---

## Integration Tests

### PaymentControllerIntegrationTest

| Test Name | Scenario | Type |
|-----------|----------|------|
| shouldCreatePaymentSuccessfully | POST /v1/payments → 201 | Sanity |
| shouldReturnBadRequestWhenMissingIdempotencyHeader | Missing header → 400 | Negative |
| shouldReturnBadRequestWhenAmountIsMissing | Missing amount → 400 | Negative |
| shouldReturnBadRequestWhenAmountIsZeroOrNegative | Zero/negative amount → 400 | Negative |
| shouldReturnBadRequestWhenCurrencyIsInvalid | Invalid currency format → 400 | Negative |
| shouldReturnBadRequestWhenPaymentMethodIsMissing | Missing paymentMethod → 400 | Negative |
| shouldReturnBadGatewayWhenProviderFails | Generic runtime exception → 500 | Negative |
| shouldReturnBadGatewayWhenProviderFailsWithPaymentProviderException | PaymentProviderException → 502 | Negative |
| shouldReturnBadRequestForUnsupportedPaymentMethod | UnsupportedPaymentMethodException → 400 | Negative |
| shouldReturnBadRequestWhenIdempotencyKeyReusedWithDifferentPayload | IdempotencyConflictException → 400 | Negative |
| shouldFetchPaymentSuccessfully | GET /v1/payments/{id} → 200 | Sanity |
| shouldReturnNotFoundWhenPaymentDoesNotExist | Payment not found → 404 | Negative |
| shouldReturnBadRequestForMalformedJson | Malformed JSON → 400 | Negative |

### CorrelationIdFilterIntegrationTest

| Test Name | Scenario | Type |
|-----------|----------|------|
| shouldGenerateCorrelationIdWhenHeaderNotProvided | No X-Correlation-Id → generates new ID | Regression |
| shouldUseProvidedCorrelationIdFromHeader | Provided header → used in response and MDC | Regression |
| shouldGenerateNewCorrelationIdWhenHeaderIsEmpty | Empty header → generates new ID | Regression |
| shouldGenerateUniqueCorrelationIdsForDifferentRequests | Two requests → different IDs | Regression |

---

## Summary of Test Coverage

- **Positive scenarios**: All happy paths (create, fetch, routing, idempotency cache hit, retry success).
- **Negative scenarios**: Missing headers, invalid inputs, provider failures, duplicate idempotency key with different payload, payment not found, malformed JSON.
- **Edge cases**: Retry exhaustion, provider exceptions, serialization errors, duplicate connectors (handled gracefully).
- **Regression**: Idempotency, retry logic, routing rules, correlation ID filter.
- **Integration**: Full HTTP flow with mocked service layer, exception mappings, filter chaining.

All tests pass with 90%+ line coverage on core logic (excluding DTOs, entities, exceptions, constants, aspects).
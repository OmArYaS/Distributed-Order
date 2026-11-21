# Jira Backlog — order-payment-engine

## Epic E1: Order Management
### Story E1-S1: Create order API
**As a** client, **I want** to create an order **so that** I can purchase items.
**Acceptance Criteria:**
- POST /api/v1/orders returns 201 with order body.
- Requires Idempotency-Key header.
- Duplicate key returns original response, no duplicate order.
- Publishes OrderCreated event.

### Story E1-S2: Fetch order status
**AC:** GET /api/v1/orders/{id} returns current status and items; 404 if missing.

### Story E1-S3: Retry payment
**AC:** POST .../retry-payment republishes OrderCreated and sets status PAYMENT_PENDING.

### Story E1-S4: Refund order
**AC:** POST .../refund sets status REFUNDED and records audit.

## Epic E2: Payment Processing
### Story E2-S1: Reserve payment on OrderCreated
**AC:** Consumes event idempotently; debits account; publishes PaymentReserved or PaymentFailed.

### Story E2-S2: Insufficient funds handling
**AC:** Publishes PaymentFailed(retryable=false); order becomes PAYMENT_FAILED.

### Story E2-S3: Refund payment
**AC:** Credits account; marks payment REFUNDED.

## Epic E3: Messaging Reliability
### Story E3-S1: Retry with exponential backoff
**AC:** Failed consumer attempts 4 times with backoff then DLQ.

### Story E3-S2: DLQ monitoring
**AC:** DLQ messages increment messaging.dlq.count metric.

## Epic E4: Observability
### Story E4-S1: Correlation ID propagation
**AC:** correlationId in MDC and Kafka headers across services.

### Story E4-S2: Structured JSON logs
**AC:** Logs are JSON with service, correlationId, level.

### Story E4-S3: Custom metrics
**AC:** /actuator/prometheus exposes all custom metrics.

## Epic E5: Persistence
### Story E5-S1: Optimistic locking
**AC:** Concurrent updates throw ObjectOptimisticLockingFailureException.

## Bug Tickets
### BUG-101: Double charge on retried create order
**Severity:** Critical
**Description:** Missing idempotency check allowed duplicate orders on retry.
**Fix:** Add IdempotencyRecord lookup before insert.

### BUG-102: Consumer processes duplicate Kafka delivery twice
**Severity:** High
**Description:** No processed-event tracking.
**Fix:** Check ProcessedEvent table; skip if present.

### BUG-103: DLQ messages not counted in metrics
**Severity:** Medium
**Description:** DLT listener missing metric increment.
**Fix:** Increment messaging.dlq.count in DLT handler.

### BUG-104: correlationId missing in payment service logs
**Severity:** Medium
**Description:** MDC not set in consumer.
**Fix:** Set MDC from Kafka header.

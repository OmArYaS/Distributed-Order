# order-payment-engine

Production-style multi-module Spring Boot backend demonstrating mid-level Java backend engineering practices: REST APIs, event-driven messaging (Kafka), relational + NoSQL persistence, idempotency, retries/DLQ, observability.

## Modules

- `common` — shared domain events, DTOs, correlation/idempotency constants, metric names.
- `order-service` (port 8081) — order lifecycle, idempotency, publishes `OrderCreated`/`OrderCompleted`.
- `payment-service` (port 8082) — consumes `OrderCreated`, reserves payment, publishes `PaymentReserved`/`PaymentFailed`.

## Architecture

```
Client -> order-service (REST) -> PostgreSQL (orders)
              | Kafka: order.created
              v
        payment-service -> PostgreSQL (accounts, payments) -> Kafka: payment.reserved / payment.failed
              ^                                                            |
              |________________________ order-service consumes _____________|
MongoDB: append-only audit_events (both services)
Redis: optional distributed locks / idempotency cache (profile)
```

## Tech Stack

Java 21, Spring Boot 3.3, Spring Web, Spring Data JPA, Spring Kafka, Spring Actuator, Micrometer, PostgreSQL, MongoDB, Redis, Kafka, Testcontainers.

## Local Setup

```bash
docker compose up -d
mvn -q -pl order-service,payment-service -am spring-boot:run
```

Order service: http://localhost:8081, Payment service: http://localhost:8082.
Actuator/Prometheus: http://localhost:8081/actuator/prometheus

## API Examples

Create order:
```bash
curl -X POST http://localhost:8081/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: 11111111-2222-3333-4444-555555555555' \
  -H 'X-Correlation-Id: corr-1' \
  -d '{"customerId":"aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee","currency":"USD","items":[{"productId":"11111111-1111-1111-1111-111111111111","productName":"Widget","quantity":2,"unitPrice":5.00}]}'
```

Get order:
```bash
curl http://localhost:8081/api/v1/orders/{id}
```

Retry payment:
```bash
curl -X POST http://localhost:8081/api/v1/orders/{id}/retry-payment -H 'Idempotency-Key: key-2'
```

Refund:
```bash
curl -X POST http://localhost:8081/api/v1/orders/{id}/refund -H 'Idempotency-Key: key-3'
```

## ADRs

See `docs/adr/`.

## Tests

```bash
mvn test
```

## Observability

- Structured JSON logs (Logstash encoder) with `correlationId` in MDC.
- Micrometer metrics: `order.creation.count`, `order.creation.latency`, `payment.success.count`, `payment.failure.count`, `messaging.processing.duration`, `messaging.dlq.count`.
- Prometheus endpoint via Actuator.

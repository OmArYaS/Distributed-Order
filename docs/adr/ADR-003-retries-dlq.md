# ADR-003: Kafka retry topics and Dead Letter Queue

## Status
Accepted

## Context
Poison messages and transient failures must not block partitions or lose data.

## Decision
Use Spring Kafka `@RetryableTopic` with exponential backoff (1s, 2s, 4s... capped 30s, 4 attempts) and a `.dlq` suffix topic. Consumers are idempotent. DLQ messages increment `messaging.dlq.count` metric and are logged for triage.

## Consequences
- Transient errors self-heal.
- Poison messages isolated to DLQ for manual replay.
- Requires monitoring of DLQ depth.

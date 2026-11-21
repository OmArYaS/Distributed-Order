# ADR-002: Idempotency strategy

## Status
Accepted

## Context
Duplicate HTTP requests (retries, client timeouts) and duplicate Kafka deliveries must not double-charge.

## Decision
- HTTP: require `Idempotency-Key` header. Store request hash + cached response in `idempotency_records` (unique key). Replaying returns cached result; mismatched body -> 409.
- Messaging: track processed `eventId` in `processed_events` table; consumer skips already-processed events.
- State transitions are guarded (e.g., payment already exists for order).

## Consequences
- Safe retries end-to-end.
- Storage overhead for idempotency records (TTL expiry).

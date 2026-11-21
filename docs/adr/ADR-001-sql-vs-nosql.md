# ADR-001: PostgreSQL for transactional data, MongoDB for audit timeline

## Status
Accepted

## Context
Orders, payments, accounts require ACID transactions and optimistic locking. Audit trail is append-only, schema-flexible, high-volume.

## Decision
Use PostgreSQL for aggregates/transactions with `@Version` optimistic locking. Use MongoDB for append-only `audit_events` collection storing full event payloads and state history.

## Consequences
- Strong consistency where money moves.
- Flexible, scalable audit store; no joins needed.
- Dual persistence increases operational complexity (mitigated by docker-compose).

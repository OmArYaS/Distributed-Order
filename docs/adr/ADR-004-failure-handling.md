# ADR-004: Failure handling and consistency

## Status
Accepted

## Context
Payment may fail after order creation; services are separate datastores.

## Decision
- Optimistic locking (`@Version`) on Order, PaymentTransaction, Account.
- Saga-ish orchestration: OrderCreated -> reserve payment -> PaymentReserved/Failed -> update order status.
- Compensation via refund (credit account, mark refunded).
- Audit all state changes to MongoDB for forensics.

## Consequences
- Eventual consistency between services; manual reconciliation possible via audit log.
- No distributed transactions (2PC) to keep services decoupled.

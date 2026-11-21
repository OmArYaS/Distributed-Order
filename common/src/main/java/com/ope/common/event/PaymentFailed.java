package com.ope.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentFailed(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        int version,
        UUID orderId,
        BigDecimal amount,
        String reason,
        boolean retryable
) implements DomainEvent {
}

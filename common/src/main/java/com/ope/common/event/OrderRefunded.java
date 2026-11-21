package com.ope.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OrderRefunded(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        int version,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount,
        String reason
) implements DomainEvent {
}

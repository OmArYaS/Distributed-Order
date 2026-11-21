package com.ope.common.event;

import java.time.Instant;
import java.util.UUID;

public record OrderCompleted(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        int version,
        UUID orderId
) implements DomainEvent {
}

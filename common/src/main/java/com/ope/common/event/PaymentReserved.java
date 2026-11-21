package com.ope.common.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentReserved(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        int version,
        UUID orderId,
        UUID paymentId,
        BigDecimal amount,
        String currency
) implements DomainEvent {
}

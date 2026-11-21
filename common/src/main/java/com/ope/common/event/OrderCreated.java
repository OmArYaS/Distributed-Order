package com.ope.common.event;

import com.ope.common.dto.OrderItemDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderCreated(
        UUID eventId,
        Instant occurredAt,
        String correlationId,
        String aggregateId,
        int version,
        UUID orderId,
        UUID customerId,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemDto> items
) implements DomainEvent {
}

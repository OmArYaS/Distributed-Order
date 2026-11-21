package com.ope.common.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemDto> items,
        Instant createdAt,
        Instant updatedAt,
        Long version
) {
}

package com.ope.common.event;

import java.time.Instant;
import java.util.UUID;

public interface DomainEvent {
    UUID eventId();
    Instant occurredAt();
    String correlationId();
    String aggregateId();
    int version();

    default String eventType() {
        return getClass().getSimpleName();
    }
}

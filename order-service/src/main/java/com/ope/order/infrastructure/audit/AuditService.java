package com.ope.order.infrastructure.audit;

import com.ope.common.event.DomainEvent;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuditService {

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(String aggregateType, String aggregateId, DomainEvent event, Object payload) {
        AuditEventDocument doc = new AuditEventDocument();
        doc.setAggregateType(aggregateType);
        doc.setAggregateId(aggregateId);
        doc.setEventType(event.eventType());
        doc.setPayload(Map.of("event", payload));
        doc.setCorrelationId(event.correlationId());
        doc.setOccurredAt(event.occurredAt() == null ? Instant.now() : event.occurredAt());
        doc.setSourceService("order-service");
        repository.save(doc);
    }

    public void record(String aggregateType, String aggregateId, String eventType, Object payload, String correlationId) {
        AuditEventDocument doc = new AuditEventDocument();
        doc.setAggregateType(aggregateType);
        doc.setAggregateId(aggregateId);
        doc.setEventType(eventType);
        doc.setPayload(Map.of("event", payload));
        doc.setCorrelationId(correlationId);
        doc.setOccurredAt(Instant.now());
        doc.setSourceService("order-service");
        repository.save(doc);
    }
}

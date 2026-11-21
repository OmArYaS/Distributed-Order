package com.ope.payment.infrastructure.audit;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class AuditService {
    private final AuditEventRepository repository;
    public AuditService(AuditEventRepository repository) { this.repository = repository; }

    public void record(String aggregateType, String aggregateId, String eventType, Object payload, String correlationId) {
        AuditEventDocument doc = new AuditEventDocument();
        doc.setAggregateType(aggregateType);
        doc.setAggregateId(aggregateId);
        doc.setEventType(eventType);
        doc.setPayload(Map.of("event", payload));
        doc.setCorrelationId(correlationId);
        doc.setOccurredAt(Instant.now());
        doc.setSourceService("payment-service");
        repository.save(doc);
    }
}

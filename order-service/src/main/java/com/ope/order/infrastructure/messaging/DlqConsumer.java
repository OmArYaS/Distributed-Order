package com.ope.order.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DlqConsumer {
    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);
    private final MeterRegistry meterRegistry;

    public DlqConsumer(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @KafkaListener(topics = {"payment.reserved.dlq", "payment.failed.dlq"}, groupId = "order-service-dlq")
    public void onDlq(ConsumerRecord<String, Object> record) {
        meterRegistry.counter("messaging.dlq.count").increment();
        log.error("DLQ message received key={}", record.key());
    }
}

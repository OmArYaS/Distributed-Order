package com.ope.payment.infrastructure.messaging;

import com.ope.common.event.OrderCreated;
import com.ope.common.messaging.CorrelationConstants;
import com.ope.payment.application.PaymentService;
import com.ope.payment.domain.ProcessedEvent;
import com.ope.payment.domain.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final PaymentService paymentService;
    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    public OrderEventConsumer(PaymentService paymentService,
                              ProcessedEventRepository processedEventRepository,
                              MeterRegistry meterRegistry) {
        this.paymentService = paymentService;
        this.processedEventRepository = processedEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = "order.created", groupId = "payment-service")
    @Transactional
    public void onOrderCreated(ConsumerRecord<String, Object> record,
                               @Header(name = CorrelationConstants.KAFKA_HEADER_CORRELATION_ID, required = false) String correlationId) {
        OrderCreated event = (OrderCreated) record.value();
        if (processedEventRepository.existsByEventId(event.eventId())) {
            log.info("Duplicate event skipped eventId={}", event.eventId());
            return;
        }
        String corr = correlationId == null ? event.correlationId() : correlationId;
        MDC.put(CorrelationConstants.MDC_CORRELATION_ID, corr);
        try {
            paymentService.reservePayment(event, corr);
            ProcessedEvent pe = new ProcessedEvent();
            pe.setEventId(event.eventId());
            pe.setConsumerGroup("payment-service");
            pe.setProcessedAt(Instant.now());
            processedEventRepository.save(pe);
        } finally {
            MDC.remove(CorrelationConstants.MDC_CORRELATION_ID);
        }
    }

    @KafkaListener(topics = "order.created.dlq", groupId = "payment-service-dlq")
    public void onDlq(ConsumerRecord<String, Object> record) {
        meterRegistry.counter("messaging.dlq.count", "topic", "order.created").increment();
        log.error("DLQ message received topic=order.created key={}", record.key());
    }
}

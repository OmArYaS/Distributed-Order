package com.ope.order.infrastructure.messaging;

import com.ope.common.event.PaymentFailed;
import com.ope.common.event.PaymentReserved;
import com.ope.common.messaging.CorrelationConstants;
import com.ope.order.application.OrderService;
import com.ope.order.domain.ProcessedEvent;
import com.ope.order.domain.ProcessedEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    private final OrderService orderService;
    private final ProcessedEventRepository processedEventRepository;
    private final MeterRegistry meterRegistry;

    public PaymentEventConsumer(OrderService orderService,
                               ProcessedEventRepository processedEventRepository,
                               MeterRegistry meterRegistry) {
        this.orderService = orderService;
        this.processedEventRepository = processedEventRepository;
        this.meterRegistry = meterRegistry;
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = "payment.reserved", groupId = "order-service")
    @Transactional
    public void onPaymentReserved(ConsumerRecord<String, Object> record,
                                  @Header(name = CorrelationConstants.KAFKA_HEADER_CORRELATION_ID, required = false) String correlationId) {
        PaymentReserved e = (PaymentReserved) record.value();
        if (processedEventRepository.existsByEventId(e.eventId())) {
            return;
        }
        String corr = correlationId == null ? e.correlationId() : correlationId;
        MDC.put(CorrelationConstants.MDC_CORRELATION_ID, corr);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            orderService.handlePaymentReserved(e.orderId(), e.paymentId());
            markProcessed(e.eventId());
            log.info("PaymentReserved handled orderId={}", e.orderId());
        } finally {
            sample.stop(Timer.builder("messaging.processing.duration").tag("topic", "payment.reserved").register(meterRegistry));
            MDC.remove(CorrelationConstants.MDC_CORRELATION_ID);
        }
    }

    @RetryableTopic(
            attempts = "4",
            backoff = @org.springframework.retry.annotation.Backoff(delay = 1000, multiplier = 2.0, maxDelay = 30000),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".dlq"
    )
    @KafkaListener(topics = "payment.failed", groupId = "order-service")
    @Transactional
    public void onPaymentFailed(ConsumerRecord<String, Object> record,
                                @Header(name = CorrelationConstants.KAFKA_HEADER_CORRELATION_ID, required = false) String correlationId) {
        PaymentFailed e = (PaymentFailed) record.value();
        if (processedEventRepository.existsByEventId(e.eventId())) {
            return;
        }
        String corr = correlationId == null ? e.correlationId() : correlationId;
        MDC.put(CorrelationConstants.MDC_CORRELATION_ID, corr);
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            orderService.handlePaymentFailed(e.orderId(), e.reason());
            markProcessed(e.eventId());
            log.info("PaymentFailed handled orderId={}", e.orderId());
        } finally {
            sample.stop(Timer.builder("messaging.processing.duration").tag("topic", "payment.failed").register(meterRegistry));
            MDC.remove(CorrelationConstants.MDC_CORRELATION_ID);
        }
    }

    private void markProcessed(UUID eventId) {
        ProcessedEvent pe = new ProcessedEvent();
        pe.setEventId(eventId);
        pe.setConsumerGroup("order-service");
        pe.setProcessedAt(Instant.now());
        processedEventRepository.save(pe);
    }
}

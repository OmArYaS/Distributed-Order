package com.ope.order.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ope.common.dto.CreateOrderRequest;
import com.ope.common.dto.OrderItemDto;
import com.ope.common.dto.OrderItemRequest;
import com.ope.common.dto.OrderResponse;
import com.ope.common.event.OrderCreated;
import com.ope.common.messaging.CorrelationConstants;
import com.ope.common.messaging.IdempotencyConstants;
import com.ope.common.metrics.MetricNames;
import com.ope.order.domain.IdempotencyRecord;
import com.ope.order.domain.Order;
import com.ope.order.domain.OrderItem;
import com.ope.order.domain.OrderRepository;
import com.ope.order.domain.OrderStatus;
import com.ope.order.infrastructure.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private static final String ORDER_CREATED_TOPIC = "order.created";

    private final OrderRepository orderRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository,
                        IdempotencyRecordRepository idempotencyRepository,
                        KafkaTemplate<String, Object> kafkaTemplate,
                        AuditService auditService,
                        MeterRegistry meterRegistry,
                        ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey, String correlationId) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            String requestHash = hash(request);
            IdempotencyRecord existing = idempotencyRepository.findById(idempotencyKey).orElse(null);
            if (existing != null) {
                if (!existing.getRequestHash().equals(requestHash)) {
                    throw new IllegalStateException("Idempotency key reused with different request body");
                }
                log.info("Duplicate idempotent request detected, returning cached response key={}", idempotencyKey);
                // re-fetch order by key
                Order cached = orderRepository.findByIdempotencyKey(idempotencyKey).orElseThrow();
                return toResponse(cached);
            }

            Order order = new Order();
            order.setCustomerId(request.customerId());
            order.setCurrency(request.currency());
            order.setStatus(OrderStatus.CREATED);
            order.setIdempotencyKey(idempotencyKey);
            order.setCreatedAt(Instant.now());
            order.setUpdatedAt(Instant.now());

            BigDecimal total = BigDecimal.ZERO;
            for (OrderItemRequest ir : request.items()) {
                OrderItem item = new OrderItem();
                item.setOrder(order);
                item.setProductId(ir.productId());
                item.setProductName(ir.productName());
                item.setQuantity(ir.quantity());
                item.setUnitPrice(ir.unitPrice());
                item.setLineTotal(ir.unitPrice().multiply(BigDecimal.valueOf(ir.quantity())));
                order.getItems().add(item);
                total = total.add(item.getLineTotal());
            }
            order.setTotalAmount(total);

            order = orderRepository.save(order);

            IdempotencyRecord record = new IdempotencyRecord();
            record.setKey(idempotencyKey);
            record.setRequestHash(requestHash);
            record.setResponseStatus(201);
            record.setResponseBody(order.getId().toString());
            record.setExpiresAt(Instant.now().plusSeconds(86400));
            idempotencyRepository.save(record);

            OrderCreated event = new OrderCreated(
                    UUID.randomUUID(),
                    Instant.now(),
                    correlationId,
                    order.getId().toString(),
                    1,
                    order.getId(),
                    order.getCustomerId(),
                    order.getTotalAmount(),
                    order.getCurrency(),
                    order.getItems().stream().map(i -> new OrderItemDto(i.getProductId(), i.getProductName(), i.getQuantity(), i.getUnitPrice())).toList()
            );

            publish(ORDER_CREATED_TOPIC, order.getId().toString(), event, correlationId);

            auditService.record("ORDER", order.getId().toString(), event, event);

            meterRegistry.counter(MetricNames.ORDER_CREATION_COUNT).increment();
            log.info("Order created orderId={} total={}", order.getId(), total);

            return toResponse(order);
        } finally {
            sample.stop(Timer.builder(MetricNames.ORDER_CREATION_LATENCY).register(meterRegistry));
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
        return toResponse(order);
    }

    @Transactional
    public OrderResponse retryPayment(UUID orderId, String idempotencyKey, String correlationId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(OrderStatus.PAYMENT_PENDING);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        // republish OrderCreated to retrigger payment
        OrderCreated event = new OrderCreated(
                UUID.randomUUID(), Instant.now(), correlationId, order.getId().toString(), order.getVersion() == null ? 1 : order.getVersion().intValue(),
                order.getId(), order.getCustomerId(), order.getTotalAmount(), order.getCurrency(), order.toItemDtos()
        );
        publish(ORDER_CREATED_TOPIC, order.getId().toString(), event, correlationId);
        auditService.record("ORDER", order.getId().toString(), event, event);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse refund(UUID orderId, String idempotencyKey, String correlationId) {
        Order order = orderRepository.findById(orderId).orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        order.setStatus(OrderStatus.REFUNDED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditService.record("ORDER", order.getId().toString(), "OrderRefunded", order, correlationId);
        return toResponse(order);
    }

    @Transactional
    public void handlePaymentReserved(UUID orderId, UUID paymentId) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PAID);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        OrderCompleted completed = new OrderCompleted(UUID.randomUUID(), Instant.now(), MDC.get(CorrelationConstants.MDC_CORRELATION_ID), orderId.toString(), 1, orderId);
        publish("order.completed", orderId.toString(), completed, MDC.get(CorrelationConstants.MDC_CORRELATION_ID));
        auditService.record("ORDER", orderId.toString(), completed, completed);
    }

    @Transactional
    public void handlePaymentFailed(UUID orderId, String reason) {
        Order order = orderRepository.findById(orderId).orElseThrow();
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        auditService.record("ORDER", orderId.toString(), "PaymentFailed", reason, MDC.get(CorrelationConstants.MDC_CORRELATION_ID));
    }

    private void publish(String topic, String key, Object event, String correlationId) {
        org.springframework.messaging.MessageBuilder<?> builder = org.springframework.messaging.support.MessageBuilder.withPayload(event)
                .setHeader(org.springframework.kafka.support.KafkaHeaders.TOPIC, topic)
                .setHeader(org.springframework.kafka.support.KafkaHeaders.KEY, key)
                .setHeader(CorrelationConstants.KAFKA_HEADER_CORRELATION_ID, correlationId)
                .setHeader(CorrelationConstants.KAFKA_HEADER_EVENT_TYPE, event.getClass().getSimpleName());
        if (event instanceof com.ope.common.event.DomainEvent de) {
            builder.setHeader(CorrelationConstants.KAFKA_HEADER_EVENT_ID, de.eventId().toString());
        }
        kafkaTemplate.send(builder.build());
    }
    private OrderResponse toResponse(Order o) {
        return new OrderResponse(o.getId(), o.getCustomerId(), o.getStatus().name(), o.getTotalAmount(), o.getCurrency(),
                o.toItemDtos(), o.getCreatedAt(), o.getUpdatedAt(), o.getVersion());
    }

    private String hash(Object o) {
        try {
            String json = objectMapper.writeValueAsString(o);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

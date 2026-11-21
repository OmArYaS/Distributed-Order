package com.ope.order.application;

import com.ope.common.dto.CreateOrderRequest;
import com.ope.common.dto.OrderItemRequest;
import com.ope.common.event.OrderCreated;
import com.ope.order.domain.IdempotencyRecordRepository;
import com.ope.order.domain.Order;
import com.ope.order.domain.OrderRepository;
import com.ope.order.infrastructure.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock IdempotencyRecordRepository idempotencyRepository;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;
    @Mock AuditService auditService;
    @Mock com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();

    OrderService orderService;

    @org.junit.jupiter.api.BeforeEach
    void setup() {
        orderService = new OrderService(orderRepository, idempotencyRepository, kafkaTemplate, auditService, meterRegistry, objectMapper);
    }

    @Test
    void createOrder_persistsAndReturns() {
        when(idempotencyRepository.findById(any())).thenReturn(java.util.Optional.empty());
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            return o;
        });
        when(objectMapper.valueToTree(any())).thenReturn(null);

        CreateOrderRequest req = new CreateOrderRequest(
                UUID.randomUUID(), "USD",
                List.of(new OrderItemRequest(UUID.randomUUID(), "Widget", 2, new BigDecimal("5.00"))));

        // objectMapper.writeValueAsString may be called; mock it
        try {
            when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        } catch (Exception e) { /* ignore */ }

        var resp = orderService.createOrder(req, "key-1", "corr-1");
        assertThat(resp).isNotNull();
        assertThat(resp.status()).isEqualTo("CREATED");
    }
}

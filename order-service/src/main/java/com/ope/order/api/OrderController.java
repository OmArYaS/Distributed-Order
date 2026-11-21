package com.ope.order.api;

import com.ope.common.dto.CreateOrderRequest;
import com.ope.common.dto.OrderResponse;
import com.ope.common.messaging.IdempotencyConstants;
import com.ope.order.application.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader(IdempotencyConstants.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        OrderResponse response = orderService.createOrder(request, idempotencyKey, correlationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PostMapping("/{id}/retry-payment")
    public ResponseEntity<OrderResponse> retry(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyConstants.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        return ResponseEntity.ok(orderService.retryPayment(id, idempotencyKey, correlationId));
    }

    @PostMapping("/{id}/refund")
    public ResponseEntity<OrderResponse> refund(
            @PathVariable UUID id,
            @RequestHeader(IdempotencyConstants.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        return ResponseEntity.ok(orderService.refund(id, idempotencyKey, correlationId));
    }
}

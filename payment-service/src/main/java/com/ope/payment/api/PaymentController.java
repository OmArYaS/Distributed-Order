package com.ope.payment.api;

import com.ope.common.messaging.IdempotencyConstants;
import com.ope.payment.application.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/{id}/refund")
    public ResponseEntity<Void> refund(@PathVariable UUID id,
                                      @RequestHeader(IdempotencyConstants.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
                                      @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        paymentService.refund(id, "requested", correlationId);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<?> account(@PathVariable UUID id) {
        return ResponseEntity.ok().build();
    }
}

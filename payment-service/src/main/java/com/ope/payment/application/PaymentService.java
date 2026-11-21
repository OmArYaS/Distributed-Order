package com.ope.payment.application;

import com.ope.common.event.OrderCreated;
import com.ope.common.event.PaymentFailed;
import com.ope.common.event.PaymentReserved;
import com.ope.common.messaging.CorrelationConstants;
import com.ope.payment.domain.Account;
import com.ope.payment.domain.AccountRepository;
import com.ope.payment.domain.IdempotencyRecord;
import com.ope.payment.domain.IdempotencyRecordRepository;
import com.ope.payment.domain.PaymentTransaction;
import com.ope.payment.domain.PaymentTransactionRepository;
import com.ope.payment.domain.PaymentStatus;
import com.ope.payment.infrastructure.audit.AuditService;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final AccountRepository accountRepository;
    private final PaymentTransactionRepository paymentRepository;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditService auditService;
    private final MeterRegistry meterRegistry;

    public PaymentService(AccountRepository accountRepository,
                          PaymentTransactionRepository paymentRepository,
                          IdempotencyRecordRepository idempotencyRepository,
                          KafkaTemplate<String, Object> kafkaTemplate,
                          AuditService auditService,
                          MeterRegistry meterRegistry) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.auditService = auditService;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public void reservePayment(OrderCreated event, String correlationId) {
        // idempotency: skip if payment already exists for order
        if (paymentRepository.findByOrderId(event.orderId()).isPresent()) {
            log.info("Payment already processed for orderId={}, skipping", event.orderId());
            return;
        }

        Account account = accountRepository.findByCustomerId(event.customerId()).orElseGet(() -> {
            Account a = new Account();
            a.setCustomerId(event.customerId());
            a.setBalance(new BigDecimal("10000.00"));
            a.setCurrency(event.currency());
            return accountRepository.save(a);
        });

        BigDecimal amount = event.totalAmount();
        if (account.getBalance().compareTo(amount) < 0) {
            PaymentTransaction failed = new PaymentTransaction();
            failed.setOrderId(event.orderId());
            failed.setAccountId(account.getId());
            failed.setAmount(amount);
            failed.setCurrency(event.currency());
            failed.setStatus(PaymentStatus.FAILED);
            failed.setFailureReason("Insufficient funds");
            failed.setCreatedAt(Instant.now());
            failed.setUpdatedAt(Instant.now());
            paymentRepository.save(failed);

            PaymentFailed pf = new PaymentFailed(UUID.randomUUID(), Instant.now(), correlationId, event.orderId().toString(), 1,
                    event.orderId(), amount, "Insufficient funds", false);
            publish("payment.failed", event.orderId().toString(), pf, correlationId);
            auditService.record("PAYMENT", event.orderId().toString(), "PaymentFailed", pf, correlationId);
            meterRegistry.counter("payment.failure.count").increment();
            return;
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);

        PaymentTransaction tx = new PaymentTransaction();
        tx.setOrderId(event.orderId());
        tx.setAccountId(account.getId());
        tx.setAmount(amount);
        tx.setCurrency(event.currency());
        tx.setStatus(PaymentStatus.RESERVED);
        tx.setCreatedAt(Instant.now());
        tx.setUpdatedAt(Instant.now());
        tx = paymentRepository.save(tx);

        PaymentReserved pr = new PaymentReserved(UUID.randomUUID(), Instant.now(), correlationId, event.orderId().toString(), 1,
                event.orderId(), tx.getId(), amount, event.currency());
        publish("payment.reserved", event.orderId().toString(), pr, correlationId);
        auditService.record("PAYMENT", event.orderId().toString(), "PaymentReserved", pr, correlationId);
        meterRegistry.counter("payment.success.count").increment();
        log.info("Payment reserved orderId={} paymentId={}", event.orderId(), tx.getId());
    }

    @Transactional
    public void refund(UUID paymentId, String reason, String correlationId) {
        PaymentTransaction tx = paymentRepository.findById(paymentId).orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        if (tx.getStatus() == PaymentStatus.REFUNDED) return;
        Account account = accountRepository.findById(tx.getAccountId()).orElseThrow();
        account.setBalance(account.getBalance().add(tx.getAmount()));
        accountRepository.save(account);
        tx.setStatus(PaymentStatus.REFUNDED);
        tx.setUpdatedAt(Instant.now());
        paymentRepository.save(tx);
        auditService.record("PAYMENT", tx.getOrderId().toString(), "PaymentRefunded", tx, correlationId);
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
}

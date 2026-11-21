package com.ope.order.domain;

public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    COMPLETED,
    REFUNDED,
    CANCELLED
}

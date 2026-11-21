package com.ope.common.metrics;

public final class MetricNames {
    public static final String ORDER_CREATION_COUNT = "order.creation.count";
    public static final String ORDER_CREATION_LATENCY = "order.creation.latency";
    public static final String PAYMENT_SUCCESS_COUNT = "payment.success.count";
    public static final String PAYMENT_FAILURE_COUNT = "payment.failure.count";
    public static final String MESSAGING_PROCESSING_DURATION = "messaging.processing.duration";
    public static final String MESSAGING_DLQ_COUNT = "messaging.dlq.count";
    private MetricNames() {}
}

package com.ope.common.messaging;

public final class CorrelationConstants {
    public static final String HEADER_CORRELATION_ID = "X-Correlation-Id";
    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String KAFKA_HEADER_CORRELATION_ID = "correlationId";
    public static final String KAFKA_HEADER_EVENT_ID = "eventId";
    public static final String KAFKA_HEADER_EVENT_TYPE = "eventType";

    private CorrelationConstants() {}
}

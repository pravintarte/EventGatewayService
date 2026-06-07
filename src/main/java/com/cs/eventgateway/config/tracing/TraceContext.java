package com.cs.eventgateway.config.tracing;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Minimal trace context used for explicit Gateway-to-Account-Service propagation.
 */
public final class TraceContext {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String MDC_TRACE_ID_KEY = "traceId";
    public static final String MDC_APP_TRACE_ID_KEY = "appTraceId";

    private TraceContext() {
    }

    public static String currentOrNewTraceId() {
        String traceId = MDC.get(MDC_APP_TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(MDC_TRACE_ID_KEY);
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = newTraceId();
        }
        return traceId;
    }

    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

package com.cs.eventgateway.config.tracing;

import java.util.UUID;

import org.slf4j.MDC;

/**
 * Minimal application correlation context used for explicit
 * Gateway-to-Account-Service propagation.
 *
 * <p>Micrometer owns the {@code traceId} MDC key used by Zipkin. This class
 * only writes {@code appTraceId} so log correlation does not mask the real
 * exported trace id.</p>
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

    public static TraceScope startNewTrace() {
        return startTrace(newTraceId());
    }

    public static TraceScope startTrace(String traceId) {
        return new TraceScope(traceId);
    }

    public static final class TraceScope implements AutoCloseable {

        private final String previousAppTraceId;

        private TraceScope(String traceId) {
            this.previousAppTraceId = MDC.get(MDC_APP_TRACE_ID_KEY);
            MDC.put(MDC_APP_TRACE_ID_KEY, traceId);
        }

        @Override
        public void close() {
            restore(MDC_APP_TRACE_ID_KEY, previousAppTraceId);
        }

        private void restore(String key, String previousValue) {
            if (previousValue == null || previousValue.isBlank()) {
                MDC.remove(key);
            } else {
                MDC.put(key, previousValue);
            }
        }
    }
}

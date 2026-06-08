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

    /**
     * HTTP header used to expose and propagate the application correlation id.
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * MDC key populated by Micrometer tracing for the exported distributed trace id.
     */
    public static final String MDC_TRACE_ID_KEY = "traceId";

    /**
     * MDC key owned by the gateway for explicit header propagation and log correlation.
     */
    public static final String MDC_APP_TRACE_ID_KEY = "appTraceId";

    /**
     * Prevents instantiation of the static trace-context helper.
     */
    private TraceContext() {
    }

    /**
     * Resolves the best available correlation id for an outbound call.
     *
     * <p>The gateway first prefers {@link #MDC_APP_TRACE_ID_KEY} because that
     * value is intentionally mirrored into {@link #TRACE_ID_HEADER}. If no
     * application id is active, it falls back to Micrometer's {@code traceId}
     * so scheduler or framework-created tracing spans can still be propagated.
     * A new id is generated only when no MDC value is available.</p>
     *
     * @return active application trace id, active Micrometer trace id, or a new id
     */
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

    /**
     * Creates a new lowercase hexadecimal trace id suitable for headers and MDC values.
     *
     * @return newly generated trace id without UUID separators
     */
    public static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * Starts a scoped application trace using a newly generated id.
     *
     * <p>This is primarily used by background work, such as scheduled retries,
     * where there is no inbound HTTP request to provide a correlation id.</p>
     *
     * @return scope that restores the previous MDC value when closed
     */
    public static TraceScope startNewTrace() {
        return startTrace(newTraceId());
    }

    /**
     * Starts a scoped application trace using the supplied id.
     *
     * <p>The supplied id is stored only under {@link #MDC_APP_TRACE_ID_KEY}.
     * Micrometer remains responsible for the standard {@code traceId} and
     * {@code spanId} MDC keys that are exported to Zipkin.</p>
     *
     * @param traceId trace id to expose as the active application correlation id
     * @return scope that restores the previous MDC value when closed
     */
    public static TraceScope startTrace(String traceId) {
        return new TraceScope(traceId);
    }

    /**
     * Auto-closeable MDC scope for the application correlation id.
     *
     * <p>The scope captures the previous {@code appTraceId} before installing
     * the new value. Closing the scope restores that exact previous state so
     * nested request handling and background tasks do not leak correlation ids
     * into unrelated log events.</p>
     */
    public static final class TraceScope implements AutoCloseable {

        private final String previousAppTraceId;

        /**
         * Installs the supplied application trace id while capturing the previous MDC value.
         *
         * @param traceId trace id to make active for the lifetime of this scope
         */
        private TraceScope(String traceId) {
            this.previousAppTraceId = MDC.get(MDC_APP_TRACE_ID_KEY);
            MDC.put(MDC_APP_TRACE_ID_KEY, traceId);
        }

        /**
         * Restores the MDC value that was active before this scope was created.
         */
        @Override
        public void close() {
            restore(MDC_APP_TRACE_ID_KEY, previousAppTraceId);
        }

        /**
         * Restores or removes an MDC entry to match the value captured at scope entry.
         *
         * @param key MDC key to restore
         * @param previousValue value that existed before the scope started
         */
        private void restore(String key, String previousValue) {
            if (previousValue == null || previousValue.isBlank()) {
                MDC.remove(key);
            } else {
                MDC.put(key, previousValue);
            }
        }
    }
}

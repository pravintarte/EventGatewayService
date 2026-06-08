package com.cs.eventgateway.config.tracing;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Mirrors the active distributed trace id into the gateway correlation context.
 *
 * <p>The filter is intentionally ordered at {@link Ordered#LOWEST_PRECEDENCE}
 * so Spring's observation and tracing filters can create the server span first.
 * When a Micrometer span exists, its exported trace id is reused for the
 * response header, downstream Account Service calls, structured logs, and
 * Zipkin. Only when tracing is unavailable does the filter fall back to an
 * inbound {@code X-Trace-Id} header or a locally generated id.</p>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    private final ObjectProvider<Tracer> tracerProvider;

    /**
     * Creates the filter with a lazily resolved Micrometer tracer.
     *
     * <p>The provider keeps the filter usable in test slices or profiles where
     * tracing infrastructure is not present, while still allowing the runtime
     * service to align {@code X-Trace-Id} with the exported Zipkin trace id.</p>
     *
     * @param tracerProvider optional provider for the active Micrometer tracer
     */
    public TraceContextFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    /**
     * Establishes the per-request application trace scope and propagates it downstream.
     *
     * <p>The method does not create or modify Micrometer spans. It only selects
     * the correlation id that should be visible to application code, writes it
     * to the response header, and stores it under {@code appTraceId} for the
     * duration of the remaining filter chain.</p>
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain remaining servlet filter chain
     * @throws ServletException when the downstream filter chain fails
     * @throws IOException when request or response IO fails
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = currentSpanTraceId();

        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        }

        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.newTraceId();
        }

        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try (TraceContext.TraceScope ignored = TraceContext.startTrace(traceId)) {
            filterChain.doFilter(request, response);
        }
    }

    /**
     * Reads the trace id from the currently active Micrometer span when available.
     *
     * <p>A {@code null} return means tracing is absent, no span is active, or
     * the span does not expose a context. The caller then applies the existing
     * header and generated-id fallbacks without changing distributed tracing
     * behavior.</p>
     *
     * @return active exported trace id, or {@code null} when none is available
     */
    private String currentSpanTraceId() {
        Tracer tracer = tracerProvider.getIfAvailable();
        if (tracer == null) {
            return null;
        }
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null || currentSpan.context() == null) {
            return null;
        }
        return currentSpan.context().traceId();
    }
}

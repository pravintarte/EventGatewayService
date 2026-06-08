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
 * Ensures every inbound request has a stable trace id available to logs and downstream calls.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    private final ObjectProvider<Tracer> tracerProvider;

    public TraceContextFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

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

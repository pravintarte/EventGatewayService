package com.cs.eventgateway.config.tracing;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every inbound request has a stable trace id available to logs and downstream calls.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String previousTraceId = MDC.get(TraceContext.MDC_TRACE_ID_KEY);
        String previousAppTraceId = MDC.get(TraceContext.MDC_APP_TRACE_ID_KEY);
        String traceId = request.getHeader(TraceContext.TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = previousAppTraceId == null || previousAppTraceId.isBlank()
                    ? TraceContext.newTraceId()
                    : previousAppTraceId;
        }

        MDC.put(TraceContext.MDC_TRACE_ID_KEY, traceId);
        MDC.put(TraceContext.MDC_APP_TRACE_ID_KEY, traceId);
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousTraceId == null || previousTraceId.isBlank()) {
                MDC.remove(TraceContext.MDC_TRACE_ID_KEY);
            } else {
                MDC.put(TraceContext.MDC_TRACE_ID_KEY, previousTraceId);
            }
            if (previousAppTraceId == null || previousAppTraceId.isBlank()) {
                MDC.remove(TraceContext.MDC_APP_TRACE_ID_KEY);
            } else {
                MDC.put(TraceContext.MDC_APP_TRACE_ID_KEY, previousAppTraceId);
            }
        }
    }
}

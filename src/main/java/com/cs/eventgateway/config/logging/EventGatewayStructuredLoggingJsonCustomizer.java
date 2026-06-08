package com.cs.eventgateway.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.cs.eventgateway.config.tracing.TraceContext;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

/**
 * Adds gateway-standard fields to every structured log line.
 */
public class EventGatewayStructuredLoggingJsonCustomizer
        implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    private static final String DEFAULT_SERVICE_NAME = "event-gateway-api";

    private final String serviceName;

    public EventGatewayStructuredLoggingJsonCustomizer(Environment environment) {
        this.serviceName = environment.getProperty("spring.application.name", DEFAULT_SERVICE_NAME);
    }

    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        members.add("serviceName", serviceName);
        members.add("traceId", (event) -> mdcValue(event, TraceContext.MDC_TRACE_ID_KEY));
        members.add("spanId", (event) -> mdcValue(event, "spanId"));
        members.add("appTraceId", (event) -> mdcValue(event, TraceContext.MDC_APP_TRACE_ID_KEY));
    }

    private String mdcValue(ILoggingEvent event, String key) {
        if (event == null || event.getMDCPropertyMap() == null) {
            return "";
        }
        String value = event.getMDCPropertyMap().get(key);
        return value == null ? "" : value;
    }
}

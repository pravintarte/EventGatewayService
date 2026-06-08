package com.cs.eventgateway.config.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.cs.eventgateway.config.tracing.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;
import org.springframework.core.env.Environment;

/**
 * Adds Event Gateway standard fields to every structured JSON log line.
 *
 * <p>Spring Boot creates this customizer from the class name configured under
 * {@code logging.structured.json.customizer}. The instance is then asked to
 * register additional JSON members that Logback writes for each logging event.
 * Keeping the enrichment in one customizer prevents controllers, services, and
 * infrastructure classes from each having to remember the operational log
 * schema.</p>
 *
 * <p>The log contract intentionally separates distributed tracing from
 * application-level correlation:</p>
 *
 * <ul>
 *     <li>{@code serviceName}: stable logical service name used by log queries.</li>
 *     <li>{@code traceId}: Micrometer/Zipkin trace id from the logging MDC.</li>
 *     <li>{@code spanId}: Micrometer/Zipkin span id from the logging MDC.</li>
 *     <li>{@code appTraceId}: gateway-facing correlation id mirrored in {@code X-Trace-Id}.</li>
 * </ul>
 *
 * <p>Do not emit logs while resolving per-event MDC values. This class is part
 * of the logging pipeline, so logging from inside {@link #mdcValue(ILoggingEvent, String)}
 * would run once per log event and can create recursive logging behavior.</p>
 */
public class EventGatewayStructuredLoggingJsonCustomizer
        implements StructuredLoggingJsonMembersCustomizer<ILoggingEvent> {

    private static final Logger log = LoggerFactory.getLogger(EventGatewayStructuredLoggingJsonCustomizer.class);
    private static final String DEFAULT_SERVICE_NAME = "event-gateway-api";

    private final String serviceName;

    /**
     * Creates the structured logging customizer and resolves the service name
     * that will be attached to every JSON log line.
     *
     * <p>The service name is read from {@code spring.application.name}. If that
     * property is unavailable during early logging initialization, the
     * Event Gateway default is used so the log field remains stable.</p>
     *
     * @param environment Spring environment used to resolve application metadata
     */
    public EventGatewayStructuredLoggingJsonCustomizer(Environment environment) {
        this.serviceName = environment.getProperty("spring.application.name", DEFAULT_SERVICE_NAME);
        log.info("Configured structured logging customizer serviceName={}", serviceName);
    }

    /**
     * Registers additional JSON members for each Logback logging event.
     *
     * <p>The member callbacks are intentionally null-safe. Some startup logs can
     * be emitted before Micrometer has populated tracing MDC keys, and logs
     * outside an HTTP request may not have trace context at all. In those cases
     * this customizer returns empty strings instead of omitting fields, preserving
     * a predictable JSON shape for downstream log indexing.</p>
     *
     * @param members mutable structured-log member registry supplied by Spring Boot
     */
    @Override
    public void customize(JsonWriter.Members<ILoggingEvent> members) {
        log.debug("Registering structured log members serviceName={} traceId spanId appTraceId", serviceName);
        members.add("serviceName", serviceName);
        members.add("traceId", (event) -> mdcValue(event, TraceContext.MDC_TRACE_ID_KEY));
        members.add("spanId", (event) -> mdcValue(event, "spanId"));
        members.add("appTraceId", (event) -> mdcValue(event, TraceContext.MDC_APP_TRACE_ID_KEY));
    }

    /**
     * Reads one value from a logging event's MDC map.
     *
     * <p>This helper deliberately returns an empty string for missing values.
     * Empty strings make the absence of a value explicit while keeping field
     * presence consistent across startup logs, scheduled tasks, request logs,
     * and test logs.</p>
     *
     * @param event Logback event that may carry MDC values
     * @param key MDC key to read
     * @return MDC value, or an empty string when the event, MDC map, or key is absent
     */
    private String mdcValue(ILoggingEvent event, String key) {
        if (event == null || event.getMDCPropertyMap() == null) {
            return "";
        }
        String value = event.getMDCPropertyMap().get(key);
        return value == null ? "" : value;
    }
}

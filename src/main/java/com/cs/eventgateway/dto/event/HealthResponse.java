package com.cs.eventgateway.dto.event;

import java.time.Instant;
import java.util.Map;

/**
 * Lightweight public health response.
 *
 * @param status service status
 * @param service service name
 * @param timestamp server-side UTC timestamp
 * @param diagnostics basic component diagnostics
 */
public record HealthResponse(String status, String service, Instant timestamp, Map<String, String> diagnostics) {
}

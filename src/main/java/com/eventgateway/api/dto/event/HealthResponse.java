package com.eventgateway.api.dto.event;

import java.time.Instant;

/**
 * Lightweight public health response.
 *
 * @param status service status
 * @param service service name
 * @param timestamp server-side UTC timestamp
 */
public record HealthResponse(String status, String service, Instant timestamp) {
}

package com.eventgateway.api.controller.advice;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error response.
 *
 * @param timestamp server-side UTC timestamp
 * @param status HTTP status code
 * @param code stable application error code
 * @param description human-readable error description
 * @param details optional validation details
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String description,
        List<String> details
) {
}

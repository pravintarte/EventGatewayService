package com.eventgateway.api.dto;

import java.time.Instant;

/**
 * Standard successful API response envelope.
 *
 * @param timestamp server-side UTC timestamp
 * @param status HTTP status code
 * @param code stable application response code
 * @param description human-readable response description
 * @param data response payload
 * @param <T> response payload type
 */
public record ApiResponse<T>(
        Instant timestamp,
        int status,
        String code,
        String description,
        T data
) {
}

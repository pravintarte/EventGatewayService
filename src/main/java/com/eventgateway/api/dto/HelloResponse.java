package com.eventgateway.api.dto;

import java.time.Instant;
import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable response body returned by the hello endpoint.
 */
@Getter
@Builder
@ToString
public class HelloResponse {

    /**
     * Human-readable confirmation that the API is available.
     */
    private final String message;

    /**
     * Logical Spring application name.
     */
    private final String serviceName;

    /**
     * Server-side UTC timestamp for the response.
     */
    private final Instant timestamp;

    /**
     * Baseline platform capabilities enabled in this skeleton.
     */
    private final List<String> capabilities;
}

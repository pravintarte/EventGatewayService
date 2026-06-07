package com.cs.eventgateway.controller.advice;

import java.time.Instant;
import java.util.List;

import com.cs.eventgateway.dto.ApiCodes;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Standard API error response.
 *
 * @param timestamp server-side UTC timestamp
 * @param status HTTP status code
 * @param code stable application error code
 * @param description human-readable error description
 * @param details optional validation details
 */
@Schema(description = "Standard API error response envelope.")
public record ApiErrorResponse(
        @Schema(description = "Server-side UTC timestamp.", example = "2026-06-06T21:00:00Z")
        Instant timestamp,

        @Schema(description = "HTTP status code.", example = "400")
        int status,

        @Schema(description = "Stable application error code.", example = ApiCodes.VALIDATION_ERROR)
        String code,

        @Schema(description = "Human-readable error description.", example = "Request validation failed.")
        String description,

        @Schema(description = "Optional validation or error details.", example = "[\"amount: must be greater than 0\"]")
        List<String> details
) {
}

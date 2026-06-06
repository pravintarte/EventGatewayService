package com.eventgateway.api.controller.advice;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.eventgateway.api.exception.DuplicateEventConflictException;
import com.eventgateway.api.exception.EventNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for stable API error response codes.
 *
 * <p>The exception handler is part of the public API contract. These tests call
 * handler methods directly and assert that known domain exceptions map to the
 * expected HTTP status and application error code. This prevents accidental
 * changes that would force clients to parse descriptions instead of relying on
 * stable machine-readable codes.</p>
 */
class GlobalExceptionHandlerTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-06-06T21:00:00Z"),
            ZoneOffset.UTC
    );

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(CLOCK);

    /**
     * Verifies the response emitted when a caller requests an event id that is
     * not present in the gateway ledger.
     *
     * <p>The expected API contract is HTTP 404 with code
     * {@code EVENT_NOT_FOUND}. The description should preserve the missing event
     * id so client logs and support tooling can identify the failed lookup.</p>
     */
    @Test
    void handleNotFound_whenEventRecordDoesNotExist_returnsNotFoundEnvelopeWithEventNotFoundCode() {
        ResponseEntity<ApiErrorResponse> response = handler.handleNotFound(new EventNotFoundException("evt-404"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("EVENT_NOT_FOUND");
        assertThat(response.getBody().description()).isEqualTo("Event not found: evt-404");
    }

    /**
     * Verifies the response emitted when an upstream system reuses an existing
     * event id with a different payload.
     *
     * <p>The expected API contract is HTTP 409 with code
     * {@code DUPLICATE_EVENT_CONFLICT}. This tells clients the request was not a
     * safe idempotent retry and should be investigated or corrected upstream.</p>
     */
    @Test
    void handleDuplicateConflict_whenEventIdIsReusedWithDifferentPayload_returnsConflictEnvelopeWithDuplicateConflictCode() {
        ResponseEntity<ApiErrorResponse> response = handler.handleDuplicateConflict(
                new DuplicateEventConflictException("evt-001")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_EVENT_CONFLICT");
        assertThat(response.getBody().description())
                .isEqualTo("Event id already exists with a different payload: evt-001");
    }
}

package com.eventgateway.api.exception;

/**
 * Raised when an upstream reuses an event id with a different payload.
 */
public class DuplicateEventConflictException extends RuntimeException {

    public DuplicateEventConflictException(String eventId) {
        super("Event id already exists with a different payload: " + eventId);
    }
}

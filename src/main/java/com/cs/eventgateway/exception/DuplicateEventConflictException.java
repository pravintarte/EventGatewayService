package com.cs.eventgateway.exception;

import java.util.UUID;

/**
 * Raised when an upstream reuses an event id with a different payload.
 */
public class DuplicateEventConflictException extends RuntimeException {

    public DuplicateEventConflictException(UUID eventId) {
        super("Event id already exists with a different payload: " + eventId);
    }
}

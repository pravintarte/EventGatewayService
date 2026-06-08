package com.cs.eventgateway.exception;

import java.util.UUID;

/**
 * Raised when an upstream reuses an event id with a different payload.
 */
public class DuplicateEventConflictException extends RuntimeException {

    /**
     * Creates an exception for an event id that already exists with different business data.
     *
     * @param eventId conflicting event id supplied by the caller
     */
    public DuplicateEventConflictException(UUID eventId) {
        super("Event id already exists with a different payload: " + eventId);
    }
}

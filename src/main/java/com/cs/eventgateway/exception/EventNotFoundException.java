package com.cs.eventgateway.exception;

import java.util.UUID;

/**
 * Raised when a requested ledger event does not exist.
 */
public class EventNotFoundException extends RuntimeException {

    /**
     * Creates an exception for a ledger lookup that did not find the requested event.
     *
     * @param eventId missing event id supplied by the caller
     */
    public EventNotFoundException(UUID eventId) {
        super("Event not found: " + eventId);
    }
}

package com.eventgateway.api.exception;

/**
 * Raised when a requested ledger event does not exist.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String eventId) {
        super("Event not found: " + eventId);
    }
}

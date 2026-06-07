package com.cs.eventgateway.exception;

import java.util.UUID;

/**
 * Raised when a requested ledger event does not exist.
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(UUID eventId) {
        super("Event not found: " + eventId);
    }
}
